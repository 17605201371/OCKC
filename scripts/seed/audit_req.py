#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""统计首屏与页面切换的请求次数，识别重复拉取。"""
import sys, time, json
sys.path.insert(0, "/Users/zss/WorkBuddy AI/2026-10-23-13-56-59/ockc-seed")
from cdp import Chrome

BASE = "http://47.105.100.205:8080"


def login(c, email, pw="CRDaOyEIrbyTobV3"):
    c.goto(BASE, 2.0)
    c.wait_for("!!document.querySelector('input[type=email]')", 15)
    c.eval(f"""(function(){{
      const set=(el,v)=>{{const s=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;s.call(el,v);el.dispatchEvent(new Event('input',{{bubbles:true}}));}};
      set(document.querySelector('input[type=email]'),{chr(39)}{email}{chr(39)});
      set(document.querySelector('input[type=password]'),{chr(39)}{pw}{chr(39)});
      document.querySelector('form').requestSubmit();}})()""")
    return c.wait_for("!!document.querySelector('nav, .sidebar, .app-shell')", 20)


c = Chrome(width=1440, height=1000)
try:
    login(c, "teacher@example.test")
    time.sleep(3)
    # 安装请求记录器
    c.eval("""(function(){window.__req=[];const of=window.fetch;
      window.fetch=function(...a){const u=(typeof a[0]==='string')?a[0]:a[0].url;
        window.__req.push(u);return of.apply(this,a);};return 1})()""")

    c.click_text("button", "知识社区")
    time.sleep(3)
    reqs = c.eval("window.__req.filter(u=>u.includes('/api/'))")
    from collections import Counter
    cnt = Counter(reqs)
    print("切换知识社区请求数:", len(reqs))
    for u, n in cnt.items():
        print(f"  {n}x {u}")
    print("重复:", {u: n for u, n in cnt.items() if n > 1} or "无")
finally:
    c.close()
