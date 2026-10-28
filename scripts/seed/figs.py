#!/usr/bin/env python3
"""生成知识帖配图：HTML -> Chrome headless 截图 -> PNG"""
import subprocess, pathlib, math

OUT = pathlib.Path("/tmp/ockc_seed/figs")
OUT.mkdir(parents=True, exist_ok=True)
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

BASE_CSS = """
* { box-sizing: border-box; margin:0; padding:0; }
body { width:1200px; height:100vh; font-family:"PingFang SC","Hiragino Sans GB","Helvetica Neue",sans-serif;
  background:#fbfcfe; color:#1b2531; padding:40px 52px; position:relative; overflow:hidden; }
.brand { position:absolute; right:52px; top:34px; font-size:12.5px; letter-spacing:.14em; color:#9aa7b6; }
h1 { font-size:28px; font-weight:700; letter-spacing:-.01em; }
.sub { margin-top:8px; font-size:14px; color:#64748b; line-height:1.6; max-width:1000px; }
.rule { width:54px; height:4px; background:#2563eb; border-radius:2px; margin:16px 0 24px; }
.wrap { display:flex; gap:16px; align-items:stretch; }
.card { flex:1; background:#fff; border:1px solid #e6ecf3; border-radius:14px; padding:18px 18px 16px;
  box-shadow:0 1px 2px rgba(16,24,40,.04), 0 8px 24px -18px rgba(16,24,40,.35); }
.card .idx { font-size:11.5px; font-weight:700; color:#2563eb; letter-spacing:.1em; }
.card h3 { font-size:16.5px; margin:7px 0 6px; }
.card p { font-size:13px; color:#5b6b7d; line-height:1.6; }
.tag { display:inline-block; font-size:11.5px; padding:3px 9px; border-radius:999px; background:#eef4ff; color:#2563eb; margin-top:9px;}
.arrow { align-self:center; color:#c3cdda; font-size:20px; }
.bar-row { display:flex; align-items:center; gap:14px; margin-bottom:14px; }
.bar-label { width:200px; font-size:13.5px; color:#334155; text-align:right; }
.bar-track { flex:1; height:24px; background:#eef2f7; border-radius:6px; overflow:hidden; }
.bar-fill { height:100%; border-radius:6px; }
.bar-val { width:92px; font-size:13px; color:#475569; font-variant-numeric:tabular-nums; }
.foot { position:absolute; left:52px; bottom:24px; font-size:11.5px; color:#94a3b8; }
.legend { display:flex; gap:20px; margin-top:4px; font-size:12.5px; color:#64748b; }
.dot { width:10px; height:10px; border-radius:3px; display:inline-block; margin-right:6px; vertical-align:-1px;}
"""

def page(title, sub, body, foot="", h=480):
    return f"""<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><style>{BASE_CSS}</style></head>
<body><div class="brand">课程知识帖 · 配图</div>
<h1>{title}</h1><div class="sub">{sub}</div><div class="rule"></div>{body}
{f'<div class="foot">{foot}</div>' if foot else ''}</body></html>"""

def shot(name, html, h=480):
    hp = OUT / f"{name}.html"
    hp.write_text(html, encoding="utf-8")
    png = OUT / f"{name}.png"
    cmd = [CHROME, "--headless=new", "--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage",
           "--hide-scrollbars", "--force-device-scale-factor=1",
           "--default-background-color=ffffff", f"--window-size=1200,{h}",
           f"--screenshot={png}", f"file://{hp}"]
    subprocess.run(cmd, capture_output=True, timeout=90)
    if not png.exists():
        print("FAIL", name); return None
    print("OK", name, png.stat().st_size // 1024, "KB")
    return png

# ---------- 1. 细胞呼吸三阶段 ----------
steps = [("第一阶段","糖酵解","细胞质基质","1 葡萄糖 → 2 丙酮酸","净得 2 ATP + 2 NADH","#2563eb"),
         ("第二阶段","三羧酸循环","线粒体基质","2 丙酮酸 → 6 CO₂","2 ATP + 8 NADH + 2 FADH₂","#7c3aed"),
         ("第三阶段","氧化磷酸化","线粒体内膜","NADH / FADH₂ 传递电子","约 26–28 ATP","#0891b2")]
cards = '<div class="arrow">→</div>'.join(
  f'<div class="card"><div class="idx" style="color:{c}">{i}</div><h3>{n}</h3>'
  f'<p><b style="color:#334155">场所：</b>{loc}</p><p><b style="color:#334155">反应：</b>{r}</p>'
  f'<div class="tag" style="background:{c}14;color:{c}">{o}</div></div>'
  for (i,n,loc,r,o,c) in steps)
shot("respiration_stages", page(
  "细胞呼吸：三个阶段与能量账本",
  "同一分子葡萄糖在三个场所依次被拆解，ATP 并非平均分配——绝大部分来自最后一步的电子传递。",
  f'<div class="wrap">{cards}</div>',
  "数据来源：《生物化学》糖的有氧氧化通路；ATP 计数据经典教材估算区间", 400), 400)

# ---------- 2. ATP 产出对比 ----------
bars = [("糖酵解（底物水平磷酸化）",2,"#60a5fa"),("三羧酸循环（底物水平磷酸化）",2,"#7c3aed"),
        ("氧化磷酸化（化学渗透）",27,"#0891b2")]
rows = "".join(f'<div class="bar-row"><div class="bar-label">{n}</div>'
  f'<div class="bar-track"><div class="bar-fill" style="width:{v/27*100:.1f}%;background:{c}"></div></div>'
  f'<div class="bar-val">{v} ATP</div></div>' for n,v,c in bars)
shot("respiration_atp", page(
  "1 分子葡萄糖的 ATP 产出分布",
  "把三段的产出放在同一把尺子上：氧化磷酸化贡献约 87%，这解释了为什么缺氧时能量供应会骤降。",
  f'{rows}<div class="legend"><span><i class="dot" style="background:#0891b2"></i>主要来源</span>'
  f'<span><i class="dot" style="background:#7c3aed"></i>循环自身产出</span>'
  f'<span><i class="dot" style="background:#60a5fa"></i>起始阶段</span></div>',
  "合计约 30–32 ATP，随穿梭系统与组织差异浮动", 430), 430)

# ---------- 3. 斜面受力分解（几何计算绘制） ----------
AX, AY = 130, 400           # 斜面左下顶点
BX, BY = 900, 400           # 斜面右下顶点
CX, CY = 900, 160           # 斜面右上顶点（直角）
dx, dy = CX - AX, CY - AY   # 斜面方向向量（向上）
L = math.hypot(dx, dy)
ux, uy = dx / L, dy / L     # 上坡单位向量
nx, ny = uy, -ux            # 外法线（指向上方）
theta = math.degrees(math.atan2(-dy, dx))
# 物体中心：沿斜面 45% 处，沿法线外移 26px
PX = AX + dx * 0.45 + nx * 26
PY = AY + dy * 0.45 + ny * 26
def pt(vec, k):  # 从物体中心沿方向画箭头端点
    return (PX + vec[0]*k, PY + vec[1]*k)
g_end   = pt((0, 1), 108)
n_end   = pt((nx, ny), 92)
f_end   = pt((ux, uy), 86)                       # 摩擦力沿坡向上
sd_end  = pt((-ux, -uy), 88)                     # 重力沿坡向下分量
sc_end  = pt((-nx, -ny), 88)                     # 重力垂直分量
svg_force = f"""
<svg width="1120" height="470" viewBox="0 0 1120 470">
  <defs>
    <marker id="mR" markerWidth="9" markerHeight="9" refX="7" refY="3" orient="auto"><path d="M0,0 L0,6 L8,3 z" fill="#dc2626"/></marker>
    <marker id="mG" markerWidth="9" markerHeight="9" refX="7" refY="3" orient="auto"><path d="M0,0 L0,6 L8,3 z" fill="#059669"/></marker>
    <marker id="mB" markerWidth="9" markerHeight="9" refX="7" refY="3" orient="auto"><path d="M0,0 L0,6 L8,3 z" fill="#2563eb"/></marker>
    <marker id="mGy" markerWidth="8" markerHeight="8" refX="6" refY="2.5" orient="auto"><path d="M0,0 L0,5 L7,2.5 z" fill="#94a3b8"/></marker>
  </defs>
  <path d="M{AX},{AY} L{BX},{BY} L{CX},{CY} Z" fill="#f1f5f9" stroke="#94a3b8" stroke-width="2"/>
  <line x1="90" y1="{AY}" x2="980" y2="{AY}" stroke="#cbd5e1" stroke-width="2"/>
  <path d="M{AX+96},{AY} A96,96 0 0,0 {AX+96*math.cos(math.radians(theta)):.1f},{AY-96*math.sin(math.radians(theta)):.1f}"
        fill="none" stroke="#64748b" stroke-width="1.6"/>
  <text x="{AX+104}" y="{AY-30}" font-size="14" fill="#475569">θ = {theta:.1f}°</text>
  <g transform="translate({PX:.1f},{PY:.1f}) rotate({-theta:.2f})">
    <rect x="-24" y="-24" width="48" height="48" rx="6" fill="#dbeafe" stroke="#2563eb" stroke-width="2"/>
  </g>
  <line x1="{PX:.1f}" y1="{PY:.1f}" x2="{g_end[0]:.1f}" y2="{g_end[1]:.1f}" stroke="#dc2626" stroke-width="3" marker-end="url(#mR)"/>
  <text x="{g_end[0]+8:.1f}" y="{g_end[1]+4:.1f}" font-size="14" fill="#dc2626">G = mg</text>
  <line x1="{PX:.1f}" y1="{PY:.1f}" x2="{n_end[0]:.1f}" y2="{n_end[1]:.1f}" stroke="#059669" stroke-width="3" marker-end="url(#mG)"/>
  <text x="{n_end[0]-26:.1f}" y="{n_end[1]-6:.1f}" font-size="14" fill="#059669">N</text>
  <line x1="{PX:.1f}" y1="{PY:.1f}" x2="{f_end[0]:.1f}" y2="{f_end[1]:.1f}" stroke="#2563eb" stroke-width="3" marker-end="url(#mB)"/>
  <text x="{f_end[0]+6:.1f}" y="{f_end[1]-8:.1f}" font-size="14" fill="#2563eb">f</text>
  <line x1="{PX:.1f}" y1="{PY:.1f}" x2="{sd_end[0]:.1f}" y2="{sd_end[1]:.1f}" stroke="#94a3b8" stroke-width="2" stroke-dasharray="5 4" marker-end="url(#mGy)"/>
  <text x="{sd_end[0]-118:.1f}" y="{sd_end[1]+16:.1f}" font-size="13" fill="#64748b">mg·sinθ</text>
  <line x1="{PX:.1f}" y1="{PY:.1f}" x2="{sc_end[0]:.1f}" y2="{sc_end[1]:.1f}" stroke="#94a3b8" stroke-width="2" stroke-dasharray="5 4" marker-end="url(#mGy)"/>
  <text x="{sc_end[0]+10:.1f}" y="{sc_end[1]+4:.1f}" font-size="13" fill="#64748b">mg·cosθ</text>
  <text x="{BX-70}" y="{AY+22}" font-size="13" fill="#94a3b8">水平面</text>
</svg>"""
shot("incline_forces", page(
  "斜面上的物体：把重力拆成两个有用分量",
  "沿斜面方向的 mg·sinθ 决定下滑趋势，垂直方向的 mg·cosθ 决定正压力与摩擦力上限；摩擦力 f 总沿坡向上。",
  svg_force, "示意图由作者依据受力分析规则绘制，非实验照片", 700), 700)

# ---------- 4. a-θ 曲线（真实采样） ----------
mu, g0 = 0.25, 9.8
def accel(deg):
    th = math.radians(deg)
    return g0 * (math.sin(th) - mu * math.cos(th))
X0, X1, Y0, Y1 = 110, 1000, 380, 90
def mapx(deg): return X0 + deg / 90 * (X1 - X0)
def mapy(a):   return Y0 - a / 9.8 * (Y0 - Y1)
pts = [(d, accel(d)) for d in range(0, 91, 2)]
moving = [(d, a) for d, a in pts if a > 0]
path = " ".join(f"{'M' if i==0 else 'L'}{mapx(d):.1f},{mapy(a):.1f}" for i, (d, a) in enumerate(moving))
theta0 = math.degrees(math.atan(mu))
svg_curve = f"""
<svg width="1120" height="440" viewBox="0 0 1120 440">
  <line x1="{X0-30}" y1="{Y0}" x2="{X1+30}" y2="{Y0}" stroke="#cbd5e1" stroke-width="2"/>
  <line x1="{X0-30}" y1="{Y0}" x2="{X0-30}" y2="{Y1-20}" stroke="#cbd5e1" stroke-width="2"/>
  <path d="{path}" fill="none" stroke="#2563eb" stroke-width="3"/>
  <line x1="{X0}" y1="{Y0}" x2="{mapx(theta0):.1f}" y2="{Y0}" stroke="#dc2626" stroke-width="3"/>
  <circle cx="{mapx(theta0):.1f}" cy="{Y0}" r="5.5" fill="#dc2626"/>
  <text x="{mapx(theta0)+10:.1f}" y="{Y0-14}" font-size="13" fill="#dc2626">θ₀ = arctan μ ≈ {theta0:.0f}°</text>
  <text x="{X0-70}" y="{Y1-34}" font-size="13" fill="#64748b">a / (m·s⁻²)</text>
  <text x="{X1+8}" y="{Y0+20}" font-size="13" fill="#64748b">θ / °</text>
  <text x="{X0-56}" y="{mapy(9.8)+4:.1f}" font-size="12.5" fill="#94a3b8">9.8</text>
  <text x="{X0-30}" y="{Y0+22}" font-size="12.5" fill="#94a3b8">0</text>
  <text x="{mapx(45)-8:.1f}" y="{Y0+22}" font-size="12.5" fill="#94a3b8">45</text>
  <text x="{mapx(90)-14:.1f}" y="{Y0+22}" font-size="12.5" fill="#94a3b8">90</text>
  <text x="{X0+14}" y="{Y0+44}" font-size="13" fill="#dc2626">静止区：θ &lt; θ₀ 时 a = 0</text>
  <text x="{mapx(46):.1f}" y="{mapy(accel(46))-16:.1f}" font-size="13" fill="#2563eb">越过门槛后趋近 g·sinθ</text>
</svg>"""
shot("incline_curve", page(
  "加速度随倾角的变化：存在一个启动门槛",
  f"取 μ = {mu} 时，倾角小于约 {theta0:.0f}° 物体保持静止；越过门槛后加速度按 g(sinθ − μcosθ) 增长。",
  svg_curve, "由 a = g(sinθ − μcosθ) 计算绘制，g 取 9.8 m/s²", 680), 680)

# ---------- 5. 导数与单调性（三次函数） ----------
def f(x): return x**3 - 3*x
XS, XE = -2.1, 2.1
PX0, PX1, PY0, PY1 = 100, 1010, 380, 80
def px(x): return PX0 + (x - XS) / (XE - XS) * (PX1 - PX0)
def py(y): return (PY0 + PY1) / 2 - y / 3.4 * (PY0 - PY1) / 2
curve = []
x = XS
while x <= XE + 1e-9:
    curve.append(f"{'M' if not curve else 'L'}{px(x):.1f},{py(f(x)):.1f}")
    x += 0.04
d = " ".join(curve)
svg_mono = f"""
<svg width="1120" height="430" viewBox="0 0 1120 430">
  <line x1="{PX0-40}" y1="{py(0):.1f}" x2="{PX1+30}" y2="{py(0):.1f}" stroke="#cbd5e1" stroke-width="2"/>
  <line x1="{px(0):.1f}" y1="{PY1-20}" x2="{px(0):.1f}" y2="{PY0+20}" stroke="#cbd5e1" stroke-width="2"/>
  <path d="{d}" fill="none" stroke="#2563eb" stroke-width="3"/>
  <circle cx="{px(-1):.1f}" cy="{py(f(-1)):.1f}" r="6" fill="#dc2626"/>
  <circle cx="{px(1):.1f}" cy="{py(f(1)):.1f}" r="6" fill="#dc2626"/>
  <text x="{px(-1)-72:.1f}" y="{py(f(-1))-16:.1f}" font-size="13" fill="#dc2626">极大值 f′(−1)=0</text>
  <text x="{px(1)+10:.1f}" y="{py(f(1))+24:.1f}" font-size="13" fill="#dc2626">极小值 f′(1)=0</text>
  <rect x="{px(XS):.1f}" y="392" width="{px(-1)-px(XS):.1f}" height="13" rx="4" fill="#22c55e" opacity=".5"/>
  <rect x="{px(-1):.1f}" y="392" width="{px(1)-px(-1):.1f}" height="13" rx="4" fill="#f59e0b" opacity=".5"/>
  <rect x="{px(1):.1f}" y="392" width="{px(XE)-px(1):.1f}" height="13" rx="4" fill="#22c55e" opacity=".5"/>
  <text x="{px(-2):.1f}" y="424" font-size="13" fill="#15803d">f′ &gt; 0 递增</text>
  <text x="{px(-0.55):.1f}" y="424" font-size="13" fill="#b45309">f′ &lt; 0 递减</text>
  <text x="{px(1.35):.1f}" y="424" font-size="13" fill="#15803d">f′ &gt; 0 递增</text>
</svg>"""
shot("derivative_monotonic", page(
  "导数的符号决定单调区间",
  "以 f(x) = x³ − 3x 为例：f′(x) = 3x² − 3 的符号把定义域切成三段，符号变化点正是极值点。",
  svg_mono, "曲线按 f(x)=x³−3x 逐点采样绘制，区间色带对应 f′(x) 的正负", 670), 670)

# ---------- 6. 极值判定流程 ----------
flow = """
<div class="wrap" style="align-items:stretch">
  <div class="card"><div class="idx">STEP 1</div><h3>确定定义域</h3><p>先写清函数有意义的区间，避免在定义域外讨论单调性。</p></div>
  <div class="arrow">→</div>
  <div class="card"><div class="idx">STEP 2</div><h3>求 f′(x) 并因式分解</h3><p>解 f′(x)=0 得驻点，不可导点同样列入候选。</p></div>
  <div class="arrow">→</div>
  <div class="card"><div class="idx">STEP 3</div><h3>列表判号</h3><p>按候选点分段，逐段判断 f′(x) 的正负号。</p></div>
  <div class="arrow">→</div>
  <div class="card"><div class="idx">STEP 4</div><h3>结论与端点比较</h3><p>左正右负为极大，左负右正为极小；闭区间还要比端点。</p></div>
</div>"""
shot("extremum_flow", page(
  "求极值的四步检查清单",
  "四步法把“凭感觉找极值”变成可复核的流程，每一步都能在卷面上留下痕迹。",
  flow, "依据人教版高中数学选择性必修二「导数在研究函数中的应用」整理", 380), 380)

# ---------- 7. 论证链条 ----------
chain = """
<div class="wrap" style="align-items:stretch">
  <div class="card" style="border-color:#bfdbfe"><div class="idx">主张 CLAIM</div><h3>城市应设低排放区</h3><p>一句话可判断真假的陈述，是全链条的靶心。</p><div class="tag">可证伪</div></div>
  <div class="arrow">←</div>
  <div class="card"><div class="idx">理由 REASON</div><h3>低排放区显著降低 PM2.5</h3><p>支撑主张的一般性前提，需独立成立。</p><div class="tag">需证据</div></div>
  <div class="arrow">←</div>
  <div class="card"><div class="idx">证据 EVIDENCE</div><h3>伦敦 2008 年政策前后监测数据</h3><p>具体、可核查、来源明确的事实材料。</p><div class="tag" style="background:#dcfce7;color:#15803d">已核验</div></div>
  <div class="arrow">←</div>
  <div class="card" style="border-color:#fecaca"><div class="idx">反驳 REBUTTAL</div><h3>污染可能来自区域传输</h3><p>预判最强反对意见并回应，论证才算闭环。</p><div class="tag" style="background:#fee2e2;color:#b91c1c">待回应</div></div>
</div>"""
shot("argument_chain", page(
  "论证链条：从主张倒推到证据",
  "写作时先立主张，再问“凭什么”，一路问到可核查的事实为止；反驳环节决定论证的韧性。",
  chain, "结构参考图尔敏论证模型（Toulmin Model）", 380), 380)

# ---------- 8. 论证强度评估 ----------
def bar(label, val, color):
    return (f'<div class="bar-row"><div class="bar-label">{label}</div>'
            f'<div class="bar-track"><div class="bar-fill" style="width:{val}%;background:{color}"></div></div>'
            f'<div class="bar-val">{val}</div></div>')
shot("argument_score", page(
  "同一主张在两份稿件中的论证强度",
  "把四个环节分别打分：初稿的证据环节薄弱，修订后补入可核验来源，整体得分明显抬升。",
  bar("主张明确度",88,"#2563eb")+bar("理由相关性",82,"#7c3aed")+
  bar("证据可核验性（初稿）",46,"#f59e0b")+bar("证据可核验性（修订）",86,"#059669")+
  bar("反驳回应完整度",74,"#0891b2"),
  "评分口径为本课程量规 C 维度示意，非正式成绩", 440), 440)
print("done")

# ---------- 9. 有氧 vs 无氧 ----------
cmp_cards = """
<div class="wrap">
  <div class="card" style="border-color:#bfdbfe">
    <div class="idx" style="color:#2563eb">有氧呼吸</div>
    <h3>彻底氧化，产物是 CO₂ 和 H₂O</h3>
    <p>场所：细胞质基质 + 线粒体</p>
    <p>产物：6 CO₂、6 H₂O，约 30–32 ATP</p>
    <p>氧的作用：作为电子传递链末端受体</p>
    <div class="tag">产能效率高</div>
  </div>
  <div class="card" style="border-color:#fecaca">
    <div class="idx" style="color:#dc2626">无氧呼吸</div>
    <h3>不彻底分解，产物是有机物</h3>
    <p>场所：仅细胞质基质</p>
    <p>产物：乳酸，或乙醇 + CO₂，净得 2 ATP</p>
    <p>氧的作用：不参与，靠 NAD⁺ 再生维持糖酵解</p>
    <div class="tag" style="background:#fee2e2;color:#b91c1c">产能效率低</div>
  </div>
</div>"""
shot("respiration_compare", page(
  "有氧呼吸与无氧呼吸的分岔点",
  "两条通路共享糖酵解这一段；是否进入线粒体、氧是否作为最终电子受体，决定了产物与能量产出。",
  cmp_cards, "依据高中生物必修一「细胞的能量供应和利用」整理", 420), 420)

# ---------- 10. 三种摩擦状态 ----------
fric = """
<div class="wrap">
  <div class="card"><div class="idx">情形 A</div><h3>tanθ &lt; μ：静止</h3>
    <p>静摩擦力随倾角增大而增大，始终与重力沿坡分量平衡，合力为零，a = 0。</p>
    <div class="tag">f = mg·sinθ</div></div>
  <div class="card" style="border-color:#fde68a"><div class="idx" style="color:#b45309">情形 B</div><h3>tanθ = μ：临界</h3>
    <p>静摩擦达到最大值，物体恰好开始滑动，是判断“会不会滑”的关键分界。</p>
    <div class="tag" style="background:#fef3c7;color:#b45309">f = μN</div></div>
  <div class="card" style="border-color:#bbf7d0"><div class="idx" style="color:#15803d">情形 C</div><h3>tanθ &gt; μ：加速下滑</h3>
    <p>滑动摩擦固定为 μN，重力分量大于摩擦力，产生沿坡向下的加速度。</p>
    <div class="tag" style="background:#dcfce7;color:#15803d">a = g(sinθ − μcosθ)</div></div>
</div>"""
shot("incline_states", page(
  "同一斜面，三种受力状态",
  "把“会不会滑”拆成三种情形分别列式，比笼统讨论摩擦力更不容易出错。",
  fric, "依据牛顿第二定律与滑动摩擦定律整理", 400), 400)

# ---------- 11. 切线逼近 ----------
def f2(x): return x*x/2
def px2(x): return 140 + (x + 0.6) / 4.2 * 860
def py2(y): return 400 - y / 9.0 * 300
curve2 = []
x = -0.55
while x <= 3.5:
    curve2.append(f"{'M' if not curve2 else 'L'}{px2(x):.1f},{py2(f2(x)):.1f}")
    x += 0.05
d2 = " ".join(curve2)
def tangent(x0):
    y0, k = f2(x0), x0
    x1, x2 = x0 - 1.1, x0 + 1.1
    return (f'<line x1="{px2(x1):.1f}" y1="{py2(y0 + k*(x1-x0)):.1f}" '
            f'x2="{px2(x2):.1f}" y2="{py2(y0 + k*(x2-x0)):.1f}" stroke="#dc2626" stroke-width="2.4"/>'
            f'<circle cx="{px2(x0):.1f}" cy="{py2(y0):.1f}" r="5" fill="#dc2626"/>')
svg_tan = f"""
<svg width="1120" height="430" viewBox="0 0 1120 430">
  <line x1="100" y1="{py2(0):.1f}" x2="1030" y2="{py2(0):.1f}" stroke="#cbd5e1" stroke-width="2"/>
  <line x1="{px2(0):.1f}" y1="70" x2="{px2(0):.1f}" y2="410" stroke="#cbd5e1" stroke-width="2"/>
  <path d="{d2}" fill="none" stroke="#2563eb" stroke-width="3"/>
  {tangent(1.0)}
  {tangent(2.6)}
  <text x="{px2(1.0)+8:.1f}" y="{py2(0.5)-14:.1f}" font-size="13" fill="#dc2626">斜率 f′(1) = 1</text>
  <text x="{px2(2.6)-150:.1f}" y="{py2(3.4)-10:.1f}" font-size="13" fill="#dc2626">斜率 f′(2.6) = 2.6</text>
  <text x="{px2(3.1):.1f}" y="{py2(6.2):.1f}" font-size="13" fill="#2563eb">f(x) = x²/2</text>
</svg>"""
shot("tangent_slope", page(
  "导数是切线的斜率：位置越陡，导数值越大",
  "同一条曲线上取两点作切线：x = 1 处切线平缓，x = 2.6 处切线明显更陡，斜率恰好等于该点的导数值。",
  svg_tan, "曲线按 f(x)=x²/2 采样绘制，切线斜率取 f′(x)=x", 670), 670)

# ---------- 12. 论证提纲结构 ----------
outline = """
<div class="wrap" style="align-items:stretch;gap:22px">
  <div class="card" style="flex:0 0 250px;border-color:#bfdbfe;background:#f8fbff">
    <div class="idx">一级 · 总论点</div><h3>低排放区值得推广</h3>
    <p>全文只允许一个中心句，其余段落都为它服务。</p>
  </div>
  <div class="card" style="flex:1">
    <div class="idx">二级 · 分论点一</div><h3>环境效益：污染物浓度下降</h3>
    <p>配 1 条可核验监测数据；说明减排幅度与统计口径。</p>
    <div class="tag">证据型</div>
  </div>
  <div class="card" style="flex:1">
    <div class="idx">二级 · 分论点二</div><h3>健康效益：呼吸系统就诊减少</h3>
    <p>配 1 项队列研究；交代样本与随访时长。</p>
    <div class="tag">证据型</div>
  </div>
  <div class="card" style="flex:1;border-color:#fecaca">
    <div class="idx">二级 · 让步与反驳</div><h3>经济成本可能上升</h3>
    <p>先承认合理之处，再用过渡成本数据回应，避免稻草人。</p>
    <div class="tag" style="background:#fee2e2;color:#b91c1c">反驳型</div>
  </div>
</div>"""
shot("argument_outline", page(
  "知识帖的论证提纲：一总三分",
  "提纲阶段就把“每个分论点配什么证据”写清楚，成文时不会出现有观点无材料的空转段落。",
  outline, "提纲模板来自本课程写作工作单，非固定范式", 400), 400)
print("extra done")
