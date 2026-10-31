#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""学生视角：滚到右侧栏底部，检查 AI 反馈区块。"""
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
    for i in range(4):
        c.eval(f"""(function(){{
          const bs=[...document.querySelectorAll('button')].filter(b=>b.textContent.trim()==='继续编辑');
          if(bs[{i}]) bs[{i}].click();
        }})()""")
        time.sleep(2.2)
        if c.eval("document.querySelectorAll('.annotation-mark').length"):
            break
        c.click_text("button", "返回"); time.sleep(1.6)
        c.click_text("button", "我的作业"); time.sleep(1.6)

    print("AI 区块存在:", c.eval("!!document.querySelector('#sidebar-ai')"))
    print("AI 文本:", (c.eval("document.querySelector('#sidebar-ai')?.textContent") or "")[:200])
    # 收起面板
    print("点收起:", c.eval("""(function(){
      const b=document.querySelector('.panel-collapse');
      if(!b) return 'no-btn';
      b.click(); return 'ok';
    })()"""))
    time.sleep(0.8)
    print("面板可见:", c.eval("(()=>{const a=document.querySelector('.feedback-sidebar'); return a? getComputedStyle(a).display!=='none':false})()"))
    print("重开按钮:", c.eval("!!document.querySelector('.panel-reopen')"))
    c.shot("/tmp/ui_collapsed.png")
    print("点重开:", c.eval("""(function(){const b=document.querySelector('.panel-reopen'); if(!b) return 'no'; b.click(); return 'ok';})()"""))
    time.sleep(0.6)
    print("面板恢复:", c.eval("(()=>{const a=document.querySelector('.feedback-sidebar'); return a? getComputedStyle(a).display!=='none':false})()"))
    # 滚到 AI 区
    c.eval("""(function(){const s=document.querySelector('#sidebar-ai'); if(s) s.scrollIntoView({block:'center'}); return !!s;})()""")
    time.sleep(0.6)
    c.shot("/tmp/ui_ai.png")
    print("console:", c.console[-6:])
finally:
    c.close()
