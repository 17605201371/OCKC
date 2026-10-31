#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""最终验收：长文 + 图片批注徽标 + 行内公式 + 社区列表。"""
import json, sys, time
sys.path.insert(0, ".")
from cdp import Chrome

BASE = "http://47.105.100.205:8080"
PW = "CRDaOyEIrbyTobV3"
R = {}

def login(c, user):
    c.goto(BASE, 2.5)
    c.eval(f"""(async()=>{{
      const t = await (await fetch('/api/auth/csrf')).json();
      const body = new URLSearchParams({{username:{json.dumps(user)}, password:{json.dumps(PW)}}});
      await fetch('/api/auth/login', {{method:'POST', headers:{{'Content-Type':'application/x-www-form-urlencoded', [t.headerName]:t.token}}, body}});
    }})()""", await_promise=True)
    c.goto(BASE, 3.0)
    c.wait_for("document.querySelectorAll('button').length>5", 20)

c = Chrome()
try:
    login(c, "teacher@example.test")
    c.click_text("button", "批改中心"); time.sleep(2.5)
    for _ in range(5):
        if c.eval("""[...document.querySelectorAll('button')].some(b=>b.textContent.includes('当光照变弱'))"""):
            break
        if c.click_text("button", "下一页") != "ok":
            break
        time.sleep(1.6)
    c.click_text("button", "当光照变弱"); time.sleep(2.0)
    c.wait_for("document.querySelector('.ProseMirror')?.children.length > 5", 25); time.sleep(1.5)
    R["大纲"] = c.eval("[...document.querySelectorAll('.document-outline nav button')].map(b=>b.querySelector('span').textContent+' '+b.querySelectorAll('span')[1].textContent.slice(0,10))")
    R["行内公式"] = c.eval("document.querySelectorAll('.math-inline').length")
    R["块公式"] = c.eval("document.querySelectorAll('.knowledge-math').length")
    R["表格"] = c.eval("document.querySelectorAll('.ProseMirror table').length")
    R["图块"] = c.eval("document.querySelectorAll('.knowledge-figure').length")
    R["图批注徽标"] = c.eval("""[...document.querySelectorAll('.figure-annotate')].map(b=>b.textContent.trim())""")
    R["右侧图片批注卡片"] = c.eval("""[...document.querySelectorAll('.feedback-card')].map(c=>{
      const q=c.querySelector('blockquote'); return q? q.textContent.slice(0,18): '';
    })""")
    c.eval("document.querySelector('.knowledge-figure')?.scrollIntoView({block:'center'})")
    time.sleep(0.8)
    c.shot("/tmp/v4_long.png")
    print(json.dumps(R, ensure_ascii=False, indent=1))
    # 点图片批注 → 定位到右侧卡片
    c.eval("document.querySelector('.figure-annotate')?.click()")
    time.sleep(0.5)
    c.eval("document.querySelector('.annotation-close')?.click()")
    print("console:", [x for x in c.console if "error" in x.lower() or "EXC" in x][:3])
finally:
    c.close()
