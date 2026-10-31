#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""教师批改页：提交批注 → 正文高亮 → 反向定位。"""
import json, sys, time
sys.path.insert(0, ".")
from cdp import Chrome

BASE = "http://47.105.100.205:8080"
PW = "CRDaOyEIrbyTobV3"

SELECT_JS = """(function(){
  const blocks=[...document.querySelectorAll('.document-paper [data-block-id]')];
  const p=blocks.find(b=>b.tagName==='P' && (b.textContent||'').trim().length>30);
  if(!p) return 'no-paragraph';
  const walker=document.createTreeWalker(p, NodeFilter.SHOW_TEXT);
  const n=walker.nextNode();
  const len=Math.min(14, n.textContent.length);
  const r=document.createRange(); r.setStart(n,0); r.setEnd(n,len);
  const sel=window.getSelection(); sel.removeAllRanges(); sel.addRange(r);
  p.dispatchEvent(new MouseEvent('mouseup',{bubbles:true}));
  return {block:p.dataset.blockId, text:r.toString()};
})()"""

c = Chrome()
try:
    c.goto(BASE, 2.5)
    c.eval(f"""(async()=>{{
      const t = await (await fetch('/api/auth/csrf')).json();
      const body = new URLSearchParams({{username:'teacher@example.test', password:{json.dumps(PW)}}});
      await fetch('/api/auth/login', {{method:'POST', headers:{{'Content-Type':'application/x-www-form-urlencoded', [t.headerName]:t.token}}, body}});
    }})()""", await_promise=True)
    c.goto(BASE, 3.0)
    c.wait_for("document.querySelectorAll('button').length>5", 20)
    c.click_text("button", "批改中心")
    time.sleep(2.0)
    c.click_text("button", "光反应之后，物质去了哪里")
    c.wait_for("document.querySelector('.ProseMirror') !== null", 20)
    time.sleep(1.5)

    print("选中:", c.eval(SELECT_JS))
    time.sleep(0.6)
    # 选维度：逻辑性
    print("选逻辑性:", c.click_text(".annotation-dims button", "逻辑性"))
    # 填正文
    c.eval("""(function(){
      const ta=document.querySelector('.annotation-popover textarea');
      const setter=Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype,'value').set;
      setter.call(ta,'这里把「水分解伴随氧气释放」写成了并列项，建议点明它与 ATP/NADPH 生成是同一光反应的耦合结果。');
      ta.dispatchEvent(new Event('input',{bubbles:true}));
      return ta.value.length;
    })()""")
    time.sleep(0.3)
    print("提交批注:", c.click_text(".annotation-actions button", "添加批注"))
    time.sleep(3.0)
    print("浮层已关闭:", c.eval("!document.querySelector('.annotation-popover')"))
    print("正文高亮数:", c.eval("document.querySelectorAll('.annotation-mark').length"))
    print("高亮文本:", c.eval("document.querySelector('.annotation-mark')?.textContent"))
    print("高亮类:", c.eval("document.querySelector('.annotation-mark')?.className"))
    c.shot("/tmp/ui_after_annot.png")

    # 点击正文高亮 → 右侧定位
    print("点高亮:", c.eval("""(function(){
      const m=document.querySelector('.annotation-mark');
      if(!m) return 'no-mark';
      m.dispatchEvent(new MouseEvent('click',{bubbles:true}));
      return 'ok';
    })()"""))
    time.sleep(1.2)
    print("右侧高亮卡片:", c.eval("document.querySelector('.feedback-card.is-target')?.id"))
    c.shot("/tmp/ui_jump.png")
    print("console:", c.console[-8:])
finally:
    c.close()
