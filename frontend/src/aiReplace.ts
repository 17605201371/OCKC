// 「采纳」要把 AI 给的那句话换回正文。段落内容是 text 与 inlineMath 混排的节点数组，
// 所以先在「纯文本视图」里定位 quote，再按位置重建节点；公式节点不能被切一半，
// 一旦 quote 的边界落在公式内部就放弃，交回人工处理。
type Piece = {
  kind: 'text' | 'math' | 'other';
  node: any;
  text: string;
  start: number;
  end: number;
};

function flatten(para: any): { plain: string; pieces: Piece[] } {
  const pieces: Piece[] = [];
  let plain = '';
  for (const n of para?.content || []) {
    let text = '';
    let kind: Piece['kind'] = 'other';
    if (n?.type === 'text') {
      text = n.text || '';
      kind = 'text';
    } else if (n?.type === 'inlineMath') {
      text = n.attrs?.latex || '';
      kind = 'math';
    }
    pieces.push({ kind, node: n, text, start: plain.length, end: plain.length + text.length });
    plain += text;
  }
  return { plain, pieces };
}

// replacement 里的公式约定写成 $...$，这里还原成 inlineMath 节点。
// 模型经常不守约定、直接把原文的 LaTeX 照抄成裸文本，所以再把「被替换范围里出现过的公式」
// 当作词典扫一遍，命中的片段同样还原成节点，避免 \sin 2\theta 变成一串反斜杠。
function splitByKnown(text: string, known: string[]): any[] {
  if (!text || !known.length) return text ? [{ type: 'text', text }] : [];
  const out: any[] = [];
  let rest = text;
  let guard = 0;
  while (rest && guard++ < 50) {
    let at = -1;
    let hit = '';
    for (const k of known) {
      const i = rest.indexOf(k);
      if (i < 0) continue;
      if (at < 0 || i < at || (i === at && k.length > hit.length)) {
        at = i;
        hit = k;
      }
    }
    if (at < 0) {
      out.push({ type: 'text', text: rest });
      break;
    }
    if (at > 0) out.push({ type: 'text', text: rest.slice(0, at) });
    out.push({ type: 'inlineMath', attrs: { latex: hit } });
    rest = rest.slice(at + hit.length);
  }
  return out;
}

function parseReplacement(value: string, known: string[] = []): any[] {
  const out: any[] = [];
  const re = /\$([^$]+)\$/g;
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(value))) {
    if (m.index > last) out.push(...splitByKnown(value.slice(last, m.index), known));
    const latex = m[1].trim();
    if (latex) out.push({ type: 'inlineMath', attrs: { latex } });
    last = m.index + m[0].length;
  }
  if (last < value.length) out.push(...splitByKnown(value.slice(last), known));
  return out;
}

function mergeText(nodes: any[]): any[] {
  const out: any[] = [];
  for (const n of nodes) {
    const prev = out[out.length - 1];
    if (n?.type === 'text' && prev?.type === 'text') {
      prev.text = (prev.text || '') + (n.text || '');
      continue;
    }
    out.push(n);
  }
  return out.filter((n) => n.type !== 'text' || (n.text || '').length > 0);
}

function cloneNode(node: any): any {
  return JSON.parse(JSON.stringify(node));
}

export type ReplaceResult = { doc: any; before: any; after: string };

// 返回 null 表示没法安全替换：段落不在了、原句找不到、或边界切在公式中间。
export function applySuggestion(
  doc: any,
  paragraphId: string,
  quote: string,
  replacement: string,
): ReplaceResult | null {
  if (!doc?.content || !paragraphId || !quote || !replacement) return null;
  const index = doc.content.findIndex((b: any) => b?.attrs?.blockId === paragraphId);
  if (index < 0) return null;
  const para = doc.content[index];
  if (!Array.isArray(para.content)) return null;

  const { plain, pieces } = flatten(para);
  const at = plain.indexOf(quote);
  if (at < 0) return null;
  const stop = at + quote.length;

  // 被替换范围里原有的公式，作为识别 replacement 中裸 LaTeX 的词典。
  const known = pieces
    .filter((p) => p.kind === 'math' && p.end > at && p.start < stop && p.text)
    .map((p) => p.text)
    .sort((a, b) => b.length - a.length);

  const repPieces = parseReplacement(replacement, known);
  if (!repPieces.length) return null;

  const out: any[] = [];
  let inserted = false;
  const markOf = (node: any) => (Array.isArray(node?.marks) && node.marks.length ? { marks: node.marks } : {});

  for (const p of pieces) {
    const overlaps = p.end > at && p.start < stop;
    if (!overlaps) {
      if (!inserted && p.start >= stop) {
        out.push(...repPieces);
        inserted = true;
      }
      out.push(cloneNode(p.node));
      continue;
    }
    if (p.kind === 'math') {
      // 只允许整块公式被整体替换，切一半会破坏 latex。
      if (!(p.start >= at && p.end <= stop)) return null;
      if (!inserted) {
        out.push(...repPieces);
        inserted = true;
      }
      continue;
    }
    if (p.kind !== 'text') return null;
    const left = p.text.slice(0, Math.max(0, at - p.start));
    const right = p.text.slice(Math.max(0, stop - p.start));
    const extra = markOf(p.node);
    if (left) out.push({ type: 'text', text: left, ...extra });
    if (!inserted) {
      out.push(...repPieces);
      inserted = true;
    }
    if (right) out.push({ type: 'text', text: right, ...extra });
  }
  if (!inserted) out.push(...repPieces);

  const merged = mergeText(out);
  if (!merged.length) return null;

  const nextPara = { ...cloneNode(para), content: merged };
  const nextDoc = { ...doc, content: doc.content.map((b: any, i: number) => (i === index ? nextPara : b)) };
  return { doc: nextDoc, before: cloneNode(para), after: paragraphText(nextPara) };
}

// 撤销：把段落原样换回去。
export function restoreParagraph(doc: any, paragraphId: string, before: any): any {
  if (!doc?.content || !paragraphId || !before) return doc;
  return {
    ...doc,
    content: doc.content.map((b: any) => (b?.attrs?.blockId === paragraphId ? cloneNode(before) : b)),
  };
}

// 段落纯文本，用于对比预览。
export function paragraphText(node: any): string {
  return flatten(node).plain;
}
