#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""回归：各页面渲染 + 学生视角社区 + 删除冗长文案后无异常。"""
import sys, time
sys.path.insert(0, "/Users/zss/WorkBuddy AI/2026-10-23-13-56-59/ockc-seed")
from cdp import Chrome

BASE = "http://47.105.100.205:8080"
OUT = "/Users/zss/WorkBuddy AI/2026-10-23-13-56-59/ockc-seed"

def login(c, email, pw="CRDaOyEIrbyTobV3"):
    c.goto(BASE, 2.0)
    c.wait_for("!!document.querySelector('input[type=email]')", 15)
    c.eval(f"""(function(){{
      const set=(el,v)=>{{const s=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;s.call(el,v);el.dispatchEvent(new Event('input',{{bubbles:true}}));}};
      set(document.querySelector('input[type=email]'),'{email}');
      set(document.querySelector('input[type=password]'),'{pw}');
      document.querySelector('form').requestSubmit();}})()""")
    time.sleep(3.0)

def check(c, label, sel=".page"):
    time.sleep(1.6)
    txt = c.eval(f"document.querySelector('{sel}')?.innerText.slice(0,120).replace(/\\n+/g,' / ')")
    print(f"[{label}] {txt}")
    return txt

c = Chrome(width=1440, height=1080)
try:
    login(c, "teacher@example.test")
    for name in ["首页", "课程作业", "批改中心", "证据核验", "教学进展"]:
        print("click", name, c.click_text("button", name))
        check(c, name)
    # 首页文案检查
    c.click_text("button", "首页"); time.sleep(1.5)
    print("workspace-note:", c.eval("!!document.querySelector('.workspace-note')"))
    c.shot(f"{OUT}/shot_home_v2.png")

    # 证据页文案
    c.click_text("button", "证据核验"); time.sleep(1.6)
    print("evidence-help:", c.eval("!!document.querySelector('.evidence-help')"))

    # 学生视角
    c.eval("""(function(){fetch('/api/auth/logout',{method:'POST'}).catch(()=>{});return 1})()""")
    time.sleep(1)
    login(c, "student@example.test")
    print("student login body:", c.eval("document.body.innerText.slice(0,60).replace(/\\n/g,' / ')"))
    print("click community:", c.click_text("button", "知识社区"))
    c.wait_for("!!document.querySelector('.comm-row')", 20)
    time.sleep(1.2)
    print("student tabs:", c.eval("[...document.querySelectorAll('.comm-tab')].map(e=>e.textContent.replace(/\\s+/g,''))"))
    print("student chips:", c.eval("[...document.querySelectorAll('.comm-chip')].map(e=>e.textContent.trim())"))
    print("student rows:", c.eval("document.querySelectorAll('.comm-row').length"))
    print("student todo:", c.eval("[...document.querySelectorAll('.comm-card')[0].querySelectorAll('.comm-li')].map(e=>e.innerText.replace(/\\n/g,' '))"))
    print("mine tag:", c.eval("document.querySelectorAll('.comm-row .comm-tag.blue').length"))
    c.shot(f"{OUT}/shot_comm_student.png")
    print("CONSOLE:", [x for x in c.console if x.startswith(("EXC", "error"))][:8])
finally:
    c.close()
