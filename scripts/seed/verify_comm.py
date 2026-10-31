#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证：知识社区改版 + 量规简化。"""
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
      set(document.querySelector('input[type=email]'),{chr(39)}{email}{chr(39)});
      set(document.querySelector('input[type=password]'),{chr(39)}{pw}{chr(39)});
      document.querySelector('form').requestSubmit();}})()""")
    time.sleep(3.0)

c = Chrome(width=1440, height=1080)
try:
    login(c, "teacher@example.test")

    # ---------- 知识社区 ----------
    print("click community:", c.click_text("button", "知识社区"))
    c.wait_for("!!document.querySelector('.comm-row')", 20)
    time.sleep(1.0)
    print("rows:", c.eval("document.querySelectorAll('.comm-row').length"))
    print("tabs:", c.eval("[...document.querySelectorAll('.comm-tab')].map(e=>e.textContent.replace(/\\s+/g,''))"))
    print("chips:", c.eval("[...document.querySelectorAll('.comm-chip')].map(e=>e.textContent.trim())"))
    print("rail:", c.eval("[...document.querySelectorAll('.comm-card-head h3')].map(e=>e.textContent.trim())"))
    print("foot:", c.eval("document.querySelector('.comm-foot')?.textContent.trim()"))
    print("bars:", c.eval("document.querySelectorAll('.comm-bars i').length"))
    print("rank:", c.eval("document.querySelectorAll('.comm-rank').length"))
    print("people:", c.eval("document.querySelectorAll('.comm-card:nth-child(3) .comm-li').length"))
    c.shot(f"{OUT}/shot_comm_v2.png")

    # 优质 tab
    c.click_text(".comm-tab", "优质")
    time.sleep(0.8)
    print("top tab rows:", c.eval("document.querySelectorAll('.comm-row').length"))
    c.click_text(".comm-tab", "全部")
    time.sleep(0.5)

    # 排序：质量最高
    c.eval("""(function(){const s=document.querySelector('.comm-sort');
      Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype,'value').set.call(s,'q');
      s.dispatchEvent(new Event('change',{bubbles:true}));return s.value})()""")
    time.sleep(0.6)
    print("sorted scores:", c.eval("[...document.querySelectorAll('.comm-score b')].map(e=>e.textContent)"))

    # ---------- 课程量规 ----------
    print("click rubric:", c.click_text("button", "课程量规"))
    c.wait_for("!!document.querySelector('.rubric-workbench')", 15)
    time.sleep(1.2)
    print("head:", c.eval("document.querySelector('.rw-current-head')?.innerText.replace(/\\s+/g,' ').trim()"))
    print("version select:", c.eval("!!document.querySelector('.rw-version')"))
    c.shot(f"{OUT}/shot_rubric_v2.png")

    print("click edit:", c.click_text(".rw-head-actions button", "编辑"))
    c.wait_for("!!document.querySelector('.rw-form')", 15)
    time.sleep(1.0)
    print("items:", c.eval("document.querySelectorAll('.rw-item').length"))
    print("inputs in item1:", c.eval("document.querySelectorAll('.rw-item')[0]?.querySelectorAll('input').length"))
    print("buttons:", c.eval("[...document.querySelectorAll('.rw-foot button')].map(e=>e.textContent.trim()+(e.disabled?'(disabled)':''))"))
    c.shot(f"{OUT}/shot_rubric_edit.png")

    c.click_text(".rw-foot button", "取消")
    time.sleep(1.0)
    print("back to view:", c.eval("!!document.querySelector('.rw-current')"))
    print("CONSOLE:", [x for x in c.console if x.startswith(("EXC", "error"))][:8])
finally:
    c.close()
