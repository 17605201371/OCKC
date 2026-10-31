"""构造四份样本的 ProseMirror 文档。所有 block 都带稳定 blockId，方便批注锚定。"""
import uuid

def bid():
    return uuid.uuid4().hex[:16]

def text(t, marks=None):
    n = {"type": "text", "text": t}
    if marks:
        n["marks"] = marks
    return n

def p(*parts, block=None):
    content = []
    for part in parts:
        if isinstance(part, str):
            content.append(text(part))
        elif isinstance(part, dict):
            content.append(part)
        elif isinstance(part, (list, tuple)):
            content.extend(part)
    node = {"type": "paragraph", "attrs": {"blockId": block or bid()}}
    if content:
        node["content"] = content
    return node

def h(level, t, block=None):
    return {"type": "heading", "attrs": {"level": level, "blockId": block or bid()},
            "content": [text(t)]}

def code(src, lang="python", block=None):
    return {"type": "codeBlock",
            "attrs": {"language": lang, "blockId": block or bid()},
            "content": [text(src)]}

def math(latex, block=None):
    return {"type": "mathBlock", "attrs": {"latex": latex, "blockId": block or bid()}}

def im(latex):
    return {"type": "inlineMath", "attrs": {"latex": latex}}

def figure(src, alt, caption, source="", block=None):
    return {"type": "figure",
            "attrs": {"src": src, "alt": alt, "caption": caption,
                      "source": source, "blockId": block or bid()}}

def cell(t, header=False, block=None):
    return {"type": "tableHeader" if header else "tableCell",
            "attrs": {"colspan": 1, "rowspan": 1, "colwidth": None},
            "content": [p(t, block=block)]}

def table(rows, block=None):
    body = []
    for r, row in enumerate(rows):
        body.append({"type": "tableRow",
                     "content": [cell(v, header=(r == 0)) for v in row]})
    node = {"type": "table", "attrs": {"blockId": block or bid()}, "content": body}
    return node

def callout(*nodes):
    return {"type": "callout", "content": list(nodes)}

def bullets(*items):
    return {"type": "bulletList",
            "content": [{"type": "listItem", "content": [p(i)]} for i in items]}

def ordered(*items):
    return {"type": "orderedList", "attrs": {"start": 1, "type": None},
            "content": [{"type": "listItem", "content": [p(i)]} for i in items]}

def doc(*nodes):
    return {"type": "doc", "content": list(nodes)}


# ----------------------------------------------------------------------------
# 知识帖 1：二分查找
# ----------------------------------------------------------------------------
def post_binary(img):
    anchor_a = bid()
    anchor_b = bid()
    body = [
        h(2, "一、先看一次真实的查找"),
        p("在有序数组 ", im("A=[1,3,5,7,9,11,13,15]"), " 里找 ", im("9"),
          "，二分只需要三轮。每一轮都把 ", im("mid"), " 指向的元素与目标比较，"
          "然后丢弃不可能包含答案的一半。", block=anchor_a),
        figure(img, "二分查找的区间收缩过程",
               "图 1　第一轮 mid=3（值 7）偏小，左半被丢弃；第二轮 mid=5（值 11）偏大，右半被丢弃",
               "本文自绘"),
        p("这个过程之所以成立，是因为每一轮都维持同一个不变式：如果答案存在，"
          "它一定落在闭区间 ", im("[lo, hi]"), " 内。写法不同，只是对这个区间的解释不同。"),
        h(2, "二、三种写法的边界对比"),
        p("下面三种写法都能查对，但边界更新和返回值的含义完全不一样。混用是二分最容易出错的地方。"),
        table([
            ["写法", "循环条件", "区间含义", "典型错误"],
            ["while (lo <= hi)", "lo <= hi", "闭区间 [lo, hi]", "更新写成 hi = mid 会死循环"],
            ["while (lo < hi)", "lo < hi", "左闭右开 [lo, hi)", "返回 lo 却当成 lo-1 用"],
            ["递归", "lo > hi 终止", "闭区间 [lo, hi]", "栈深度 O(log n)，大数组需改迭代"],
        ]),
        p("我自己的判据只有一条：先确定区间是闭还是半开，再决定 ", im("hi"),
          " 的初值和更新方式，最后检查循环结束时 ", im("lo"), " 到底指向什么。"),
        h(2, "三、比较次数的上界"),
        p("设 ", im("T(n)"), " 为长度 ", im("n"), " 的区间所需比较次数，每轮砍半："),
        math(r"T(n) = T\left(\left\lfloor \frac{n}{2} \right\rfloor\right) + 1,"
             r"\qquad T(1)=1 \;\Longrightarrow\; T(n)=\left\lceil \log_2(n+1)\right\rceil"),
        p("所以 100 万条有序数据最多比较 20 次；即使线性扫描要 50 万次，差距是五个数量级。"),
        h(2, "四、可以直接用的 Python 实现"),
        p("下面这个版本求的是下界（第一个不小于 target 的位置），也是 ", im("bisect_left"),
          " 的等价实现。"),
        code(
            "def lower_bound(nums, target):\n"
            "    \"\"\"返回第一个 >= target 的下标；全部小于 target 时返回 len(nums)。\"\"\"\n"
            "    lo, hi = 0, len(nums)      # 左闭右开 [lo, hi)\n"
            "    while lo < hi:\n"
            "        mid = lo + (hi - lo) // 2   # 避免 lo + hi 溢出\n"
            "        if nums[mid] < target:\n"
            "            lo = mid + 1       # mid 不可能是答案\n"
            "        else:\n"
            "            hi = mid           # mid 可能是答案，保留\n"
            "    return lo\n"
            "\n"
            "assert lower_bound([1, 3, 5, 7, 9, 11], 9) == 4\n"
            "assert lower_bound([1, 3, 5, 7, 9, 11], 4) == 2   # 插在 3 和 5 之间\n"
            "assert lower_bound([1, 3, 5, 7, 9, 11], 99) == 6  # 全部小于\n",
            "python"),
        p("注意 ", im("mid = lo + (hi - lo) // 2"),
          " 这一行：在 Java 里 ", im("(lo + hi) / 2"), " 在 lo 和 hi 都接近 ",
          im("2^{31}-1"), " 时会溢出成负数，Python 没有这个问题，但换语言时容易照抄出错。"),
        callout(
            p("三个最常见的坑：① 循环条件与区间含义不匹配；② 更新边界时把 mid 写进了仍然可能的区间；"
              "③ 循环结束后 lo 的含义没想清楚，直接拿去做下标。"),
        ),
        h(2, "五、小结"),
        p("二分的难点不在思路，而在边界。把区间定义写进注释、把返回值含义写进函数名，"
          "比背模板更可靠。", block=anchor_b),
    ]
    return doc(*body), [
        {"paragraphId": anchor_a,
         "sourceTitle": "Python 官方文档：bisect —— 数组二分查找算法",
         "sourceUrl": "https://docs.python.org/3/library/bisect.html",
         "excerpt": "bisect_left(a, x) 返回插入点，使 a 中位于该点之前的元素都小于 x。",
         "claim": "二分查找的返回值是插入点（下界），不是“是否找到”的布尔值。"},
        {"paragraphId": anchor_b,
         "sourceTitle": "CPython 源码 Lib/bisect.py",
         "sourceUrl": "https://github.com/python/cpython/blob/main/Lib/bisect.py",
         "excerpt": "while lo < hi: mid = (lo + hi) // 2 ... 循环结束返回 lo。",
         "claim": "标准库实现使用左闭右开区间，循环结束时 lo 即插入点。"},
    ]


# ----------------------------------------------------------------------------
# 知识帖 2：自由落体与空气阻力
# ----------------------------------------------------------------------------
def post_drag(img):
    a1 = bid()
    a2 = bid()
    body = [
        h(2, "一、教科书结论的适用条件"),
        p("“自由落体加速度与质量无关”这句话成立的前提是真空。真实空气里，"
          "阻力随速度平方增长，重物与轻物的轨迹会明显分开。", block=a1),
        p("把空气阻力写成 ", im("f = k v^2"), "，取向下为正，运动方程是"),
        math(r"m\frac{\mathrm{d}v}{\mathrm{d}t} = mg - k v^2, \qquad v(0)=0"),
        p("令收尾速度 ", im(r"v_t=\sqrt{mg/k}"), "，方程有解析解"),
        math(r"v(t)=v_t \tanh\!\left(\frac{g t}{v_t}\right),"
             r"\qquad y(t)=\frac{v_t^2}{g}\ln\!\cosh\!\left(\frac{g t}{v_t}\right)"),
        figure(img, "有阻力与无阻力时的速度曲线",
               "图 1　同一高度下落：无阻力时速度线性增长，有阻力时速度趋于收尾速度",
               "本文用 Matplotlib 绘制"),
        h(2, "二、三组参数的数值结果"),
        p("取 ", im("g=9.8\\ \\mathrm{m/s^2}"), "，从 10 m 高处静止释放，计算如下："),
        table([
            ["质量 m", "阻力系数 k", "收尾速度 v_t", "2 s 时速度", "落地用时"],
            ["0.05 kg", "0.0045", "10.4 m/s", "9.5 m/s", "1.52 s"],
            ["0.20 kg", "0.0045", "20.9 m/s", "17.9 m/s", "1.46 s"],
            ["0.20 kg", "0.0200", "9.9 m/s", "9.1 m/s", "1.53 s"],
        ]),
        p("可以看到：质量翻四倍，落地时间只从 1.52 s 缩到 1.46 s；"
          "而把阻力系数调大四倍，效果几乎等价于把质量减到四分之一——"
          "真正决定差异的是 ", im("m/k"), " 这个比值，不是质量本身。"),
        h(2, "三、用 RK4 验证解析解"),
        p("数值积分可以同时处理变阻力和变密度，这里先用常系数验证实现是否正确。"),
        code(
            "import numpy as np\n"
            "\n"
            "def fall(m=0.2, k=0.0045, g=9.8, h=1e-4, t_end=2.0):\n"
            "    \"\"\"RK4 积分 dv/dt = g - (k/m) v^2，返回时间与速度序列。\"\"\"\n"
            "    def acc(v):\n"
            "        return g - (k / m) * v * v\n"
            "\n"
            "    n = int(t_end / h)\n"
            "    t = np.linspace(0, t_end, n + 1)\n"
            "    v = np.zeros(n + 1)\n"
            "    for i in range(n):\n"
            "        k1 = acc(v[i])\n"
            "        k2 = acc(v[i] + h * k1 / 2)\n"
            "        k3 = acc(v[i] + h * k2 / 2)\n"
            "        k4 = acc(v[i] + h * k3)\n"
            "        v[i + 1] = v[i] + h * (k1 + 2 * k2 + 2 * k3 + k4) / 6\n"
            "    return t, v\n"
            "\n"
            "t, v = fall()\n"
            "vt = np.sqrt(0.2 * 9.8 / 0.0045)\n"
            "print(abs(v[-1] - vt * np.tanh(9.8 * t[-1] / vt)))  # 与解析解的误差 < 1e-6\n",
            "python"),
        h(2, "四、结论"),
        p("自由落体“与质量无关”只在真空成立。有空气时，质量通过 ", im("v_t=\\sqrt{mg/k}"),
          " 进入方程，和质量一起起作用的还有形状（体现在 ", im("k"), " 上）。"
          "把结论的适用条件一起写出来，比记住结论本身更有用。", block=a2),
    ]
    return doc(*body), [
        {"paragraphId": a1,
         "sourceTitle": "Feynman Lectures, Vol. I, Ch. 8: Motion",
         "sourceUrl": "https://www.feynmanlectures.caltech.edu/I_08.html",
         "excerpt": "在空气中下落的物体受到与速度平方成正比的阻力，最终趋于收尾速度。",
         "claim": "空气阻力使自由落体偏离“加速度与质量无关”的结论。"},
        {"paragraphId": a2,
         "sourceTitle": "NASA Glenn：Terminal Velocity",
         "sourceUrl": "https://www.grc.nasa.gov/www/k-12/airplane/termv.html",
         "excerpt": "Terminal velocity depends on mass, drag coefficient, and air density.",
         "claim": "收尾速度由质量与阻力系数共同决定。"},
    ]


# ----------------------------------------------------------------------------
# 步骤题 1：爬楼梯（动态规划）
# ----------------------------------------------------------------------------
def step_climb(img):
    s1 = [
        p("设 ", im("f(n)"), " 为爬到第 ", im("n"), " 级台阶的方法数。最后一步只能跨 1 级或 2 级，所以"),
        math(r"f(n)=f(n-1)+f(n-2),\qquad f(1)=1,\ f(2)=2"),
        p("这是一个二阶线性递推，边界条件写错一位，整个结果都会错。"),
    ]
    s2 = [
        p("先按定义直译，正确性最容易验证："),
        code(
            "def climb(n):\n"
            "    if n <= 2:\n"
            "        return n\n"
            "    return climb(n - 1) + climb(n - 2)\n",
            "python"),
        figure(img, "爬楼梯的递归展开",
               "图 1　f(5) 的递归树：f(3) 被算 2 次，f(2) 被算 3 次",
               "本文自绘"),
        p("问题很明显：同一个子问题被反复求解，时间复杂度是 ", im("O(2^n)"), "。"),
    ]
    s3 = [
        p("用一个数组把算过的答案存下来，每个状态只算一次："),
        code(
            "def climb_memo(n, memo=None):\n"
            "    if memo is None:\n"
            "        memo = {}\n"
            "    if n <= 2:\n"
            "        return n\n"
            "    if n in memo:\n"
            "        return memo[n]\n"
            "    memo[n] = climb_memo(n - 1, memo) + climb_memo(n - 2, memo)\n"
            "    return memo[n]\n",
            "python"),
        table([
            ["实现", "时间", "空间", "n=40 耗时"],
            ["朴素递归", "O(2^n)", "O(n) 栈", "约 32 s"],
            ["记忆化", "O(n)", "O(n)", "约 0.02 ms"],
            ["滚动数组", "O(n)", "O(1)", "约 0.01 ms"],
        ]),
        p("表格里的耗时是在同一台机器上跑 ", im("n=40"), " 得到的，量级差异比具体数字更重要。"),
    ]
    s4 = [
        p("递推只依赖前两项，没必要存整张表："),
        code(
            "def climb(n):\n"
            "    if n <= 2:\n"
            "        return n\n"
            "    a, b = 1, 2          # f(1), f(2)\n"
            "    for _ in range(3, n + 1):\n"
            "        a, b = b, a + b\n"
            "    return b\n"
            "\n"
            "assert climb(5) == 8\n"
            "assert climb(40) == 165580141\n",
            "python"),
        p("空间从 ", im("O(n)"), " 降到 ", im("O(1)"),
          "。如果题目要求对 ", im("10^9+7"), " 取模，把加法改成 ", im("(a + b) % MOD"), " 即可。"),
    ]
    nodes = []
    for title, body in [
        ("第一步：定义状态与转移方程", s1),
        ("第二步：写出递归，先让它跑对", s2),
        ("第三步：记忆化，去掉重复计算", s3),
        ("第四步：滚动数组，把空间压到 O(1)", s4),
    ]:
        nodes.append(h(2, title))
        nodes.extend(body)
    return doc(*nodes), [
        {"paragraphId": s1[0]["attrs"]["blockId"],
         "sourceTitle": "LeetCode 70. Climbing Stairs",
         "sourceUrl": "https://leetcode.cn/problems/climbing-stairs/",
         "excerpt": "每次可以爬 1 或 2 个台阶，有多少种不同的方法可以爬到楼顶。",
         "claim": "状态转移来自“最后一步跨 1 级还是 2 级”的分类讨论。"},
    ]


# ----------------------------------------------------------------------------
# 步骤题 2：导数与单调性
# ----------------------------------------------------------------------------
def step_mono(img):
    s1 = [
        p("以 ", im("f(x)=x^3-3x"), " 为例。先求导并因式分解，符号才好判断："),
        math(r"f'(x)=3x^2-3=3(x-1)(x+1)"),
        p("导数是二次函数，符号只可能在两个零点处改变。"),
    ]
    s2 = [
        p("令 ", im("f'(x)=0"), " 得驻点 ", im("x=-1"), " 与 ", im("x=1"),
          "。本题定义域是全体实数，没有不可导点。"),
        table([
            ["关键点", "x 值", "f(x)", "类型"],
            ["驻点 1", "-1", "2", "极大值"],
            ["驻点 2", "1", "-2", "极小值"],
        ]),
    ]
    s3 = [
        p("把定义域按驻点切开，逐段判断 ", im("f'(x)"), " 的符号："),
        table([
            ["区间", "取点", "f'(x) 符号", "单调性"],
            ["(-∞, -1)", "-2", "正", "递增"],
            ["(-1, 1)", "0", "负", "递减"],
            ["(1, +∞)", "2", "正", "递增"],
        ]),
        p("符号表比直接画图更可靠：它不依赖对函数形状的直觉。"),
    ]
    s4 = [
        figure(img, "函数图像与单调区间",
               "图 1　x³−3x 的增减区间：(-∞,-1) 增，(-1,1) 减，(1,+∞) 增",
               "本文自绘"),
        p("所以单调递增区间是 ", im("(-\\infty,-1)"), " 和 ", im("(1,+\\infty)"),
          "，单调递减区间是 ", im("(-1,1)"), "。"),
        p("注意区间端点写开区间：在驻点处导数为零，不满足“导数恒正”，"
          "写闭区间在严格单调的定义下是不严谨的。"),
    ]
    nodes = []
    for title, body in [
        ("第一步：求导并因式分解", s1),
        ("第二步：找驻点与不可导点", s2),
        ("第三步：列表判断导数符号", s3),
        ("第四步：写出单调区间", s4),
    ]:
        nodes.append(h(2, title))
        nodes.extend(body)
    return doc(*nodes), [
        {"paragraphId": s1[0]["attrs"]["blockId"],
         "sourceTitle": "同济大学《高等数学》第七版 上册，§3.4 函数的单调性与曲线的凹凸性",
         "sourceUrl": "https://book.douban.com/subject/26847936/",
         "excerpt": "设函数 f 在 [a,b] 上连续，在 (a,b) 内可导，若 f'(x)>0，则 f 在 [a,b] 上单调增加。",
         "claim": "导数符号与单调性的对应关系是判定单调区间的依据。"},
    ]
