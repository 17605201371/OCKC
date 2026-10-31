#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""上线后整体验收：教师浮层 / 学生高亮双向定位 / 面板收起 / AI 建议。"""
import json, sys, time
sys.path.insert(0, ".")
from cdp import Chrome

BASE = "http://47.105.100.205:8080"
PW = "CRDaOyEIrbyTobV3"
R = {}

def login(c, user):
    c.goto(BASE, 2.5)
    c.eval(f"""(async()=>{{
      const t = await (await fetch('/api/auth/csrf')).json();
      const body = new URLSearchParams({{username:{json.dumps(user)}, password:{json.dumps(PW)}}});
      const r = await fetch('/api/auth/login', {{method:'POST', headers:{{'Content-Type':'application/x-www-form-urlencoded', [t.headerName]:t.token}}, body}});
      return r.status;
    }})()""", await_promise=True)
    c.goto(BASE, 3.0)
    c.wait_for("document.querySelectorAll('button').length>5", 20)

# ---------- 教师 ----------
c = Chrome()
try:
    login(c, "teacher@example.test")
    c.click_text("button", "批改中心"); time.sleep(2.0)
    c.click_text("button", "光反应之后，物质去了哪里")
    c.wait_for("document.querySelector('.ProseMirror') !== null", 20); time.sleep(1.2)
    R["教师页 data-block-id"] = c.eval("document.querySelectorAll('.document-paper [data-block-id]').length")
    R["提交时量规已隐藏"] = c.eval("!document.querySelector('.rubric-snapshot')")
    R["来源与核验默认收起"] = c.eval("(()=>{const d=document.querySelector('.source-panel'); return d? !d.open : null})()")
    R["正文既有高亮"] = c.eval("document.querySelectorAll('.annotation-mark').length")
    c.eval("""(function(){
      const blocks=[...document.querySelectorAll('.document-paper [data-block-id]')];
      const p=blocks.find(b=>b.tagName==='P' && (b.textContent||'').trim().length>30);
      const n=document.createTreeWalker(p, NodeFilter.SHOW_TEXT).nextNode();
      const r=document.createRange(); r.setStart(n,0); r.setEnd(n,Math.min(12,n.textContent.length));
      const s=window.getSelection(); s.removeAllRanges(); s.addRange(r);
      p.dispatchEvent(new MouseEvent('mouseup',{bubbles:true}));
    })()""")
    time.sleep(0.7)
    R["选中浮层"] = c.eval("!!document.querySelector('.annotation-popover')")
    R["浮层维度"] = c.eval("[...document.querySelectorAll('.annotation-dims button')].map(b=>b.textContent.trim())")
    R["浮层只有文本框无子指标"] = c.eval("document.querySelectorAll('.annotation-popover select').length === 0")
    c.shot("/tmp/final_teacher.png")
    # 面板收起
    c.eval("document.querySelector('.panel-collapse')?.click()"); time.sleep(0.5)
    R["面板可收起"] = c.eval("!document.querySelector('.feedback-sidebar') || getComputedStyle(document.querySelector('.feedback-sidebar')).display==='none'")
    R["重开按钮出现"] = c.eval("!!document.querySelector('.panel-reopen')")
    c.eval("document.querySelector('.panel-reopen')?.click()"); time.sleep(0.4)
    R["面板可恢复"] = c.eval("!!document.querySelector('.feedback-sidebar')")
    R["console 错误"] = [x for x in c.console if "error" in x.lower() or "EXC" in x][:3]
finally:
    c.close()

# ---------- 学生 ----------
c = Chrome()
try:
    login(c, "student@example.test")
    c.click_text("button", "我的作业"); time.sleep(2.0)
    marks_page = None
    ai_page = None
    for i in range(5):
        c.eval(f"""(function(){{
          const bs=[...document.querySelectorAll('button')].filter(b=>b.textContent.trim()==='继续编辑');
          if(bs[{i}]) bs[{i}].click();
        }})()""")
        time.sleep(2.4)
        if marks_page is None and c.eval("document.querySelectorAll('.annotation-mark').length"):
            marks_page = i
            R["学生正文高亮数"] = c.eval("document.querySelectorAll('.annotation-mark').length")
            R["学生选中不弹教师浮层"] = c.eval("""(function(){
              const m=document.querySelector('.annotation-mark');
              const p=m.closest('[data-block-id]');
              const n=document.createTreeWalker(p, NodeFilter.SHOW_TEXT).nextNode();
              const r=document.createRange(); r.setStart(n,0); r.setEnd(n,Math.min(5,n.textContent.length));
              const s=window.getSelection(); s.removeAllRanges(); s.addRange(r);
              p.dispatchEvent(new MouseEvent('mouseup',{bubbles:true}));
              return !document.querySelector('.annotation-popover');
            })()""")
            c.eval("document.querySelectorAll('.annotation-mark')[0].dispatchEvent(new MouseEvent('click',{bubbles:true}))")
            time.sleep(1.0)
            R["点高亮→右侧卡片"] = c.eval("document.querySelector('.feedback-card.is-target')?.id")
            c.shot("/tmp/final_student.png")
        if ai_page is None and c.eval("!!document.querySelector('#sidebar-ai')"):
            ai_page = i
            R["AI 建议条数"] = c.eval("document.querySelectorAll('.ai-suggestion').length")
            c.eval("document.querySelector('#sidebar-ai').scrollIntoView({block:'center'})"); time.sleep(0.5)
            c.shot("/tmp/final_ai.png")
        if marks_page is not None and ai_page is not None:
            break
        c.click_text("button", "返回"); time.sleep(1.5)
        c.click_text("button", "我的作业"); time.sleep(1.5)
    R["学生高亮命中页"] = marks_page
    R["学生 AI 命中页"] = ai_page
finally:
    c.close()

print(json.dumps(R, ensure_ascii=False, indent=1))
