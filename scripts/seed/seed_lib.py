#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""在已部署的 OCKC 站点上构造多组「知识帖作业」完整流程数据。
流程：布置作业 → 学生写作(含插图) → 提交 → 证据核验 → 评分 → 批注 → 学生回应
     → 修订二稿 → 复核评分 → 申请发表 → 教师审核发表
"""
import json, pathlib, sys, time, uuid, requests

BASE = "http://47.105.100.205:8080"
FIGS = pathlib.Path("/tmp/ockc_seed/figs")
PW = "CRDaOyEIrbyTobV3"
LOG = []

def log(*a):
    msg = " ".join(str(x) for x in a)
    LOG.append(msg)
    print(msg, flush=True)

class Client:
    def __init__(self, email):
        self.s = requests.Session()
        self.email = email
    def csrf(self):
        r = self.s.get(f"{BASE}/api/auth/csrf", timeout=20)
        r.raise_for_status()
        d = r.json()
        return d["token"], d["headerName"]
    def login(self):
        t, h = self.csrf()
        r = self.s.post(f"{BASE}/api/auth/login", data={"username": self.email, "password": PW},
                        headers={h: t}, timeout=20)
        if r.status_code != 200:
            raise RuntimeError(f"login failed {self.email}: {r.status_code} {r.text[:200]}")
        return self
    def req(self, method, path, body=None, files=None, extra_headers=None):
        t, h = self.csrf()
        headers = {h: t}
        if extra_headers:
            headers.update(extra_headers)
        kw = dict(headers=headers, timeout=90)
        if files:
            kw["files"] = files
        elif body is not None:
            headers["Content-Type"] = "application/json"
            kw["data"] = json.dumps(body, ensure_ascii=False).encode("utf-8")
        r = self.s.request(method, f"{BASE}{path}", **kw)
        if r.status_code >= 400:
            raise RuntimeError(f"{method} {path} -> {r.status_code} {r.text[:400]}")
        try:
            return r.json()
        except Exception:
            return r.text
    def get(self, p): return self.req("GET", p)
    def post(self, p, b=None, **kw): return self.req("POST", p, b, **kw)
    def put(self, p, b=None): return self.req("PUT", p, b)

# ---------- 文档构造 ----------
def h2(bid, text):
    return {"type": "heading", "attrs": {"level": 2, "blockId": bid},
            "content": [{"type": "text", "text": text}]}

def para(bid, text, bold_prefix=None):
    content = []
    if bold_prefix:
        content.append({"type": "text", "text": bold_prefix, "marks": [{"type": "bold"}]})
    content.append({"type": "text", "text": text})
    return {"type": "paragraph", "attrs": {"blockId": bid}, "content": content}

def figure(bid, src, alt, caption, source):
    return {"type": "figure", "attrs": {"blockId": bid, "src": src, "alt": alt,
                                        "caption": caption, "source": source}}

def quote(bid, text):
    return {"type": "blockquote", "content": [para(bid, text)]}

def callout(bid, text):
    return {"type": "callout", "content": [para(bid, text)]}

def mathblock(bid, latex):
    return {"type": "mathBlock", "attrs": {"blockId": bid, "latex": latex}}

def bullets(bid, items):
    lis = []
    for i, t in enumerate(items):
        lis.append({"type": "listItem", "content": [para(f"{bid}-{i}", t)]})
    return {"type": "bulletList", "content": lis}

def doc(*blocks):
    return {"type": "doc", "content": list(blocks)}

def ev(pid, title, url, excerpt, claim):
    return {"paragraphId": pid, "sourceTitle": title, "sourceUrl": url,
            "excerpt": excerpt, "claim": claim}

CODES = ["S1","S2","S3","A1","A2","A3","C1","C2","L1","L2","L3"]
def items(scores, na=None):
    na = na or {}
    out = []
    for c in CODES:
        if c in na:
            out.append({"code": c, "score": None, "status": "NA", "reason": na[c]})
        else:
            out.append({"code": c, "score": scores[c], "status": "SCORED", "reason": ""})
    return out

# ---------- 上传插图 ----------
def upload(c, eid, fig_name):
    p = FIGS / f"{fig_name}.png"
    with p.open("rb") as f:
        r = c.req("POST", f"/api/entries/{eid}/media",
                  files={"file": (p.name, f, "image/png")})
    return r["src"]

def build_doc(c, eid, blocks):
    """把 ('FIG', id, name, alt, caption, source) 占位替换为真实 src"""
    out = []
    for b in blocks:
        if isinstance(b, tuple) and b[0] == "FIG":
            _, bid, name, alt, cap, src_note = b
            src = upload(c, eid, name)
            out.append(figure(bid, src, alt, cap, src_note))
        else:
            out.append(b)
    return doc(*out)

# ---------- 长文排版扩展：多级标题 / 行内公式 / 表格 / 混排段落 ----------
import uuid as _uuid

def h(level, bid, text):
    return {"type": "heading", "attrs": {"level": level, "blockId": bid},
            "content": [{"type": "text", "text": text}]}

def h3(bid, text): return h(3, bid, text)

def im(latex):
    """行内公式节点（编辑器里也可直接输入 $...$ 生成）。"""
    return {"type": "inlineMath", "attrs": {"latex": latex}}

def t(s, bold=False, italic=False):
    n = {"type": "text", "text": s}
    marks = []
    if bold: marks.append({"type": "bold"})
    if italic: marks.append({"type": "italic"})
    if marks: n["marks"] = marks
    return n

def prich(bid, parts):
    """混排段落：parts 里可以夹 im('...') 行内公式。"""
    return {"type": "paragraph", "attrs": {"blockId": bid}, "content": parts}

def table(bid, header, rows):
    def cell(text, head):
        return {"type": "tableHeader" if head else "tableCell",
                "attrs": {"colspan": 1, "rowspan": 1},
                "content": [{"type": "paragraph",
                             "attrs": {"blockId": f"{bid}-{_uuid.uuid4().hex[:8]}"},
                             "content": [{"type": "text", "text": text}]}]}
    body = [{"type": "tableRow", "content": [cell(x, True) for x in header]}]
    for r in rows:
        body.append({"type": "tableRow", "content": [cell(x, False) for x in r]})
    return {"type": "table", "attrs": {"blockId": bid}, "content": body}
