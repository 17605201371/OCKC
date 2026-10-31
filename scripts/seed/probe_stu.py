#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""学生视角：点击正文高亮 → 右侧定位。"""
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

    print("高亮:", c.eval("[...document.querySelectorAll('.annotation-mark')].map(m=>m.textContent)"))
    c.shot("/tmp/ui_stu_marks.png")
    print("点第一个高亮:", c.eval("""(function(){
      const m=document.querySelectorAll('.annotation-mark')[0];
      m.dispatchEvent(new MouseEvent('click',{bubbles:true})); return 'ok';
    })()"""))
    time.sleep(1.4)
    print("右侧高亮卡片:", c.eval("document.querySelector('.feedback-card.is-target')?.id"))
    print("右侧批注卡片数:", c.eval("document.querySelectorAll('.feedback-card').length"))
    c.shot("/tmp/ui_stu_jump.png")
    # 反向：点右侧卡片的「定位正文」
    print("点定位正文:", c.click_text(".feedback-card button", "定位正文"))
    time.sleep(1.2)
    print("正文锚点:", c.eval("document.querySelector('.anchor-focus')?.dataset.blockId"))
    c.shot("/tmp/ui_stu_locate.png")
    print("console:", c.console[-6:])
finally:
    c.close()
