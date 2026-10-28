#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""社区列表与长文详情页截图。"""
import json, sys, time
sys.path.insert(0, ".")
from cdp import Chrome

BASE = "http://47.105.100.205:8080"
PW = "CRDaOyEIrbyTobV3"

c = Chrome()
try:
    c.goto(BASE, 2.5)
    c.eval(f"""(async()=>{{
      const t = await (await fetch('/api/auth/csrf')).json();
      const body = new URLSearchParams({{username:'teacher@example.test', password:{json.dumps(PW)}}});
      await fetch('/api/auth/login', {{method:'POST', headers:{{'Content-Type':'application/x-www-form-urlencoded', [t.headerName]:t.token}}, body}});
    }})()""", await_promise=True)
    c.goto(BASE, 3.0)
    c.wait_for("document.querySelectorAll('button').length>5", 20)
    c.click_text("button", "知识社区"); time.sleep(2.5)
    titles = c.eval("""[...document.querySelectorAll('h3, .post-title, article strong')].map(e=>e.textContent.trim()).filter(Boolean).slice(0,14)""")
    print("社区条目:", json.dumps(titles, ensure_ascii=False))
    c.shot("/tmp/v4_community.png")
    print("打开长文:", c.click_text("button, article, h3", "一克葡萄糖的去向"))
    time.sleep(3.0)
    print("页面:", c.eval("document.querySelector('.nav-name, header h1')?.textContent?.trim()?.slice(0,30)"))
    print("大纲:", c.eval("document.querySelectorAll('.document-outline nav button').length"))
    print("行内公式:", c.eval("document.querySelectorAll('.math-inline').length"))
    print("表格:", c.eval("document.querySelectorAll('.ProseMirror table').length"))
    print("图块:", c.eval("document.querySelectorAll('.knowledge-figure').length"))
    c.shot("/tmp/v4_reader.png")
finally:
    c.close()
