#!/usr/bin/env python3
"""为长文知识帖补充 3 张配图：HTML -> Chrome headless 截图 -> PNG"""
import subprocess, pathlib, math

OUT = pathlib.Path("/tmp/ockc_seed/figs")
OUT.mkdir(parents=True, exist_ok=True)
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

CSS = """
*{box-sizing:border-box;margin:0;padding:0}
body{width:1200px;height:100vh;font-family:"PingFang SC","Hiragino Sans GB","Helvetica Neue",sans-serif;
 background:#fbfcfe;color:#1b2531;padding:38px 52px;position:relative;overflow:hidden}
.brand{position:absolute;right:52px;top:32px;font-size:12.5px;letter-spacing:.14em;color:#9aa7b6}
h1{font-size:27px;font-weight:700;letter-spacing:-.01em}
.sub{margin-top:8px;font-size:13.5px;color:#64748b;line-height:1.6;max-width:1020px}
.rule{width:54px;height:4px;background:#2563eb;border-radius:2px;margin:14px 0 20px}
.wrap{display:flex;gap:14px;align-items:stretch}
.card{flex:1;background:#fff;border:1px solid #e6ecf3;border-radius:14px;padding:16px 16px 14px;
 box-shadow:0 1px 2px rgba(16,24,40,.04),0 8px 24px -18px rgba(16,24,40,.35)}
.card .idx{font-size:11px;font-weight:700;letter-spacing:.1em}
.card h3{font-size:15.5px;margin:6px 0 5px}
.card p{font-size:12.5px;color:#5b6b7d;line-height:1.6}
.tag{display:inline-block;font-size:11px;padding:3px 8px;border-radius:999px;background:#eef4ff;color:#2563eb;margin-top:8px}
.arrow{align-self:center;color:#c3cdda;font-size:18px}
.foot{position:absolute;left:52px;bottom:20px;font-size:11px;color:#94a3b8}
.legend{display:flex;gap:18px;margin-top:6px;font-size:12px;color:#64748b}
.dot{width:10px;height:10px;border-radius:3px;display:inline-block;margin-right:6px;vertical-align:-1px}
"""

def page(title, sub, body, foot="", h=470):
    return f"""<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><style>{CSS}</style></head>
<body><div class="brand">课程知识帖 · 配图</div>
<h1>{title}</h1><div class="sub">{sub}</div><div class="rule"></div>{body}
{f'<div class="foot">{foot}</div>' if foot else ''}</body></html>"""

def shot(name, html, h=470):
    hp = OUT / f"{name}.html"
    hp.write_text(html, encoding="utf-8")
    png = OUT / f"{name}.png"
    cmd = [CHROME, "--headless=new", "--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage",
           "--hide-scrollbars", "--force-device-scale-factor=1", "--default-background-color=ffffff",
           f"--window-size=1200,{h}", f"--screenshot={png}", f"file://{hp}"]
    subprocess.run(cmd, capture_output=True, timeout=90)
    print(("OK " if png.exists() else "FAIL ") + name, png.stat().st_size // 1024 if png.exists() else "")
    return png

# ---------- 1. 从光到 ATP 的能量链 ----------
chain = [("①","光反应","类囊体薄膜","H₂O 光解 → O₂ + H⁺ + e⁻","把光能转成 ATP 与 NADPH","#2563eb"),
         ("②","卡尔文循环","叶绿体基质","CO₂ + ATP + NADPH → 三碳糖","把活跃化学能存进有机物","#7c3aed"),
         ("③","细胞呼吸","细胞质 + 线粒体","有机物逐级脱氢、电子传递","把有机物里的能放回 ATP","#0891b2")]
cards = '<div class="arrow">→</div>'.join(
  f'<div class="card"><div class="idx" style="color:{c}">{i}</div><h3>{n}</h3>'
  f'<p><b style="color:#334155">{w}</b></p><p>{d}</p><span class="tag">{t}</span></div>'
  for i, n, w, d, t, c in chain)
shot("energy_chain", page(
  "一条链上的能量：光 → 有机物 → ATP",
  "三个环节首尾相接，任何一环被切断，后面都无法凭空补上；写解释时要把「能量形式的变化」和「发生场所」同时说清。",
  f'<div class="wrap">{cards}</div>',
  "注意：ATP 不是能量的储存形式，而是能量的通用「搬运工」；储存靠有机物，搬运靠 ATP。", 470))

# ---------- 2. 光强—光合速率曲线（SVG 采样） ----------
W, H, L, T = 1000, 290, 86, 26
def X(v): return L + v / 1600 * W
def Y(v): return T + H - v / 26 * H
pts = []
for k in range(0, 1601, 20):
    y = 24 * (1 - math.exp(-k / 380)) + 0.9 * (k / 1600)
    pts.append(f"{X(k):.1f},{Y(y):.1f}")
poly = " ".join(pts)
light_curve = f"""
<svg width="1040" height="360" viewBox="0 0 1120 370">
 <g stroke="#e6ecf3" stroke-width="1">
  {''.join(f'<line x1="{L}" y1="{Y(v):.1f}" x2="{L+W}" y2="{Y(v):.1f}"/>' for v in range(0,27,5))}
  {''.join(f'<line x1="{X(v):.1f}" y1="{T}" x2="{X(v):.1f}" y2="{T+H}"/>' for v in range(0,1601,200))}
 </g>
 <line x1="{L}" y1="{T+H}" x2="{L+W}" y2="{T+H}" stroke="#94a3b8" stroke-width="1.5"/>
 <line x1="{L}" y1="{T}" x2="{L}" y2="{T+H}" stroke="#94a3b8" stroke-width="1.5"/>
 <polyline fill="none" stroke="#2563eb" stroke-width="3" stroke-linecap="round" points="{poly}"/>
 <line x1="{X(1100):.1f}" y1="{T}" x2="{X(1100):.1f}" y2="{T+H}" stroke="#f59e0b" stroke-width="1.5" stroke-dasharray="6 5"/>
 <text x="{X(1100)+9:.1f}" y="{T+16}" font-size="12.5" fill="#b45309">光饱和点</text>
 <text x="{L-60}" y="{T+8}" font-size="12.5" fill="#64748b">光合速率</text>
 <text x="{L+W-104}" y="{T+H-8}" font-size="12.5" fill="#94a3b8">光强 / lx</text>
 {''.join(f'<text x="{min(X(v), L+W-16):.1f}" y="{T+H+20}" font-size="11.5" fill="#94a3b8">{v}</text>' for v in range(0,1601,400))}
 {''.join(f'<text x="{L-28}" y="{Y(v)+4:.1f}" font-size="11.5" fill="#94a3b8">{v}</text>' for v in range(0,26,5))}
</svg>"""
shot("light_curve", page(
  "光强增加，光合速率为什么不会一直涨",
  "曲线先近似线性上升，随后变缓并趋于平台；平台的成因是暗反应（酶与 CO₂ 供应）跟不上光反应的产物输出。",
  light_curve,
  "读图要点：① 起点不为零（有呼吸作用的基线）② 拐点附近是「光反应不再是限制因素」的位置 ③ 平台高度受温度和 CO₂ 浓度影响。", 470), 660)

# ---------- 3. 误差分析：散点 + 拟合 + 残差 ----------
import random
random.seed(11)
data = [(0.10, 0.62), (0.15, 1.18), (0.20, 1.52), (0.25, 2.34), (0.30, 2.46),
        (0.35, 3.28), (0.40, 3.40), (0.45, 4.14), (0.50, 4.38), (0.55, 5.10)]
def fit(ds):
    n = len(ds); sx = sum(x for x, _ in ds); sy = sum(y for _, y in ds)
    sxx = sum(x*x for x, _ in ds); sxy = sum(x*y for x, y in ds)
    k = (n*sxy - sx*sy) / (n*sxx - sx*sx); b = (sy - k*sx) / n
    return k, b
k, b = fit(data)
W2, H2, L2, T2 = 900, 290, 88, 26
def X2(v): return L2 + (v - 0.05) / 0.55 * W2
def Y2(v): return T2 + H2 - (v - 0.0) / 5.8 * H2
dots = "".join(f'<circle cx="{X2(x):.1f}" cy="{Y2(y):.1f}" r="5.5" fill="#2563eb" opacity=".9"/>' for x, y in data)
resid = "".join(f'<line x1="{X2(x):.1f}" y1="{Y2(y):.1f}" x2="{X2(x):.1f}" y2="{Y2(k*x+b):.1f}" stroke="#f59e0b" stroke-width="2" stroke-dasharray="4 3"/>' for x, y in data)
line = f'<line x1="{X2(0.08):.1f}" y1="{Y2(k*0.08+b):.1f}" x2="{X2(0.57):.1f}" y2="{Y2(k*0.57+b):.1f}" stroke="#dc2626" stroke-width="2.4"/>'
xticks = "".join(f'<text x="{X2(v)-12:.1f}" y="{T2+H2+20}" font-size="11.5" fill="#94a3b8">{v}</text>' for v in [0.1,0.2,0.3,0.4,0.5])
yticks = "".join(f'<text x="{L2-24}" y="{Y2(v)+4:.1f}" font-size="11.5" fill="#94a3b8">{v}</text>' for v in [1,2,3,4,5])
scatter = f"""<svg width="1040" height="360" viewBox="0 0 1120 370">
 <g stroke="#e6ecf3" stroke-width="1">
  {''.join(f'<line x1="{L2}" y1="{Y2(v):.1f}" x2="{L2+W2}" y2="{Y2(v):.1f}"/>' for v in [1,2,3,4,5])}
  {''.join(f'<line x1="{X2(v):.1f}" y1="{T2}" x2="{X2(v):.1f}" y2="{T2+H2}"/>' for v in [0.1,0.2,0.3,0.4,0.5])}
 </g>
 <line x1="{L2}" y1="{T2+H2}" x2="{L2+W2}" y2="{T2+H2}" stroke="#94a3b8" stroke-width="1.5"/>
 <line x1="{L2}" y1="{T2}" x2="{L2}" y2="{T2+H2}" stroke="#94a3b8" stroke-width="1.5"/>
 {resid}{line}{dots}{xticks}{yticks}
 <text x="{L2+W2-58}" y="{T2+H2-8}" font-size="12.5" fill="#94a3b8">sinθ</text>
 <text x="{L2-62}" y="{T2+10}" font-size="12.5" fill="#64748b">加速度 a</text>
 <text x="{L2+14}" y="{T2+26}" font-size="12.5" fill="#dc2626">拟合直线 a = {k:.2f}sinθ − {abs(b):.2f}</text>
</svg>"""
shot("error_scatter", page(
  "误差不是结论的反面：先看残差，再谈结论",
  "橙色虚线是每个数据点到拟合直线的残差。残差随机分布在直线两侧，说明误差主要来自读数；若残差呈系统弯折，则模型本身有问题。",
  scatter,
  "写作提示：报告结论时同时给出拟合参数、残差范围和最大偏离点，比只写一个平均值更可核查。", 470), 660)
print("done")
