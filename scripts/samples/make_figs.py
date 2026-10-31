import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, Rectangle, FancyBboxPatch
import numpy as np

plt.rcParams["font.sans-serif"] = ["Heiti SC", "Arial Unicode MS", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False

INK = "#2c2a26"
MUTED = "#8a8578"
ACCENT = "#b8873b"
ACCENT_SOFT = "#f0e2c9"
BLUE = "#2f6f62"
BLUE_SOFT = "#d9e8e2"
RED = "#a8452f"
BG = "#fbfaf7"
OUT = os.environ.get("OCKC_ASSETS", "/tmp/ockc_assets")
os.makedirs(OUT, exist_ok=True)

def frame(w=12, h=5):
    fig, ax = plt.subplots(figsize=(w, h), dpi=160)
    fig.patch.set_facecolor(BG)
    ax.set_facecolor(BG)
    ax.set_axis_off()
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    return fig, ax

def fig_binary():
    fig, ax = frame(12, 5.6)
    arr = [1, 3, 5, 7, 9, 11, 13, 15]
    cell_w, cell_h = 9.2, 11
    left0 = 6
    rows = [
        (76, 0, 3, 7, "第一轮：mid = (0+7)//2 = 3，A[3]=7 < 9，丢掉左半"),
        (34, 4, 5, 7, "第二轮：mid = (4+7)//2 = 5，A[5]=11 > 9，丢掉右半"),
    ]
    for y, lo, mid, hi, label in rows:
        ax.text(left0, y + 19, label, fontsize=13.5, color=INK, va="bottom")
        for i, v in enumerate(arr):
            x = left0 + i * (cell_w + 0.8)
            inside = lo <= i <= hi
            face = ACCENT_SOFT if inside else "#f2f0ea"
            edge = ACCENT if inside else "#ddd8cd"
            if i == mid:
                face, edge = ACCENT, ACCENT
            ax.add_patch(Rectangle((x, y), cell_w, cell_h, facecolor=face,
                                   edgecolor=edge, linewidth=1.8, zorder=2))
            ax.text(x + cell_w / 2, y + cell_h / 2, str(v), ha="center", va="center",
                    fontsize=17, color="white" if i == mid else INK,
                    fontweight="bold" if i == mid else "normal", zorder=3)
            ax.text(x + cell_w / 2, y - 4.5, str(i), ha="center", va="center",
                    fontsize=11.5, color=MUTED, zorder=3)
            if i == lo:
                ax.text(x + cell_w / 2, y - 10.5, "lo", ha="center", fontsize=12.5,
                        color=BLUE, fontweight="bold")
            if i == hi:
                ax.text(x + cell_w / 2, y - 10.5, "hi", ha="center", fontsize=12.5,
                        color=RED, fontweight="bold")
            if i == mid:
                ax.text(x + cell_w / 2, y + cell_h + 2, "mid", ha="center",
                        fontsize=12, color=ACCENT, fontweight="bold")
    ax.text(left0, 12, "目标 target = 9：第三轮 lo=hi=4，返回下标 4（值 9）",
            fontsize=14, color=BLUE, fontweight="bold")
    ax.text(left0, 3, "每一轮把候选区间砍半，最多比较 log2(n+1) 次（向上取整）",
            fontsize=12.5, color=MUTED)
    fig.savefig(OUT + "/binary_search.png", bbox_inches="tight",
                facecolor=BG, pad_inches=0.25)
    plt.close(fig)

def fig_vt():
    fig, ax = plt.subplots(figsize=(11, 5.2), dpi=160)
    fig.patch.set_facecolor(BG)
    ax.set_facecolor(BG)
    t = np.linspace(0, 6, 400)
    g, k, m = 9.8, 0.0045, 0.2
    vt = np.sqrt(m * g / k)
    ax.plot(t, g * t, color=ACCENT, linewidth=2.6, label="无空气阻力 v = gt")
    ax.plot(t, vt * np.tanh(g * t / vt), color=BLUE, linewidth=2.6,
            label="有空气阻力（k=0.0045, m=0.2 kg）")
    ax.axhline(vt, color=BLUE, linestyle="--", linewidth=1.3, alpha=0.7)
    ax.text(3.9, vt + 3.2, f"收尾速度 vt ≈ {vt:.1f} m/s", color=BLUE, fontsize=12.5)
    ax.set_xlabel("时间 t / s", fontsize=13, color=INK)
    ax.set_ylabel("速度 v / (m/s)", fontsize=13, color=INK)
    ax.tick_params(labelsize=11.5, colors=MUTED)
    ax.grid(True, linestyle=":", color="#ddd8cd", alpha=0.9)
    for spine in ("top", "right"):
        ax.spines[spine].set_visible(False)
    for spine in ("left", "bottom"):
        ax.spines[spine].set_color("#d8d2c6")
    ax.legend(fontsize=12.5, frameon=False, loc="upper left")
    ax.set_title("同一高度下落：速度随时间的变化", fontsize=15, color=INK, pad=14)
    fig.savefig(OUT + "/drag_vt.png", bbox_inches="tight",
                facecolor=BG, pad_inches=0.25)
    plt.close(fig)

def fig_tree():
    fig, ax = frame(12, 6)
    pos = {
        "f(5)": (50, 82),
        "f(4)": (28, 64), "f(3)a": (74, 64),
        "f(3)b": (15, 45), "f(2)a": (40, 45),
        "f(2)b": (63, 45), "f(1)a": (87, 45),
        "f(1)b": (9, 26), "f(1)c": (30, 26),
    }
    edges = [("f(5)", "f(4)"), ("f(5)", "f(3)a"),
             ("f(4)", "f(3)b"), ("f(4)", "f(2)a"),
             ("f(3)a", "f(2)b"), ("f(3)a", "f(1)a"),
             ("f(3)b", "f(1)b"), ("f(2)a", "f(1)c")]
    for a, b in edges:
        ax.add_patch(FancyArrowPatch(pos[a], pos[b], arrowstyle="-", linewidth=1.6,
                                     color="#cfc8ba", shrinkA=15, shrinkB=15, zorder=1))
    dup = {"f(3)a", "f(3)b", "f(2)a", "f(2)b"}
    for name, (x, y) in pos.items():
        face = "#f6e3e0" if name in dup else ACCENT_SOFT
        edge = RED if name in dup else ACCENT
        label = name.replace("a", "").replace("b", "").replace("c", "")
        ax.add_patch(FancyBboxPatch((x - 7, y - 4.6), 14, 9.2,
                                    boxstyle="round,pad=0.6,rounding_size=2.2",
                                    facecolor=face, edgecolor=edge, linewidth=1.7, zorder=2))
        ax.text(x, y, label, ha="center", va="center", fontsize=13.5, color=INK, zorder=3)
    ax.text(4, 95, "爬楼梯 f(n)=f(n-1)+f(n-2)：同一子问题被反复展开", fontsize=14.5,
            color=INK, fontweight="bold")
    ax.text(4, 7, "红色节点重复出现：f(3) 算了 2 次、f(2) 算了 3 次，时间复杂度退化为 O(2ⁿ)",
            fontsize=12.5, color=RED)
    fig.savefig(OUT + "/dp_tree.png", bbox_inches="tight",
                facecolor=BG, pad_inches=0.25)
    plt.close(fig)

def fig_mono():
    fig, ax = plt.subplots(figsize=(11, 5), dpi=160)
    fig.patch.set_facecolor(BG)
    ax.set_facecolor(BG)
    x = np.linspace(-2.6, 2.6, 500)
    ax.plot(x, x**3 - 3*x, color=INK, linewidth=2.6)
    ax.axhline(0, color="#cfc8ba", linewidth=1)
    ax.axvline(0, color="#cfc8ba", linewidth=1)
    for x0, label, color, offset in ((-1, "x = -1 极大值", ACCENT, (12, 16)),
                                     (1, "x = 1 极小值", BLUE, (14, -24))):
        ax.plot([x0], [x0**3 - 3*x0], "o", markersize=9, color=color, zorder=5)
        ax.annotate(label, (x0, x0**3 - 3*x0), textcoords="offset points",
                    xytext=offset, fontsize=12.5, color=color)
    ax.axvspan(-2.6, -1, color=ACCENT_SOFT, alpha=0.55, zorder=0)
    ax.axvspan(1, 2.6, color=ACCENT_SOFT, alpha=0.55, zorder=0)
    ax.axvspan(-1, 1, color=BLUE_SOFT, alpha=0.6, zorder=0)
    ax.text(-2.05, 5.6, "f′ > 0 递增", fontsize=12.5, color=ACCENT)
    ax.text(1.35, 5.6, "f′ > 0 递增", fontsize=12.5, color=ACCENT)
    ax.text(-0.44, -6.6, "f′ < 0 递减", fontsize=12.5, color=BLUE)
    ax.set_xlabel("x", fontsize=13, color=INK)
    ax.set_ylabel("f(x) = x³ − 3x", fontsize=13, color=INK)
    ax.tick_params(labelsize=11.5, colors=MUTED)
    for spine in ("top", "right"):
        ax.spines[spine].set_visible(False)
    for spine in ("left", "bottom"):
        ax.spines[spine].set_color("#d8d2c6")
    ax.set_title("导数符号决定升降：x³ − 3x 的单调区间", fontsize=15, color=INK, pad=12)
    fig.savefig(OUT + "/monotonic.png", bbox_inches="tight",
                facecolor=BG, pad_inches=0.25)
    plt.close(fig)

fig_binary(); fig_vt(); fig_tree(); fig_mono()
print("ok")
