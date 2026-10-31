#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""部署后回归：缓存/压缩生效、页面可用、反馈与进度数据正常。"""
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
    return c.wait_for("!!document.querySelector('nav, .sidebar, .app-shell')", 20)


c = Chrome(width=1440, height=1080)
try:
    print("登录:", login(c, "teacher@example.test"))
    time.sleep(2.5)

    print("== 首屏资源 ==", json.dumps(c.eval("""(function(){
      return performance.getEntriesByType('resource')
        .filter(r=>/assets\\/index-|assets\\/Editor-|theme-init/.test(r.name))
        .map(r=>({f:r.name.split('/').pop(), ms:Math.round(r.duration),
                  transfer:Math.round(r.transferSize/1024), decoded:Math.round(r.decodedBodySize/1024)}));
    })()"""), ensure_ascii=False))

    print("== 图标请求 ==", c.eval("""(function(){
      const e=performance.getEntriesByType('resource').find(r=>/favicon/.test(r.name));
      return e?{f:e.name.split('/').pop(),ms:Math.round(e.duration),code:e.responseStatus}:null})()"""))

    # 各页可用性
    for label, sel in [("知识社区", ".comm-row"), ("教学进展", ".progress-entry"),
                       ("证据核验", ".page"), ("课程量规", ".rw-current")]:
        t0 = time.time()
        c.click_text("button", label)
        ok = c.wait_for(f"document.querySelector({json.dumps(sel)})", 15)
        print(f"== {label} == 到达={ok} {int((time.time()-t0)*1000)}ms 节点={c.eval('document.querySelectorAll(\"*\").length')}")

    # 批改中心 → 打开第一份提交，检验 feedback 批量查询
    c.click_text("button", "批改中心")
    c.wait_for("document.querySelector('.review-row, .review-list button, .review-workbench tbody tr')", 15)
    time.sleep(1)
    opened = c.eval("""(function(){
      const b=document.querySelector('.review-row, .review-workbench tbody tr button, .review-list button');
      if(!b) return 'no-row'; b.click(); return 'ok';})()""")
    time.sleep(2.5)
    print("== 打开评审 ==", opened,
          "| 面板:", c.eval("!!document.querySelector('.grade-summary')"),
          "| 批注条数:", c.eval("document.querySelectorAll('[id^=feedback-]').length"),
          "| 标题:", c.eval("(document.querySelector('.grade-summary')?.parentElement?.innerText||'').slice(0,40).replace(/\\s+/g,' ')"))

    # 二次加载：哈希资源应命中缓存
    c.send("Network.setCacheDisabled", {"cacheDisabled": False})
    c.goto(BASE, 2.5)
    c.wait_for("!!document.querySelector('input[type=email]')", 15)
    print("== 二次加载资源 ==", json.dumps(c.eval("""(function(){
      return performance.getEntriesByType('resource')
        .filter(r=>/assets\\/index-|theme-init/.test(r.name))
        .map(r=>({f:r.name.split('/').pop(), ms:Math.round(r.duration),
                  transfer:Math.round(r.transferSize/1024)}));
    })()"""), ensure_ascii=False))

    print("== 控制台 ==", c.console[:8] or "无")
finally:
    c.close()
