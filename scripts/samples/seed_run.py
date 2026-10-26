"""创建四份图文样本：两篇知识帖 + 两道步骤题，并完成评分与批注。

用法：
    python3 make_figs.py          # 先在 ASSETS 目录生成四张示意图
    python3 seed_run.py           # 全部创建；也可传 1/2/3/4 只跑其中一份

环境变量：OCKC_BASE、OCKC_TEACHER、OCKC_STUDENT、OCKC_PASSWORD、OCKC_ASSETS。
"""
import os, sys, json, uuid
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import requests
from seed_content import post_binary, post_drag, step_climb, step_mono

BASE = os.environ.get("OCKC_BASE", "http://47.105.100.205:8080")
PASSWORD = os.environ.get("OCKC_PASSWORD", "CRDaOyEIrbyTobV3")
TEACHER = (os.environ.get("OCKC_TEACHER", "teacher@example.test"), PASSWORD)
STUDENT = (os.environ.get("OCKC_STUDENT", "student@example.test"), PASSWORD)
ASSETS = os.environ.get("OCKC_ASSETS", "/tmp/ockc_assets")


def session(email, pw):
    s = requests.Session()
    d = s.get(BASE + "/api/auth/csrf").json()
    s.headers.update({d["headerName"]: d["token"]})
    r = s.post(BASE + "/api/auth/login",
               data={"username": email, "password": pw},
               headers={d["headerName"]: d["token"]})
    r.raise_for_status()
    return s


def csrf(s):
    d = s.get(BASE + "/api/auth/csrf").json()
    s.headers[d["headerName"]] = d["token"]


def call(s, method, path, body=None, key=None, expect=200):
    csrf(s)
    headers = {}
    if key:
        headers["Idempotency-Key"] = key
    r = s.request(method, BASE + "/api" + path, json=body, headers=headers)
    if r.status_code != expect:
        raise SystemExit(f"{method} {path} -> {r.status_code}: {r.text[:400]}")
    return r.json() if r.content else None


def upload(s, eid, filename):
    csrf(s)
    with open(f"{ASSETS}/{filename}", "rb") as fh:
        r = s.post(f"{BASE}/api/entries/{eid}/media",
                   files={"file": (filename, fh, "image/png")})
    if r.status_code != 200:
        raise SystemExit(f"upload {filename} -> {r.status_code}: {r.text[:300]}")
    data = r.json()
    print("    upload ->", data)
    return data["src"]


def render_text(n):
    """与后端 CommunityService.text() 保持一致的取文方式：公式取 latex，图取 alt+caption。"""
    out = n.get("text", "")
    t = n.get("type")
    if t in ("mathBlock", "inlineMath"):
        out += n.get("attrs", {}).get("latex", "")
    if t == "figure":
        a = n.get("attrs", {})
        out += "[图片] " + a.get("alt", "") + " " + a.get("caption", "") + " " + a.get("source", "")
    for c in n.get("content", []):
        out += render_text(c)
    return out


def find_text(node, block_id):
    if node.get("attrs", {}).get("blockId") == block_id:
        return render_text(node)
    for c in node.get("content", []):
        got = find_text(c, block_id)
        if got:
            return got
    return ""


def quote_of(doc, block_id, n=16):
    t = find_text(doc, block_id)
    return t[:n]


def make_assignment(teacher, title, requirements, kind):
    body = {
        "title": title,
        "requirements": requirements,
        "dueAt": "2026-11-30T12:00:00Z",
        "aiFeedbackEnabled": True,
        "rubricId": None,
        "kind": kind,
        "evidencePolicy": "DISPUTED",
    }
    return call(teacher, "POST", "/courses/1/assignments", body)


def publish_sample(teacher, student, spec):
    a = make_assignment(teacher, spec["title"], spec["requirements"], spec["kind"])
    entry = call(student, "POST", f"/assignments/{a['id']}/entries", {})
    eid = entry["id"]
    images = {}
    for name in spec.get("images", []):
        images[name] = upload(student, eid, name)
    doc, evidence = spec["build"](next(iter(images.values())) if images else "")
    saved = call(student, "PUT", f"/entries/{eid}/draft", {
        "title": spec["entry_title"],
        "content": doc,
        "revision": entry["revision"],
        "evidence": evidence,
    })
    sub = call(student, "POST", f"/entries/{eid}/submissions",
               {"revision": saved["revision"]},
               key="seed-" + uuid.uuid4().hex[:16])
    print(f"  entry {eid} v{sub['version_no']} submission {sub['id']} "
          f"({len(json.dumps(doc, ensure_ascii=False))} chars)")
    return a, eid, sub, doc


def grade_post(teacher, sid, scores, summary):
    codes = ["S1", "S2", "S3", "A1", "A2", "A3", "C1", "C2", "L1", "L2", "L3"]
    items = [{"code": c, "score": scores.get(c, 86), "status": "SCORED", "reason": None}
             for c in codes]
    call(teacher, "POST", f"/submissions/{sid}/evaluations", {
        "items": items, "gateReason": "", "summary": summary,
        "expectedRevision": 0, "stepScores": None,
    })


def grade_steps(teacher, sid, steps, summary):
    call(teacher, "POST", f"/submissions/{sid}/evaluations", {
        "items": [], "gateReason": "", "summary": summary,
        "expectedRevision": 0, "stepScores": steps,
    })


def annotate(teacher, sid, doc, block_id, indicator, body, n=16):
    call(teacher, "POST", f"/submissions/{sid}/feedback", {
        "paragraphId": block_id,
        "indicator": indicator,
        "body": body,
        "quote": quote_of(doc, block_id, n),
    })


def sample_binary(teacher, student):
    print("1. 二分查找")
    a, eid, sub, doc = publish_sample(teacher, student, {
        "title": "算法笔记：把二分查找的边界一次讲清",
        "requirements": ("写一篇 1200 字以上的算法笔记，讲清一种你实际用过的查找或排序算法。"
                         "要求：至少一张示意图、一段可运行代码、一个公式和一个对比表格；"
                         "所有复杂度结论要说明推导过程。"),
        "kind": "POST",
        "entry_title": "二分查找的边界：三种写法，一个判据",
        "images": ["binary_search.png"],
        "build": post_binary,
    })
    grade_post(teacher, sub["id"], {"S1": 92, "S2": 94, "S3": 90, "A1": 93, "A2": 91,
                                    "A3": 90, "C1": 92, "C2": 88, "L1": 93, "L2": 91, "L3": 90},
               "边界不变式讲得清楚，三种写法的对比表很实用；复杂度推导完整。"
               "代码注释可以再补一句返回值语义，方便直接复用。")
    paras = [n for n in doc["content"] if n["type"] == "paragraph" and n.get("content")]
    annotate(teacher, sub["id"], doc, paras[1]["attrs"]["blockId"], "C",
             "这里只说「维持不变式」，但没写清楚不变式的具体形式。建议补一句：若答案存在，则 lo ≤ 答案下标 ≤ hi。", 18)
    annotate(teacher, sub["id"], doc, paras[-2]["attrs"]["blockId"], "A",
             "「五个数量级」这个说法需要给出具体计算：10⁶ 的 log2 约 20，线性是 5×10⁵，差 4 个数量级。", 12)


def sample_drag(teacher, student):
    print("2. 自由落体")
    a, eid, sub, doc = publish_sample(teacher, student, {
        "title": "物理探究：把「自由落体与质量无关」的适用条件写清楚",
        "requirements": ("选择一个你在课本上见过的结论，写一篇 1200 字以上的探究笔记，"
                         "说明它的适用条件和失效情形。要求：有公式推导、有数值或实验数据表格、"
                         "至少一张图，并用代码复现关键计算。"),
        "kind": "POST",
        "entry_title": "自由落体真的与质量无关吗？一次带空气阻力的数值实验",
        "images": ["drag_vt.png"],
        "build": post_drag,
    })
    grade_post(teacher, sub["id"], {"S1": 93, "S2": 92, "S3": 91, "A1": 94, "A2": 90,
                                    "A3": 89, "C1": 90, "C2": 87, "L1": 92, "L2": 90, "L3": 88},
               "从适用条件切入，比直接复述结论有价值。解析解与 RK4 互相验证的做法很扎实。"
               "表格里的落地时间建议注明积分步长，方便复现。")
    paras = [n for n in doc["content"] if n["type"] == "paragraph" and n.get("content")]
    annotate(teacher, sub["id"], doc, paras[2]["attrs"]["blockId"], "S",
             "把 m/k 说成唯一决定量不够准确：v_t 还依赖 g，形状通过 k 进入。建议改成「在 g 固定时由 m/k 决定」。", 14)
    annotate(teacher, sub["id"], doc, paras[4]["attrs"]["blockId"], "A",
             "表格的「2 s 时速度」没有说明是解析解还是数值解，两者在小数位上会有差别，请标注来源。", 10)


def sample_climb(teacher, student):
    print("3. 爬楼梯")
    a, eid, sub, doc = publish_sample(teacher, student, {
        "title": "步骤题：用动态规划解爬楼梯，从递归优化到 O(1) 空间",
        "requirements": ("把解题过程拆成 4 步提交：定义状态与转移、写出递归、加记忆化、压到滚动数组。"
                         "每一步都要有代码或公式支撑，并说明这一步解决了上一步的什么问题。"),
        "kind": "STEP",
        "entry_title": "用动态规划解爬楼梯：从递归到滚动数组",
        "images": ["dp_tree.png"],
        "build": step_climb,
    })
    grade_steps(teacher, sub["id"], [
        {"index": 0, "title": "第一步：定义状态与转移方程", "score": 10, "max": 10,
         "tags": [], "comment": ""},
        {"index": 1, "title": "第二步：写出递归，先让它跑对", "score": 9, "max": 10,
         "tags": ["表述不清"], "comment": ""},
        {"index": 2, "title": "第三步：记忆化，去掉重复计算", "score": 9, "max": 10,
         "tags": [], "comment": ""},
        {"index": 3, "title": "第四步：滚动数组，把空间压到 O(1)", "score": 10, "max": 10,
         "tags": [], "comment": ""},
    ], "四步递进清楚，每步都有可运行的代码。第二步的复杂度分析可以补一句：递归树每层翻倍，"
       "所以是 O(2^n) 而不是 O(n log n)。")
    paras = [n for n in doc["content"] if n["type"] == "paragraph" and n.get("content")]
    annotate(teacher, sub["id"], doc, paras[1]["attrs"]["blockId"], "C",
             "「最后一步只能跨 1 级或 2 级」是转移方程的依据，但没说明为什么这两种情况互不重叠，补一句分类讨论的完备性。", 14)


def sample_mono(teacher, student):
    print("4. 导数与单调性")
    a, eid, sub, doc = publish_sample(teacher, student, {
        "title": "步骤题：用导数判断单调区间，四步写完不丢分",
        "requirements": ("按四步提交：求导并因式分解、找驻点与不可导点、列表判断符号、写出单调区间。"
                         "第三步必须用表格呈现，第四步要说明端点为什么取开区间。"),
        "kind": "STEP",
        "entry_title": "用导数判断单调区间：四步走",
        "images": ["monotonic.png"],
        "build": step_mono,
    })
    grade_steps(teacher, sub["id"], [
        {"index": 0, "title": "第一步：求导并因式分解", "score": 10, "max": 10,
         "tags": [], "comment": ""},
        {"index": 1, "title": "第二步：找驻点与不可导点", "score": 9, "max": 10,
         "tags": [], "comment": ""},
        {"index": 2, "title": "第三步：列表判断导数符号", "score": 10, "max": 10,
         "tags": [], "comment": ""},
        {"index": 3, "title": "第四步：写出单调区间", "score": 10, "max": 10,
         "tags": [], "comment": ""},
    ], "符号表规范，端点取开区间的理由说清楚了。第二步可以补一句：定义域是全体实数，"
       "所以不存在不可导点，这句话本身就是得分点。")
    paras = [n for n in doc["content"] if n["type"] == "paragraph" and n.get("content")]
    annotate(teacher, sub["id"], doc, paras[-1]["attrs"]["blockId"], "S",
             "「严格单调」在开区间上成立，但如果教材采用「在区间上单调」的宽松定义，端点写闭区间也能接受。建议注明采用的教材口径。", 12)


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else ""
    teacher = session(*TEACHER)
    student = session(*STUDENT)
    me = call(teacher, "GET", "/auth/me")
    print("teacher", me["id"], me["display_name"])
    tasks = [("1", sample_binary), ("2", sample_drag), ("3", sample_climb), ("4", sample_mono)]
    for key, fn in tasks:
        if only and key != only:
            continue
        fn(teacher, student)
    print("done")


if __name__ == "__main__":
    main()
