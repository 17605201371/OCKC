// 文档结构工具：正文块锚点、标题列表与带层级编号的大纲。编辑页与阅读页共用。
export function anchorOptions(doc: any) {
  const out: { id: string; text: string }[] = [];
  function walk(n: any) {
    if (n.attrs?.blockId) {
      const texts = (x: any): string =>
        (x.text || "") + (x.content || []).map(texts).join("");
      out.push({
        id: n.attrs.blockId,
        text: (n.type === "figure" ? "[图片] " + (n.attrs.caption || n.attrs.alt || "未添加图注") : n.type === "mathBlock" ? "[公式] " + n.attrs.latex : texts(n)).slice(0, 60) || "空段落",
      });
    }
    for (const c of n.content || []) walk(c);
  }
  if (doc) walk(doc);
  return out;
}
export function documentHeadings(doc: any) {
  const out: { id: string; text: string; level: number }[] = [];
  function walk(n: any) {
    if (n.type === 'heading' && n.attrs?.blockId) out.push({ id: n.attrs.blockId, text: (n.content || []).map((x: any) => x.text || '').join('') || '未命名章节', level: n.attrs.level || 1 });
    for (const c of n.content || []) walk(c);
  }
  if (doc) walk(doc);
  return out;
}
// 大纲按 H1/H2/H3 生成 1 / 1.1 / 1.1.1 层级编号，避免长文里只剩一串连续序号。
export function outlineNumbered(doc: any) {
  const heads = documentHeadings(doc);
  if (!heads.length) return [];
  const base = Math.min(...heads.map((h) => h.level || 1));
  const counters = [0, 0, 0];
  return heads.map((h) => {
    const depth = Math.min(Math.max((h.level || 1) - base, 0), 2);
    counters[depth] += 1;
    for (let i = depth + 1; i < 3; i++) counters[i] = 0;
    const parts = counters.slice(0, depth + 1).map((n) => n || 1);
    // 正文里已经手写了「2.1 xxx」时，大纲不再重复一次编号。
    const text = String(h.text || "").replace(/^\d+(?:\.\d+)*[\s、.．]*/, "") || h.text;
    return { ...h, text, number: parts.join("."), depth };
  });
}
