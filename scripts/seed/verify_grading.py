#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证教师打分面板改版（维度评级 + 逐项微调）。"""
import sys, time
sys.path.insert(0, "/Users/zss/WorkBuddy AI/2026-10-23-13-56-59/ockc-seed")
from cdp import Chrome

BASE = "http://47.105.100.205:8080"
OUT = "/Users/zss/WorkBuddy AI/2026-10-23-13-56-59/ockc-seed"

c = Chrome(width=1600, height=1100)
try:
    c.goto(BASE, 2.0)
    c.wait_for("!!document.querySelector('input[type=email]')", 15)
    c.eval("""(function(){
      const set=(el,v)=>{const s=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;s.call(el,v);el.dispatchEvent(new Event('input',{bubbles:true}));};
      set(document.querySelector('input[type=email]'),'teacher@example.test');
      set(document.querySelector('input[type=password]'),'CRDaOyEIrbyTobV3');
      document.querySelector('form').requestSubmit();})()""")
    time.sleep(3)

    print("click review:", c.click_text("button", "批改中心"))
    c.wait_for("!!document.querySelector('.review-row')", 20)
    time.sleep(1.5)
    print("rows:", c.eval("document.querySelectorAll('.review-row').length"))

    # 打开第一份（已评分，复评视图）
    print("open row:", c.eval("(function(){const r=document.querySelector('.review-row');r.click();return r.innerText.slice(0,40)})()"))
    c.wait_for("!!document.querySelector('.grade-form')", 25)
    time.sleep(2.0)

    print("summary:", c.eval("document.querySelector('.grade-summary')?.innerText.replace(/\\n/g,' | ')"))
    print("dims:", c.eval("[...document.querySelectorAll('.grade-dim')].map(d=>d.innerText.replace(/\\n/g,' ').slice(0,60))"))
    print("active levels:", c.eval("[...document.querySelectorAll('.grade-dim')].map(d=>{const b=d.querySelector('.grade-levels button.on');return b?b.textContent.trim():'—'})"))
    print("fine closed:", c.eval("!document.querySelector('.grade-fine').open"))
    print("feedback:", c.eval("document.querySelector('.grade-feedback summary')?.textContent.trim()"))
    c.shot(f"{OUT}/shot_grading_v2.png")

    # 展开逐项微调
    c.eval("document.querySelector('.grade-fine').open=true")
    time.sleep(0.8)
    print("fine rows:", c.eval("document.querySelectorAll('.grade-fine-row').length"))
    c.eval("document.querySelector('.feedback-sidebar').scrollTop=9999")
    time.sleep(0.5)
    c.shot(f"{OUT}/shot_grading_fine.png")

    # 点击维度评级
    before = c.eval("document.querySelector('.grade-summary strong').innerText")
    c.eval("""(function(){const d=[...document.querySelectorAll('.grade-dim')][1];
      const b=[...d.querySelectorAll('.grade-levels button')][3]; b.click(); return b.textContent.trim()})()""")
    time.sleep(2.2)
    after = c.eval("document.querySelector('.grade-summary strong').innerText")
    print("score before/after:", before.replace('\n',' '), '->', after.replace('\n',' '))
    print("levels now:", c.eval("[...document.querySelectorAll('.grade-dim')].map(d=>{const b=d.querySelector('.grade-levels button.on');return b?b.textContent.trim():'—'})"))
    print("save state:", c.eval("document.querySelector('.review-save-state')?.innerText.replace(/\\n/g,' ')"))
    c.eval("document.querySelector('.feedback-sidebar').scrollTop=0")
    time.sleep(0.4)
    c.shot(f"{OUT}/shot_grading_clicked.png")

    print("CONSOLE:", [x for x in c.console if x.startswith(("EXC", "error"))][:8])
finally:
    c.close()
