#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""诊断：图块 options.annotations 是否拿到了批注数组。"""
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
    c.click_text("button", "批改中心"); time.sleep(2.5)
    for _ in range(5):
        if c.eval("""[...document.querySelectorAll('button')].some(b=>b.textContent.includes('当光照变弱'))"""):
            break
        if c.click_text("button", "下一页") != "ok":
            break
        time.sleep(1.6)
    c.click_text("button", "当光照变弱"); time.sleep(2.0)
    c.wait_for("document.querySelector('.ProseMirror')?.children.length > 5", 25); time.sleep(1.5)

    print("annotate 按钮 html:", c.eval("document.querySelector('.figure-annotate')?.outerHTML"))
    print("figure data-block-id:", c.eval("document.querySelector('.knowledge-figure')?.getAttribute('data-block-id')"))
    print("图注:", c.eval("document.querySelector('.knowledge-figure figcaption strong')?.textContent?.slice(0,20)"))
finally:
    c.close()
