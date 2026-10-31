#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证：正文里直接输入 $...$ 会转成行内公式；$$...$$ 转成公式块。"""
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
    c.eval("""[...document.querySelectorAll('button')].filter(b=>b.textContent.trim()==='继续编辑')[0].click()""")
    c.wait_for("document.querySelector('.ProseMirror') !== null", 25)
    time.sleep(2.0)

    # 把光标放到最后一个段落的末尾
    print("定位:", c.eval("""(function(){
      const ps=[...document.querySelectorAll('.ProseMirror p')];
      const p=ps[ps.length-1];
      if(!p) return 'no-p';
      p.scrollIntoView({block:'center'});
      const r=document.createRange();
      r.selectNodeContents(p); r.collapse(false);
      const s=window.getSelection(); s.removeAllRanges(); s.addRange(r);
      document.querySelector('.ProseMirror').focus();
      return 'ok';
    })()"""))
    time.sleep(0.4)
    before = c.eval("document.querySelectorAll('.math-inline').length")
    # 用真实输入事件敲入 $E=mc^2$
    c.send("Input.insertText", {"text": " $E=mc^2$ "})
    time.sleep(1.2)
    after = c.eval("document.querySelectorAll('.math-inline').length")
    print("行内公式：", before, "→", after)
    print("最后一个公式 latex:", c.eval("document.querySelectorAll('.math-inline')[document.querySelectorAll('.math-inline').length-1]?.getAttribute('title')"))

    # 再试块公式
    c.send("Input.insertText", {"text": "\n"})
    time.sleep(0.3)
    beforeb = c.eval("document.querySelectorAll('.knowledge-math').length")
    c.send("Input.insertText", {"text": "$$a^2+b^2=c^2$$"})
    time.sleep(1.2)
    afterb = c.eval("document.querySelectorAll('.knowledge-math').length")
    print("公式块：", beforeb, "→", afterb)
    c.shot("/tmp/v2_inline_math.png")
    print("console:", [x for x in c.console if "error" in x.lower() or "EXC" in x][:4])
finally:
    c.close()
