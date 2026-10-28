#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""逐字模拟键盘输入，验证 $...$ / $$...$$ 的即时转换。"""
import json, sys, time
sys.path.insert(0, ".")
from cdp import Chrome

BASE = "http://47.105.100.205:8080"
PW = "CRDaOyEIrbyTobV3"


def type_text(c, text, delay=0.06):
    for ch in text:
        c.send("Input.dispatchKeyEvent", {"type": "char", "text": ch,
                                          "unmodifiedText": ch, "key": ch})
        time.sleep(delay)


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

    # 新建一个空段落，避免改动原有内容
    c.eval("""(function(){
      const ps=[...document.querySelectorAll('.ProseMirror p')];
      const p=ps[ps.length-1];
      p.scrollIntoView({block:'center'});
      const r=document.createRange(); r.selectNodeContents(p); r.collapse(false);
      const s=window.getSelection(); s.removeAllRanges(); s.addRange(r);
      document.querySelector('.ProseMirror').focus();
      return 'ok';
    })()""")
    time.sleep(0.3)
    before = c.eval("document.querySelectorAll('.math-inline').length")
    type_text(c, "动能公式 $E_k=\\frac{1}{2}mv^2$ 说明")
    time.sleep(0.8)
    # 回车后输入块公式
    c.send("Input.dispatchKeyEvent", {"type":"keyDown","key":"Enter","code":"Enter","windowsVirtualKeyCode":13,"nativeVirtualKeyCode":13})
    c.send("Input.dispatchKeyEvent", {"type":"keyUp","key":"Enter","code":"Enter","windowsVirtualKeyCode":13,"nativeVirtualKeyCode":13})
    time.sleep(0.6)
    b1 = c.eval("document.querySelectorAll('.knowledge-math').length")
    type_text(c, "$$a^2+b^2=c^2$$")
    time.sleep(1.0)
    b2 = c.eval("document.querySelectorAll('.knowledge-math').length")
    print("块公式：", b1, "→", b2)
    time.sleep(1.0)
    after = c.eval("document.querySelectorAll('.math-inline').length")
    print("行内公式：", before, "→", after)
    print("latex:", c.eval("""(function(){
      const m=document.querySelectorAll('.math-inline');
      return m.length? m[m.length-1].getAttribute('title'): null;
    })()"""))
    print("渲染字符:", c.eval("""(function(){
      const m=document.querySelectorAll('.math-inline');
      return m.length? m[m.length-1].textContent.trim(): null;
    })()"""))
    c.shot("/tmp/v3_inline.png")
    print("console:", [x for x in c.console if "error" in x.lower() or "EXC" in x][:4])
finally:
    c.close()
