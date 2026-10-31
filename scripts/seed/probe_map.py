#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验每个高亮的 annotation-id 与右侧卡片 id 一一对应。"""
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
    c.click_text("button", "我的作业"); time.sleep(2.0)
    for i in range(5):
        c.eval(f"""(function(){{
          const bs=[...document.querySelectorAll('button')].filter(b=>b.textContent.trim()==='继续编辑');
          if(bs[{i}]) bs[{i}].click();
        }})()""")
        time.sleep(2.4)
        if c.eval("document.querySelectorAll('.annotation-mark').length"):
            break
        c.click_text("button", "返回"); time.sleep(1.5)
        c.click_text("button", "我的作业"); time.sleep(1.5)

    marks = c.eval("""[...document.querySelectorAll('.annotation-mark')].map(m=>({id:m.dataset.annotationId, text:m.textContent.slice(0,12), cls:m.className}))""")
    print("高亮:", json.dumps(marks, ensure_ascii=False))
    print("右侧卡片:", c.eval("""[...document.querySelectorAll('.feedback-card')].map(c=>({id:c.id, dim:(c.querySelector('.pill, .dim-pill, span')||{}).textContent, quote:(c.querySelector('blockquote')||{}).textContent}))"""))
    for i, m in enumerate(marks):
        c.eval(f"document.querySelectorAll('.annotation-mark')[{i}].dispatchEvent(new MouseEvent('click',{{bubbles:true}}))")
        time.sleep(0.8)
        print(f"  点第{i}个({m['text']}) → 卡片:", c.eval("document.querySelector('.feedback-card.is-target')?.id"))
finally:
    c.close()
