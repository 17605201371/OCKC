#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""学生视角：找到开启 AI 的作业，截图 AI 建议卡片。"""
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
      const body = new URLSearchParams({{username:'student@example.test', password:{json.dumps(PW)}}});
      await fetch('/api/auth/login', {{method:'POST', headers:{{'Content-Type':'application/x-www-form-urlencoded', [t.headerName]:t.token}}, body}});
    }})()""", await_promise=True)
    c.goto(BASE, 3.0)
    c.wait_for("document.querySelectorAll('button').length>5", 20)
    c.click_text("button", "我的作业")
    time.sleep(2.0)
    found = False
    for i in range(5):
        c.eval(f"""(function(){{
          const bs=[...document.querySelectorAll('button')].filter(b=>b.textContent.trim()==='继续编辑');
          if(bs[{i}]) bs[{i}].click();
        }})()""")
        time.sleep(2.5)
        if c.eval("!!document.querySelector('#sidebar-ai')"):
            found = True
            break
        c.click_text("button", "返回"); time.sleep(1.6)
        c.click_text("button", "我的作业"); time.sleep(1.6)
    print("找到 AI 区块:", found)
    if found:
        print("AI 文本:", (c.eval("document.querySelector('#sidebar-ai').textContent") or "").replace("\n", " ")[:260])
        print("建议条数:", c.eval("document.querySelectorAll('.ai-suggestion').length"))
        c.eval("document.querySelector('#sidebar-ai').scrollIntoView({block:'center'})")
        time.sleep(0.6)
        c.shot("/tmp/ui_ai.png")
    print("console:", c.console[-6:])
finally:
    c.close()
