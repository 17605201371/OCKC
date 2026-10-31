#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验收：长文排版（多级大纲/行内公式/表格）+ 图片批注入口。"""
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
      await fetch('/api/auth/login', {{method:'POST', headers:{{'Content-Type':'application/x-www-form-urlencoded', [t.headerName]:t.token}}, body}});
    }})()""", await_promise=True)
    c.goto(BASE, 3.0)
    c.wait_for("document.querySelectorAll('button').length>5", 20)

c = Chrome()
try:
    login(c, "teacher@example.test")
    c.click_text("button", "批改中心"); time.sleep(2.5)
    # 翻到含长文的一页
    for _ in range(4):
        if c.eval("""[...document.querySelectorAll('button')].some(b=>b.textContent.includes('一克葡萄糖的去向'))"""):
            break
        if c.click_text("button", "下一页") != "ok":
            break
        time.sleep(1.6)
    print("找到长文:", c.click_text("button", "一克葡萄糖的去向"))
    c.wait_for("document.querySelector('.ProseMirror') && document.querySelector('.ProseMirror').children.length > 5", 25)
    time.sleep(1.5)
    R["ProseMirror 子节点"] = c.eval("document.querySelector('.ProseMirror')?.children.length")
    R["大纲条数"] = c.eval("document.querySelectorAll('.document-outline nav button').length")
    R["大纲编号"] = c.eval("[...document.querySelectorAll('.document-outline nav button')].slice(0,8).map(b=>b.querySelector('span').textContent)")
    R["大纲缩进"] = c.eval("[...document.querySelectorAll('.document-outline nav button')].slice(0,6).map(b=>getComputedStyle(b).paddingLeft)")
    R["行内公式数"] = c.eval("document.querySelectorAll('.math-inline').length")
    R["块公式数"] = c.eval("document.querySelectorAll('.knowledge-math').length")
    R["表格数"] = c.eval("document.querySelectorAll('.ProseMirror table').length")
    R["图块数"] = c.eval("document.querySelectorAll('.knowledge-figure').length")
    R["批注按钮数"] = c.eval("document.querySelectorAll('.figure-annotate').length")
    print(json.dumps(R, ensure_ascii=False, indent=1))
    c.eval("document.querySelector('.knowledge-figure')?.scrollIntoView({block:'center'})")
    time.sleep(0.6)
    print("点图批注:", c.eval("""(function(){
      const b=document.querySelector('.figure-annotate');
      if(!b) return 'no-btn';
      b.click(); return 'ok';
    })()"""))
    time.sleep(0.8)
    R["图片批注浮层"] = c.eval("!!document.querySelector('.annotation-popover')")
    R["浮层标题"] = c.eval("document.querySelector('.annotation-popover-head strong')?.textContent")
    R["浮层引文"] = c.eval("document.querySelector('.annotation-quote')?.textContent")
    c.shot("/tmp/v2_figure_annot.png")
    c.eval("document.querySelector('.annotation-close')?.click()")
    c.eval("document.querySelector('.document-outline')?.scrollIntoView({block:'start'})")
    time.sleep(0.4)
    c.shot("/tmp/v2_outline.png")
    R["console"] = [x for x in c.console if "error" in x.lower() or "EXC" in x][:3]
finally:
    c.close()

print(json.dumps(R, ensure_ascii=False, indent=1))
