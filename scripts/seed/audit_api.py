#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""接口耗时测量：登录后在页面内 fetch 关键端点，记录耗时与响应体积。"""
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
    print("登录:", login(c, "teacher@example.test"))
    time.sleep(2)

    courses = c.eval("fetch('/api/courses').then(r=>r.json()).then(d=>d.items||d)", True)
    cid = courses[0]["id"]
    print("课程:", cid, courses[0].get("title"))

    eps = [
        ("/api/me", "me"),
        (f"/api/courses/{cid}/assignments", "作业列表"),
        (f"/api/courses/{cid}/publications", "知识社区列表"),
        (f"/api/courses/{cid}/review-workbench", "批改队列"),
        (f"/api/courses/{cid}/evidence", "证据列表"),
        (f"/api/courses/{cid}/progress", "进度页"),
        (f"/api/courses/{cid}/rubrics", "量规"),
    ]
    for url, name in eps:
        js = f"""(async()=>{{const t=performance.now();
          const r=await fetch({json.dumps(url)});const txt=await r.text();
          return {{ms:Math.round(performance.now()-t),bytes:txt.length,code:r.status}};}})()"""
        r = c.eval(js, True)
        print(f"{name:12s} {r['code']} {r['ms']:5d}ms {r['bytes']/1024:8.1f}KB  {url}")
finally:
    c.close()
