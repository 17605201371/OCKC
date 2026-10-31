#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""体验/性能审计：页面耗时、DOM 规模、横向溢出、无名称按钮、控制台报错。"""
import sys, time, json
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
    ok = c.wait_for("!!document.querySelector('.app-shell, .sidebar, nav')", 20)
    time.sleep(1.5)
    return ok


def stats(c):
    return c.eval("""(function(){
      const all=document.querySelectorAll('*');
      let deep=0;for(const e of all){let d=0,n=e;while(n.parentElement){d++;n=n.parentElement;}if(d>deep)deep=d;}
      const sw=document.documentElement.scrollWidth, cw=document.documentElement.clientWidth;
      const res=performance.getEntriesByType('resource');
      const slow=res.filter(r=>r.duration>300).map(r=>r.name.split('/').slice(-1)[0]+' '+Math.round(r.duration)+'ms');
      return {nodes:all.length, depth:deep, overflow: sw>cw+2,
              resCount:res.length, slow:slow.slice(0,5),
              bodyText: document.body.innerText.length};
    })()""")


def unnamed(c):
    return c.eval("""(function(){
      const out=[];
      for(const b of document.querySelectorAll('button')){
        const name=(b.getAttribute('aria-label')||b.getAttribute('title')||b.innerText||'').trim();
        if(!name) out.push(b.className||'(no class)');
      }
      return out;
    })()""")


c = Chrome(width=1440, height=1080)
try:
    print("登录成功:", login(c, "teacher@example.test"))
    print("== 初始页 ==", json.dumps(stats(c), ensure_ascii=False))
    nb = unnamed(c)
    print("   无名称按钮:", nb[:8], f"(共{len(nb)})")

    steps = [("知识社区", ".comm-row"), ("证据核验", ".evidence-task, .evidence-page, .ev-*"),
             ("教学进展", ".progress"), ("课程量规", ".rubric-workbench"),
             ("批改中心", ".review-row, .review-list")]
    for label, sel in steps:
        t0 = time.time()
        r = c.click_text("button", label)
        ok = c.wait_for(f"document.querySelector({json.dumps(sel)})", 15) if "*" not in sel else True
        dt = int((time.time() - t0) * 1000)
        st = stats(c)
        print(f"== {label} == 点击={r} 到达={ok} {dt}ms", json.dumps(st, ensure_ascii=False))
        if st["overflow"]:
            print("   溢出:", c.eval("""(function(){
              const cw=document.documentElement.clientWidth;const bad=[];
              for(const e of document.querySelectorAll('*')){const r=e.getBoundingClientRect();
                if(r.right>cw+2&&r.width>0) bad.push((e.className||e.tagName)+'@'+Math.round(r.right));}
              return bad.slice(0,5);})()"""))
        nb = unnamed(c)
        if nb:
            print(f"   无名称按钮 {len(nb)}:", nb[:6])
        time.sleep(0.5)

    c.send("Emulation.setDeviceMetricsOverride",
           {"width": 1024, "height": 900, "deviceScaleFactor": 1, "mobile": False})
    time.sleep(1.0)
    print("== 1024 宽 ==", json.dumps(stats(c), ensure_ascii=False))
    c.shot(f"{OUT}/shot_audit_1024.png")
    print("== 控制台 ==", c.console[:8] or "无")
finally:
    c.close()
