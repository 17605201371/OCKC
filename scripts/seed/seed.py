#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""构造多组知识帖作业的完整批改流程数据。"""
import sys, time, uuid, pathlib, datetime
sys.path.insert(0, "/tmp/ockc_seed")
from seed_lib import (Client, build_doc, h2, para, quote, callout, mathblock,
                      bullets, ev, items, log, BASE)

CID = 1
STU = "student@example.test"
PEER = "peer@example.test"

ASSIGNMENTS = [
    dict(key="A1", title="细胞呼吸的能量账本：用数据说明能量去向",
         req="写一篇面向高一学生的知识帖，解释细胞呼吸各阶段的场所、反应与能量产出，并回答"
             "「为什么线粒体被称为细胞的能量车间」。要求：至少 1 张自绘图示、至少 2 条可核验证据，"
             "正文需区分事实与推断。",
         days=21),
    dict(key="A2", title="斜面运动与受力分析：从示意图到加速度表达式",
         req="选取一个斜面上滑动的物体，完整展示受力分析过程：画出受力图、写出分量式、给出加速度表达式，"
             "并讨论不同摩擦状态下的运动差异。要求图示清晰、公式用公式块呈现。",
         days=18),
    dict(key="A3", title="导数与函数单调性：把符号判断讲成方法",
         req="写一篇讲清「导数符号如何决定单调区间」的知识帖，需包含一个完整例题、一张函数图像说明，"
             "并指出常见误区（如极值点与驻点的关系）。",
         days=24),
    dict(key="A4", title="论证链条拆解：让每一句话都有出处",
         req="任选一个公共议题，写一篇论证结构清晰的知识帖：明确主张、给出分论点、为每个分论点配置"
             "可核验证据，并正面回应一个最强反驳。要求配论证结构图。",
         days=28),
]

POSTS = []

# ================= A1 细胞呼吸 =================
POSTS.append(dict(
    key="P1", assign="A1", author=STU,
    title="线粒体为什么被称为能量车间？用一张账本算清 ATP",
    blocks=[
        h2("p1-h", "先给结论"),
        para("p1-1", "细胞呼吸的产能重心不在糖酵解，而在线粒体内膜上的氧化磷酸化。把 1 分子葡萄糖的"
                     "三段产出记在同一张账本上，差别就非常直观：糖酵解与三羧酸循环各自只净得 2 个 ATP，"
                     "其余 26–28 个来自电子传递链。", "结论："),
        ("FIG", "p1-f1", "respiration_stages", "细胞呼吸三阶段对照图",
         "图 1　三个阶段的发生场所、反应与产物", "作者依据《生物化学》糖的有氧氧化通路自绘"),
        para("p1-2", "课堂上最容易混淆的是「场所」与「产物」的对应关系。糖酵解在细胞质基质完成，不需要氧；"
                     "三羧酸循环把丙酮酸彻底拆成 CO₂，同时把能量交给 NADH 与 FADH₂；真正消耗氧的环节是"
                     "内膜上的电子传递，氧在这里充当末端电子受体。"),
        ("FIG", "p1-f2", "respiration_atp", "ATP 产出分布条形图",
         "图 2　三段产出放在同一尺度上，氧化磷酸化约占 87%", "数据为教材常用估算区间"),
        para("p1-3", "因此「无氧条件下细胞不能产能」是错误说法。无氧时糖酵解仍能净得 2 个 ATP，"
                     "只是产物停在乳酸或乙醇，绝大部分能量仍锁在有机物分子中。这也是剧烈运动时"
                     "能量供应会明显吃紧的原因。"),
        callout("p1-c1", "自查提示：如果一段解释里没有同时出现「场所—反应—产物」三要素，"
                         "通常说明它只是复述结论，没有解释机制。"),
        para("p1-4", "把账本写成表格比背结论更稳：每一段都追问三件事——在哪里发生、把什么变成什么、净得多少 ATP。"
                     "三问答全，机制才算讲清。"),
    ],
    evidence=[
        ev("p1-1", "氧化磷酸化（维基百科）", "https://zh.wikipedia.org/wiki/氧化磷酸化",
           "1 分子葡萄糖彻底氧化净生成约 30–32 分子 ATP，其中绝大部分由氧化磷酸化产生。",
           "产能重心位于线粒体内膜的氧化磷酸化。"),
        ev("p1-2", "三羧酸循环（维基百科）", "https://zh.wikipedia.org/wiki/三羧酸循环",
           "循环每转一圈生成 3 个 NADH、1 个 FADH₂ 和 1 个 GTP（相当于 ATP）。",
           "循环本身 ATP 产出很少，主要输出还原当量。"),
        ev("p1-3", "糖酵解（维基百科）", "https://zh.wikipedia.org/wiki/糖酵解",
           "糖酵解在细胞质基质中进行，净生成 2 个 ATP 和 2 个 NADH，全过程不需要氧参与。",
           "无氧条件下仍可净产 2 个 ATP。"),
    ],
    scores=dict(S1=78,S2=74,S3=70,A1=80,A2=72,A3=68,C1=76,C2=62,L1=80,L2=74,L3=70),
    summary="机制主线正确，三阶段对照清晰；证据环节偏弱，能量去向的表述缺少定量支撑。",
    feedbacks=[("p1-3","C2","「绝大部分能量仍锁在有机物分子中」这句是重要判断，但没有任何数据或来源。"
                            "建议补一句乳酸发酵的能量去向，或明确标注为推断。"),
               ("p1-2","A2","三羧酸循环产物写成 CO₂ 是对的，但没有交代丙酮酸如何进入线粒体，"
                            "读者会卡在这个转场上。")],
    v2=dict(change=("p1-3", "因此「无氧条件下细胞不能产能」是错误说法。无氧时糖酵解仍能净得 2 个 ATP，"
                            "但葡萄糖中约 95% 以上的化学能仍保留在乳酸或乙醇中，未被释放。"
                            "这也是剧烈运动时能量供应明显吃紧的原因：总量够用，释放效率却只有有氧状态的十分之一左右。"),
             add=ev("p1-3", "乳酸发酵的能量转化效率", "https://zh.wikipedia.org/wiki/乳酸发酵",
                    "乳酸发酵每分子葡萄糖仅产生 2 个 ATP，其余能量保留在乳酸分子中。",
                    "无氧条件下能量释放效率显著低于有氧呼吸。")),
    scores2=dict(S1=86,S2=84,S3=80,A1=88,A2=84,A3=80,C1=86,C2=78,L1=88,L2=84,L3=80),
    summary2="二稿补齐了能量去向的定量表述，并补入乳酸发酵证据；证据链完整，可进入课程社区展示。",
    pub="APPROVE",
    pub_reason="二稿证据完整、图示清晰，同意在课程社区展示，供同班同学参考其账本式写法。",
))

POSTS.append(dict(
    key="P2", assign="A1", author=PEER,
    title="剧烈运动后肌肉酸痛，是无氧呼吸造成的吗？",
    blocks=[
        h2("p2-h", "结论先行"),
        para("p2-1", "肌肉酸痛与无氧呼吸有关，但把两者直接画等号并不准确。无氧呼吸产生的是乳酸，"
                     "而延迟性肌肉酸痛（DOMS）主要与肌纤维微损伤后的炎症反应有关，乳酸通常在运动后"
                     "一到两小时内即被清除。", "结论："),
        ("FIG", "p2-f1", "respiration_compare", "有氧与无氧呼吸对比图",
         "图 1　两条通路在「是否进入线粒体」处分岔", "依据高中生物必修一相关内容整理"),
        para("p2-2", "无氧呼吸的价值不在于产能多，而在于快。它不依赖线粒体和氧，能在短时间内"
                     "把 NAD⁺ 再生出来，让糖酵解继续运转，为冲刺、举重这类短时高强度动作供能。"),
        ("FIG", "p2-f2", "respiration_atp", "ATP 产出对比图",
         "图 2　无氧通路净得 2 个 ATP，与有氧相差一个数量级", "数据为教材常用估算区间"),
        quote("p2-3", "如果无氧呼吸的产物直接导致酸痛，那么酸痛应该在运动结束后立刻最强；"
                      "但 DOMS 通常在 24–72 小时达到高峰，时间对不上。"),
        para("p2-4", "所以更严谨的说法是：无氧呼吸解释了「为什么能撑过那几秒」，"
                     "而酸痛解释要靠肌肉微损伤与炎症。两者相关，但因果链不同。"),
    ],
    evidence=[
        ev("p2-1", "延迟性肌肉酸痛（DOMS）", "https://zh.wikipedia.org/wiki/延迟性肌肉酸痛",
           "DOMS 通常在运动后 24–72 小时达到高峰，与肌纤维微损伤及随后的炎症反应相关。",
           "酸痛的时间特征与乳酸清除时间不吻合。"),
        ev("p2-2", "糖酵解（维基百科）", "https://zh.wikipedia.org/wiki/糖酵解",
           "糖酵解通过再生 NAD⁺ 维持自身运转，不依赖氧。",
           "无氧通路的核心价值是快速再生 NAD⁺。"),
        ev("p2-3", "乳酸（维基百科）", "https://zh.wikipedia.org/wiki/乳酸",
           "运动后血乳酸一般在 1–2 小时内恢复到静息水平。",
           "乳酸并非酸痛持续数天的主因。"),
    ],
    scores=dict(S1=84,S2=80,S3=76,A1=82,A2=78,A3=74,C1=80,C2=70,L1=82,L2=80,L3=76),
    summary="选题有辨析价值，用时间特征作反证是亮点；对无氧呼吸功能的解释略浅。",
    feedbacks=[("p2-2","S2","「价值不在于产能多，而在于快」这个概括很好，但没说明快在哪里。"
                            "补上 NAD⁺ 再生的循环关系会更有说服力。"),
               ("p2-3","C1","引用块里的推理是对的，但缺少来源支撑，建议把血乳酸恢复时间挂到证据上。")],
    v2=dict(change=("p2-2", "无氧呼吸的价值不在于产能多，而在于快。它不依赖线粒体和氧，能在数秒内"
                            "把糖酵解产生的 NADH 重新氧化为 NAD⁺，让糖酵解这条通路继续运转；"
                            "没有这一步，糖酵解会因为缺少 NAD⁺ 而停摆。这正是冲刺、举重这类"
                            "短时高强度动作的能量基础。"),
             add=ev("p2-3", "血乳酸清除动力学", "https://zh.wikipedia.org/wiki/乳酸",
                    "中等强度运动后血乳酸半衰期约 15–25 分钟，多在 1–2 小时内回到基线。",
                    "乳酸清除速度远快于 DOMS 出现时间。")),
    scores2=dict(S1=90,S2=88,S3=84,A1=88,A2=86,A3=82,C1=88,C2=82,L1=88,L2=86,L3=84),
    summary2="二稿补全 NAD⁺ 再生的机制解释，并为时间反证挂上证据，论证闭环。",
    pub="APPROVE",
    pub_reason="辨析角度好，证据与推理衔接紧密，同意展示。",
))

# ================= A2 斜面 =================
POSTS.append(dict(
    key="P3", assign="A2", author=STU,
    title="斜面上的物体会不会滑？先算 tanθ 和 μ",
    blocks=[
        h2("p3-h", "判断顺序：先分解，再比较"),
        para("p3-1", "判断斜面上物体是否滑动，不需要先算加速度。只需把重力沿坡分解，"
                     "比较下滑分量 mg·sinθ 与最大静摩擦力 μmg·cosθ，等价于比较 tanθ 与 μ。", "结论："),
        ("FIG", "p3-f1", "incline_forces", "斜面受力分解示意图",
         "图 1　重力被拆成沿坡与垂直坡面两个分量", "作者依据受力分析规则绘制"),
        para("p3-2", "垂直方向上物体没有加速度，因此支持力 N = mg·cosθ。这一等式是后面所有讨论的前提，"
                     "也是初学者最容易漏掉的一步：把 N 直接写成 mg 会连锁导致摩擦力算错。"),
        mathblock("p3-m1", "a = g\\left(\\sin\\theta - \\mu\\cos\\theta\\right)"),
        para("p3-3", "上式的适用条件是物体已经在滑动。若 tanθ ≤ μ，物体保持静止，此时摩擦力是静摩擦，"
                     "大小等于 mg·sinθ，随倾角增大而增大，而不是固定为 μN。"),
        ("FIG", "p3-f2", "incline_states", "三种摩擦状态对比图",
         "图 2　静止、临界、加速下滑三种情形的列式差异", "依据牛顿第二定律与摩擦定律整理"),
        para("p3-4", "把三种情形分开列式之后，题目里「恰好开始滑动」「保持匀速」「加速下滑」这些措辞"
                     "就有了明确的数学对应，不再靠语感判断。"),
    ],
    evidence=[
        ev("p3-1", "摩擦力（维基百科）", "https://zh.wikipedia.org/wiki/摩擦力",
           "最大静摩擦力近似等于 μ 与正压力的乘积，滑动摩擦力方向与相对运动方向相反。",
           "静摩擦存在上限 μN，比较 tanθ 与 μ 即可判断是否滑动。"),
        ev("p3-2", "牛顿运动定律（维基百科）", "https://zh.wikipedia.org/wiki/牛顿运动定律",
           "物体在垂直斜面方向无加速度时，该方向合力为零。",
           "支持力 N 等于重力垂直分量 mg·cosθ。"),
        ev("p3-3", "斜面（维基百科）", "https://zh.wikipedia.org/wiki/斜面",
           "无摩擦斜面下滑加速度为 g·sinθ，有摩擦时为 g(sinθ − μcosθ)。",
           "加速度表达式与本文推导一致。"),
    ],
    scores=dict(S1=86,S2=82,S3=80,A1=88,A2=80,A3=76,C1=84,C2=74,L1=84,L2=82,L3=80),
    summary="推导链完整，N = mg·cosθ 这一步强调得当；静摩擦与滑动摩擦的区分还可更明确。",
    feedbacks=[("p3-3","A3","「摩擦力是静摩擦，大小等于 mg·sinθ」写得对，但没有说明这个结论只在"
                            "未达到最大静摩擦前成立，建议补一句适用边界。"),
               ("p3-4","L2","结尾把措辞与数学对应起来很好，如果能配一道例题验证会更完整。")],
    v2=dict(change=("p3-3", "上式的适用条件是物体已经在滑动。若 tanθ ≤ μ，物体保持静止，此时摩擦力是静摩擦，"
                            "其大小由平衡条件决定，等于 mg·sinθ，并随倾角增大而增大；"
                            "但它不能无限增长，上限为 μmg·cosθ。一旦所需静摩擦超过上限，物体开始滑动，"
                            "摩擦力随即转为滑动摩擦 μN，此后不再随倾角线性变化。"),
             add=ev("p3-3", "静摩擦与滑动摩擦的转换", "https://zh.wikipedia.org/wiki/摩擦力",
                    "静摩擦力大小随外力变化，最大值由 μN 决定；超过最大值后转为滑动摩擦。",
                    "静摩擦有上限，达到上限即开始滑动。")),
    scores2=dict(S1=92,S2=88,S3=86,A1=90,A2=86,A3=84,C1=88,C2=82,L1=88,L2=86,L3=84),
    summary2="二稿明确了静摩擦的适用边界与转换条件，概念边界清晰。",
    pub="APPROVE",
    pub_reason="推导规范、边界条件交代清楚，可作为受力分析范例展示。",
))

POSTS.append(dict(
    key="P4", assign="A2", author=PEER,
    title="三步解斜面题：受力图、分量式、临界判断",
    blocks=[
        h2("p4-h", "固定流程比临时推理更可靠"),
        para("p4-1", "斜面题失分往往不在物理，而在顺序。把解题固定成三步——画受力图、写分量式、"
                     "判断临界——可以显著降低漏项概率。", "结论："),
        ("FIG", "p4-f1", "incline_curve", "加速度随倾角变化曲线",
         "图 1　加速度随倾角单调上升，起点处存在静止区", "按 a = g(sinθ − μcosθ) 计算绘制"),
        para("p4-2", "第一步只画三个力：重力、支持力、摩擦力。多画一个「下滑力」是最常见的错误——"
                     "那是重力的分量，不是独立存在的力。"),
        bullets("p4-l1", [
            "重力 mg：竖直向下，永不改变方向。",
            "支持力 N：垂直斜面向上，大小为 mg·cosθ。",
            "摩擦力 f：沿斜面方向，方向与相对运动（或趋势）相反。",
        ]),
        ("FIG", "p4-f2", "incline_states", "三种摩擦状态对比图",
         "图 2　三种情形分别列式，避免混淆静摩擦与滑动摩擦", "依据牛顿第二定律与摩擦定律整理"),
        para("p4-3", "第三步做临界判断：先算 tanθ 与 μ 的大小关系，再决定用平衡方程还是牛顿第二定律。"
                     "这一步顺序反了，方程就会写错。"),
        callout("p4-c1", "检查清单：N 是否写成 mg·cosθ？摩擦方向是否与运动趋势相反？"
                         "是否先判断了静止还是滑动？三问过一遍，绝大多数错误可以拦下。"),
        para("p4-4", "把流程写成检查清单后，我在这类题上的错误率明显下降，尤其是漏判临界状态的问题。"),
    ],
    evidence=[
        ev("p4-2", "受力分析（维基百科）", "https://zh.wikipedia.org/wiki/受力分析",
           "受力分析应只画物体实际受到的力，不应把某个力的分量当作独立力。",
           "「下滑力」是重力分量，不是独立力。"),
        ev("p4-3", "摩擦力（维基百科）", "https://zh.wikipedia.org/wiki/摩擦力",
           "静摩擦与滑动摩擦遵循不同的计算规则，需先判断物体是否已发生相对滑动。",
           "临界判断决定使用哪一套方程。"),
        ev("p4-4", "斜面（维基百科）", "https://zh.wikipedia.org/wiki/斜面",
           "斜面上物体的加速度取决于倾角与摩擦系数。",
           "三步流程覆盖了斜面题的主要变量。"),
    ],
    scores=dict(S1=82,S2=86,S3=84,A1=84,A2=86,A3=80,C1=78,C2=72,L1=86,L2=84,L3=82),
    summary="方法性强，检查清单实用；但缺少具体例题，抽象建议的落地感不足。",
    feedbacks=[("p4-1","C1","「显著降低漏项概率」是效果断言，缺少证据。建议补一个自己的错题对比，"
                            "或把断言改成经验描述。"),
               ("p4-3","A1","临界判断讲清了，但没有给出一个带数值的例子，读者难以自测是否真的会用。")],
    v2=dict(change=("p4-3", "第三步做临界判断：先算 tanθ 与 μ 的大小关系，再决定用平衡方程还是牛顿第二定律。"
                            "例如 θ = 30°、μ = 0.5 时，tan30° ≈ 0.58 > 0.5，物体已经滑动，"
                            "此时应直接用 a = g(sinθ − μcosθ) ≈ 0.72 m/s²；"
                            "若把 θ 换成 20°，tan20° ≈ 0.36 < 0.5，则物体静止，a = 0。"),
             add=ev("p4-3", "斜面（维基百科）", "https://zh.wikipedia.org/wiki/斜面",
                    "倾角小于摩擦角 arctan μ 时物体保持静止。",
                    "临界判断可用 tanθ 与 μ 的比较完成。")),
    scores2=dict(S1=88,S2=90,S3=88,A1=88,A2=90,A3=86,C1=86,C2=80,L1=88,L2=86,L3=86),
    summary2="二稿补入带数值的临界判断示例，方法可复现；效果断言已改为可验证表述。",
    pub="APPROVE",
    pub_reason="方法清晰、示例到位，适合作为解题流程参考。",
))

# ================= A3 导数 =================
POSTS.append(dict(
    key="P5", assign="A3", author=STU,
    title="把 f′(x) 的符号画成升降表",
    blocks=[
        h2("p5-h", "单调性本质是符号问题"),
        para("p5-1", "判断单调区间不需要看函数图像长什么样，只需要看 f′(x) 在每一段上的正负。"
                     "把定义域按驻点切开、逐段判号，结论自然出来。", "结论："),
        ("FIG", "p5-f1", "derivative_monotonic", "导数符号与单调区间对应图",
         "图 1　以 f(x)=x³−3x 为例，色带对应 f′(x) 的正负", "曲线按 f(x)=x³−3x 采样绘制"),
        para("p5-2", "以 f(x) = x³ − 3x 为例，f′(x) = 3x² − 3 = 3(x−1)(x+1)。驻点为 x = ±1，"
                     "把定义域切成三段，代入测试点即可确定每段符号。"),
        mathblock("p5-m1", "f'(x)=3x^2-3=3(x-1)(x+1)"),
        ("FIG", "p5-f2", "extremum_flow", "求极值四步流程图",
         "图 2　四步检查清单，每一步都可在卷面上留痕", "依据教材导数应用章节整理"),
        para("p5-3", "注意 f′(x) = 0 只是极值的必要条件。若导数在某点两侧不变号，该点就不是极值点，"
                     "例如 f(x) = x³ 在 x = 0 处。"),
        callout("p5-c1", "升降表三列就够：区间、f′(x) 符号、f(x) 单调性。多写一列「图像」反而容易出错。"),
        para("p5-4", "把符号判断表格化之后，含参数的题目也能按同一流程处理：把参数当作分类讨论的分界即可。"),
    ],
    evidence=[
        ev("p5-1", "导数（维基百科）", "https://zh.wikipedia.org/wiki/导数",
           "若函数在某区间内导数恒正，则函数在该区间单调递增。",
           "单调性由导数符号决定。"),
        ev("p5-2", "驻点（维基百科）", "https://zh.wikipedia.org/wiki/驻点",
           "导数为零的点称为驻点，但驻点不一定是极值点。",
           "f′(x)=0 是极值的必要条件而非充分条件。"),
        ev("p5-3", "极值（维基百科）", "https://zh.wikipedia.org/wiki/极值",
           "极值点处导数若存在则必为零；反之不成立。",
           "x³ 在 x=0 处导数为零但非极值。"),
    ],
    scores=dict(S1=88,S2=84,S3=82,A1=86,A2=82,A3=78,C1=82,C2=76,L1=86,L2=84,L3=80),
    summary="符号法讲得清楚，反例使用准确；升降表的操作细节可以更具体。",
    feedbacks=[("p5-3","S3","反例选得好，但只说了「不是极值点」，没有说明为什么——"
                            "补上「两侧导数同号」这一判据会更完整。"),
               ("p5-4","A3","含参数讨论一句带过，缺少一个具体参数例子，读者很难迁移。")],
    v2=dict(change=("p5-3", "注意 f′(x) = 0 只是极值的必要条件。若导数在某点两侧不变号，该点就不是极值点。"
                            "例如 f(x) = x³ 在 x = 0 处导数为零，但左右两侧导数均为正，"
                            "函数始终递增，因此 x = 0 不是极值点，只是图像上的一个「拐点」。"
                            "判据可以一句话概括：看 f′(x) 在该点两侧是否变号。"),
             add=ev("p5-3", "拐点（维基百科）", "https://zh.wikipedia.org/wiki/拐点",
                    "函数凹凸性发生改变的点称为拐点，其导数可能为零但不改变单调性。",
                    "x³ 在原点处单调性不变，故非极值点。")),
    scores2=dict(S1=92,S2=88,S3=88,A1=88,A2=86,A3=82,C1=86,C2=80,L1=90,L2=88,L3=84),
    summary2="二稿补入「两侧是否变号」判据与拐点概念，反例解释完整。",
    pub="APPROVE",
    pub_reason="判据表述准确，反例与概念衔接自然，同意展示。",
))

POSTS.append(dict(
    key="P6", assign="A3", author=PEER,
    title="极值点一定是驻点吗？一个常见反例",
    blocks=[
        h2("p6-h", "结论：驻点只是候选"),
        para("p6-1", "「导数为零」是极值的必要条件，不是充分条件。反过来，极值点也可能出现在"
                     "导数不存在的地方。两端都要留意。", "结论："),
        ("FIG", "p6-f1", "tangent_slope", "切线斜率与导数关系图",
         "图 1　切线的陡缓即导数值，位置越陡导数值越大", "曲线按 f(x)=x²/2 采样绘制"),
        para("p6-2", "第一类反例：f(x) = x³ 在 x = 0 处 f′(0) = 0，但函数单调递增，"
                     "该点不是极值点。第二类反例：f(x) = |x| 在 x = 0 处不可导，"
                     "却是一个极小值点。"),
        {"type": "table", "content": [
            {"type": "tableRow", "content": [
                {"type": "tableHeader", "attrs": {"colspan": 1, "rowspan": 1},
                 "content": [para("p6-t1", "情形")]},
                {"type": "tableHeader", "attrs": {"colspan": 1, "rowspan": 1},
                 "content": [para("p6-t2", "导数情况")]},
                {"type": "tableHeader", "attrs": {"colspan": 1, "rowspan": 1},
                 "content": [para("p6-t3", "是否极值")]},
            ]},
            {"type": "tableRow", "content": [
                {"type": "tableCell", "attrs": {"colspan": 1, "rowspan": 1},
                 "content": [para("p6-t4", "x³ 在 x=0")]},
                {"type": "tableCell", "attrs": {"colspan": 1, "rowspan": 1},
                 "content": [para("p6-t5", "f′(0)=0，两侧同号")]},
                {"type": "tableCell", "attrs": {"colspan": 1, "rowspan": 1},
                 "content": [para("p6-t6", "否")]},
            ]},
            {"type": "tableRow", "content": [
                {"type": "tableCell", "attrs": {"colspan": 1, "rowspan": 1},
                 "content": [para("p6-t7", "|x| 在 x=0")]},
                {"type": "tableCell", "attrs": {"colspan": 1, "rowspan": 1},
                 "content": [para("p6-t8", "不可导")]},
                {"type": "tableCell", "attrs": {"colspan": 1, "rowspan": 1},
                 "content": [para("p6-t9", "是（极小）")]},
            ]},
        ]},
        para("p6-3", "所以做题时不能只解 f′(x) = 0。完整做法是：解驻点、检查不可导点、"
                     "再看每个候选点两侧的符号变化。"),
        quote("p6-4", "把「必要条件」当成「充分条件」，是导数应用题里最稳定的一类失分点。"),
        para("p6-5", "理解了这一点，含参讨论时的分类依据也就清楚了：参数决定的是驻点个数与位置，"
                     "而是否取到极值仍要看变号。"),
    ],
    evidence=[
        ev("p6-1", "驻点（维基百科）", "https://zh.wikipedia.org/wiki/驻点",
           "导数为零的点称为驻点；驻点不一定是极值点，极值点也不一定是驻点。",
           "两类反例都成立。"),
        ev("p6-2", "绝对值函数（维基百科）", "https://zh.wikipedia.org/wiki/绝对值",
           "绝对值函数在原点处不可导，但取得极小值 0。",
           "极值点可出现在不可导点。"),
        ev("p6-3", "极值（维基百科）", "https://zh.wikipedia.org/wiki/极值",
           "判定极值需考察该点邻域内函数值的变化。",
           "判定应回到定义或符号变化。"),
    ],
    scores=dict(S1=86,S2=82,S3=80,A1=84,A2=80,A3=74,C1=80,C2=74,L1=84,L2=82,L3=78),
    summary="两类反例齐全，表格对比清楚；结论段的操作方法略简。",
    feedbacks=[("p6-3","A3","「解驻点、检查不可导点、看符号变化」三步写得对，但缺少一个完整算例，"
                            "建议补一个含参函数演示分类讨论。"),
               ("p6-4","C2","引用句是论断式表达，作为小结可以，但不宜代替论证。")],
    v2=dict(change=("p6-3", "所以做题时不能只解 f′(x) = 0。完整做法是三步：先解驻点，再单独检查定义域内的"
                            "不可导点，最后逐个候选点看两侧符号是否变化。以 f(x) = x³ − 3ax 为例，"
                            "f′(x) = 3x² − 3a：当 a > 0 时有两个驻点 x = ±√a，左侧正、中间负、右侧正，"
                            "故 x = −√a 取极大值、x = √a 取极小值；当 a ≤ 0 时 f′(x) ≥ 0 恒成立，"
                            "函数单调递增，没有极值点。"),
             add=ev("p6-3", "含参函数极值讨论", "https://zh.wikipedia.org/wiki/导数",
                    "含参数时需按判别式或参数范围分类讨论驻点个数。",
                    "参数范围决定是否存在极值点。")),
    scores2=dict(S1=90,S2=86,S3=86,A1=88,A2=86,A3=84,C1=84,C2=78,L1=88,L2=86,L3=82),
    summary2="二稿补入含参算例，三步法落到具体计算；小结的论断已由算例支撑。",
    pub="RETURN",
    pub_reason="内容已达标，但表格中的「是否极值」一列缺少判据说明，请补充后再申请展示。",
))

# ================= A4 论证 =================
POSTS.append(dict(
    key="P7", assign="A4", author=STU,
    title="论证不是堆材料：一条能自查的链条",
    blocks=[
        h2("p7-h", "主张—理由—证据—反驳"),
        para("p7-1", "很多知识帖看起来材料很多，读起来却没有说服力，原因是材料与主张之间缺少可检验的连接。"
                     "把论证拆成四环，每一环单独自查，问题就会暴露出来。", "结论："),
        ("FIG", "p7-f1", "argument_chain", "论证链条结构图",
         "图 1　从主张倒推到证据，反驳环节决定论证韧性", "结构参考图尔敏论证模型"),
        para("p7-2", "第一环是主张，必须是一句可以判断真假的陈述；第二环是理由，它要能独立成立；"
                     "第三环是证据，必须具体到可核查；第四环是反驳，要针对最强反对意见而非稻草人。"),
        ("FIG", "p7-f2", "argument_score", "论证强度评分对比图",
         "图 2　初稿与修订稿在证据环节的差距最明显", "评分口径为本课程量规 C 维度示意"),
        para("p7-3", "我最初的稿件在第三环最弱：写了「多项研究表明」，却没有给出任何一项研究的"
                     "名称、样本或统计口径。这类表述看似有据，实际上无法核查。"),
        quote("p7-4", "自检方法：把每个「研究表明」都改写成「某年某机构对多少样本做了什么测量，"
                      "得到什么数值」，改不出来的，就是需要补证据的地方。"),
        para("p7-5", "按这个标准重写后，论证的推进速度反而变快了——因为每一段都在为同一句主张服务，"
                     "不再有可有可无的段落。"),
    ],
    evidence=[
        ev("p7-1", "图尔敏模型（维基百科）", "https://zh.wikipedia.org/wiki/图尔敏模型",
           "图尔敏模型把论证分为主张、依据、正当理由、支援、限定与反驳六个要素。",
           "论证可拆解为可单独检验的环节。"),
        ev("p7-2", "论证（维基百科）", "https://zh.wikipedia.org/wiki/论证",
           "论证是由前提推出结论的过程，前提需具备可检验性。",
           "理由与证据需可独立检验。"),
        ev("p7-3", "证据（维基百科）", "https://zh.wikipedia.org/wiki/证据",
           "证据应具备相关性、可靠性与可核查性。",
           "「多项研究表明」缺少可核查信息。"),
    ],
    scores=dict(S1=84,S2=80,S3=76,A1=80,A2=76,A3=72,C1=78,C2=68,L1=82,L2=78,L3=74),
    summary="结构意识强，自检方法实用；证据环节仍偏抽象，缺少具体案例支撑。",
    feedbacks=[("p7-3","C2","「写了多项研究表明」这个自我批评很好，但没有给出改写后的样子，"
                            "读者学不到具体做法。"),
               ("p7-2","A2","四环的界定清楚，不过「最强反对意见」如何识别没有交代。")],
    v2=dict(change=("p7-3", "我最初的稿件在第三环最弱：写了「多项研究表明」，却没有给出任何一项研究的"
                            "名称、样本或统计口径。这类表述看似有据，实际上无法核查。"
                            "改写后的句子是这样的：「伦敦在 2008 年设立低排放区后，"
                            "市政府连续三年公布的监测数据显示，区域内二氧化氮年均浓度下降约 20%，"
                            "监测点数量与统计口径均在报告中列明。」两句话的区别只在于：后者可以被人查证。"),
             add=ev("p7-3", "证据的可核查性", "https://zh.wikipedia.org/wiki/证据",
                    "可核查的证据应给出可追溯的来源、时间与统计口径。",
                    "改写后句子具备可追溯要素。")),
    scores2=dict(S1=90,S2=86,S3=84,A1=86,A2=82,A3=80,C1=86,C2=78,L1=88,L2=84,L3=80),
    summary2="二稿给出了改写前后的对照，方法可模仿；证据环节的抽象问题已解决。",
    pub="PENDING",
    pub_reason="作者申请在课程社区展示，等待教师审核。",
))

POSTS.append(dict(
    key="P8", assign="A4", author=PEER,
    title="把反驳写进正文：让步段的三种写法",
    blocks=[
        h2("p8-h", "先承认，再回应"),
        para("p8-1", "让步段不是示弱，而是提高论证可信度的技术动作。承认对方合理之处，"
                     "再用更强的理由回应，读者的防御心理会明显降低。", "结论："),
        ("FIG", "p8-f1", "argument_outline", "论证提纲结构图",
         "图 1　一总三分的提纲，让步段与证据型分论点并列", "提纲模板来自本课程写作工作单"),
        para("p8-2", "第一种写法是「承认合理性 + 补充条件」：先说明对方在什么范围内成立，"
                     "再指出在本题语境下条件不满足。这种写法最稳妥，适合时间有限的考场。"),
        bullets("p8-l1", [
            "写法一：承认合理性 + 补充条件（最稳妥）。",
            "写法二：承认事实 + 换算代价（适合有数据时）。",
            "写法三：承认局部 + 反驳推论（适合对方推理有跳跃时）。",
        ]),
        ("FIG", "p8-f2", "argument_chain", "论证链条结构图",
         "图 2　反驳环节位于链条末端，决定论证是否闭环", "结构参考图尔敏论证模型"),
        para("p8-3", "第二种写法要用数据把「代价」换算成可比尺度。比如对方说低排放区会抬高物流成本，"
                     "可以回应：新增成本占终端售价的比例是多少，与健康收益相比处于什么量级。"),
        quote("p8-4", "判断让步段是否合格，只看一件事：删掉这一段，论证会不会变弱。如果不会，"
                      "这段就是装饰，不是论证。"),
        para("p8-5", "三种写法并非互斥，实际写作中可以按段落重要程度组合使用。"),
    ],
    evidence=[
        ev("p8-1", "让步（维基百科）", "https://zh.wikipedia.org/wiki/让步",
           "让步是在论证中先承认对方部分观点，再提出更有力反驳的修辞策略。",
           "让步可提升论证可信度。"),
        ev("p8-2", "图尔敏模型（维基百科）", "https://zh.wikipedia.org/wiki/图尔敏模型",
           "反驳（rebuttal）用于说明在何种条件下结论不成立。",
           "反驳环节使论证边界明确。"),
        ev("p8-3", "论证（维基百科）", "https://zh.wikipedia.org/wiki/论证",
           "有效的论证需要回应最强反对意见，而非易被击倒的弱版本。",
           "让步段应针对真实反对意见。"),
    ],
    scores=dict(S1=80,S2=84,S3=78,A1=78,A2=80,A3=76,C1=74,C2=70,L1=84,L2=80,L3=78),
    summary="三种写法归纳清晰，删除测试的判据有价值；证据多引自概念条目，实例偏少。",
    feedbacks=[("p8-3","C2","「换算成可比尺度」讲得对，但没给出具体数字，建议补一个量级对比。"),
               ("p8-4","S3","删除测试这个判据很好用，可以再说明为什么装饰性段落会削弱论证。")],
    v2=dict(change=("p8-3", "第二种写法要用数据把「代价」换算成可比尺度。比如对方说低排放区会抬高物流成本，"
                            "可以回应：按伦敦 2019 年的评估，合规改造带来的物流成本增量约占终端售价的 0.3%–0.8%，"
                            "而同期区域内与交通污染相关的呼吸道就诊量下降幅度在两位数百分比区间——"
                            "两个量级摆在一起，读者自己就能判断轻重。"),
             add=ev("p8-3", "成本与健康收益的量级对比", "https://zh.wikipedia.org/wiki/外部性",
                    "环境政策评估常需将成本与健康收益折算到同一量级比较。",
                    "量级对比使让步段可判断。")),
    scores2=dict(S1=86,S2=88,S3=84,A1=84,A2=86,A3=82,C1=82,C2=78,L1=88,L2=84,L3=82),
    summary2="二稿补入量级对比数据，让步段从修辞变为论证；判据解释仍可再补一句。",
    pub="RETURN",
    pub_reason="三种写法归纳清晰，但证据多引自概念条目，建议补充一个完整的让步段范文后再申请展示。",
))

def main():
    teacher = Client("teacher@example.test").login()
    stu = Client(STU).login()
    peer = Client(PEER).login()
    who = {STU: stu, PEER: peer}
    log("登录成功：教师 / 两名学生")

    aid_map = {}
    existing = {a["title"]: a["id"] for a in teacher.get(f"/api/courses/{CID}/assignments")}
    for a in ASSIGNMENTS:
        if a["title"] in existing:
            aid_map[a["key"]] = existing[a["title"]]
            log(f"[作业] {a['key']} 已存在 id={existing[a['title']]}")
            continue
        r = teacher.post(f"/api/courses/{CID}/assignments", {
            "title": a["title"], "requirements": a["req"],
            "dueAt": (datetime.datetime.now(datetime.timezone.utc) +
                      datetime.timedelta(days=a["days"])).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "aiFeedbackEnabled": False, "rubricId": None})
        aid_map[a["key"]] = r["id"]
        log(f"[作业] {a['key']} id={r['id']} {a['title']}")

    only = __import__("os").environ.get("SEED_ONLY")
    for post in POSTS:
        if only and post["key"] not in only.split(","):
            continue
        aid = aid_map[post["assign"]]
        c = who[post["author"]]
        log(f"\n=== {post['key']} {post['title']} ===")
        entry = c.post(f"/api/assignments/{aid}/entries")
        eid, rev = entry["id"], entry["revision"]
        log(f"  建立作答 entry={eid}")
        content = build_doc(c, eid, post["blocks"])
        d = c.put(f"/api/entries/{eid}/draft",
                  {"title": post["title"], "content": content, "revision": rev,
                   "evidence": post["evidence"]})
        rev = d["revision"]
        sub = c.post(f"/api/entries/{eid}/submissions", {"revision": rev},
                     extra_headers={"Idempotency-Key": f"{post['key'].lower()}-v1-{uuid.uuid4().hex[:8]}"})
        sid = sub["id"]
        log(f"  提交 v1 submission={sid}")

        det = teacher.get(f"/api/submissions/{sid}")
        for e in det["evidence"]:
            teacher.post(f"/api/evidence/{e['id']}/verifications",
                         {"verdict": "PASS",
                          "reason": "已对照课程提供的来源摘录核对，摘录与主张一致；引用范围未超出原文。"})
        log(f"  核验证据 {len(det['evidence'])} 条")

        teacher.post(f"/api/submissions/{sid}/evaluations",
                     {"items": items(post["scores"]), "gateReason": "",
                      "summary": post["summary"], "expectedRevision": 0})
        log("  发布 v1 评分")

        for pid, code, body in post["feedbacks"]:
            teacher.post(f"/api/submissions/{sid}/feedback",
                         {"paragraphId": pid, "indicator": code, "body": body})
        log(f"  教师批注 {len(post['feedbacks'])} 条")

        fb = c.get(f"/api/submissions/{sid}")["feedback"]
        if len(fb) >= 2:
            c.post(f"/api/feedback/{fb[0]['id']}/actions",
                   {"action": "REPLY", "body": "收到，这一点确实缺依据，二稿会补上可核查的来源与数值。"})
            c.post(f"/api/feedback/{fb[1]['id']}/actions",
                   {"action": "ADOPT", "body": "已按批注补充说明。"})
            log("  学生回应批注（回复 + 采纳）")

        # 二稿
        pid, new_text = post["v2"]["change"]
        blocks2 = []
        for b in post["blocks"]:
            if isinstance(b, dict) and b.get("type") == "paragraph" and b["attrs"]["blockId"] == pid:
                blocks2.append(para(pid, new_text, "修订："))
            else:
                blocks2.append(b)
        content2 = build_doc(c, eid, blocks2)
        ev2 = list(post["evidence"]) + [post["v2"]["add"]]
        d2 = c.put(f"/api/entries/{eid}/draft",
                   {"title": post["title"], "content": content2, "revision": rev, "evidence": ev2})
        try:
            sub2 = c.post(f"/api/entries/{eid}/submissions", {"revision": d2["revision"]},
                          extra_headers={"Idempotency-Key": f"{post['key'].lower()}-v2-{uuid.uuid4().hex[:8]}"})
            sid2 = sub2["id"]
        except RuntimeError as e:
            if "未变化" not in str(e):
                raise
            vers = c.get(f"/api/entries/{eid}")["versions"]
            sid2 = max(vers, key=lambda v: v["version_no"])["id"]
            log("  内容未变化，复用最新版本")
        log(f"  提交 v2 submission={sid2}")

        det2 = teacher.get(f"/api/submissions/{sid2}")
        for e in det2["evidence"]:
            if e["state"] in ("PENDING", "DISPUTED"):
                teacher.post(f"/api/evidence/{e['id']}/verifications",
                             {"verdict": "PASS",
                              "reason": "复核新增证据：来源可追溯，摘录与主张一致，同意采信。"})
        teacher.post(f"/api/submissions/{sid2}/evaluations",
                     {"items": items(post["scores2"]), "gateReason": "",
                      "summary": post["summary2"], "expectedRevision": 0})
        log("  发布 v2 评分")

        c.post(f"/api/submissions/{sid2}/publication-request",
               {"revision": 0, "reason": post["pub_reason"]})
        recs = teacher.get(f"/api/courses/{CID}/publication-records")
        rec = [r for r in recs if r["submission_id"] == sid2][0]
        if post["pub"] == "APPROVE":
            teacher.post(f"/api/publications/{rec['id']}/decisions",
                         {"action": "APPROVE", "reason": post["pub_reason"], "revision": rec["revision"]})
            log("  申请发表 → 教师通过")
        elif post["pub"] == "RETURN":
            teacher.post(f"/api/publications/{rec['id']}/decisions",
                         {"action": "RETURN", "reason": post["pub_reason"], "revision": rec["revision"]})
            log("  申请发表 → 教师退回")
        else:
            log("  申请发表 → 保持待审核")

    log("\n全部完成")
    pathlib.Path("/tmp/ockc_seed/run.log").write_text("\n".join(LOG), encoding="utf-8")

if __name__ == "__main__":
    main()
