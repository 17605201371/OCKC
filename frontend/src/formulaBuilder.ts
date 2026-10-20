/** Deterministic, local formula layout helpers. No evaluation, network or chemical balancing. */
export type FormulaKind = 'fraction' | 'power' | 'subscript' | 'root' | 'equation' | 'reaction';
export type FormulaField = { key: string; label: string; example: string };
export const formulaStructures: { id: FormulaKind; label: string; fields: FormulaField[] }[] = [
  { id: 'fraction', label: '分式', fields: [{ key: 'first', label: '分子', example: 'a + b' }, { key: 'second', label: '分母', example: 'c' }] },
  { id: 'power', label: '上标 / 幂', fields: [{ key: 'first', label: '底数', example: 'x' }, { key: 'second', label: '指数', example: '2' }] },
  { id: 'subscript', label: '下标', fields: [{ key: 'first', label: '主体', example: 'P' }, { key: 'second', label: '下标内容', example: 'i' }] },
  { id: 'root', label: '平方根', fields: [{ key: 'first', label: '根号内', example: 'a + b' }] },
  { id: 'equation', label: '等式', fields: [{ key: 'first', label: '等号左侧', example: 'E' }, { key: 'second', label: '等号右侧', example: 'm' }] },
  { id: 'reaction', label: '化学反应式', fields: [{ key: 'first', label: '反应物', example: '6 CO2 + 6 H2O' }, { key: 'second', label: '生成物', example: 'C6H12O6 + 6 O2' }] },
];
export const formulaTemplates = [
  { id: 'atp', label: 'ATP 转化', latex: String.raw`\mathrm{ATP} \rightarrow \mathrm{ADP} + P_i` },
  { id: 'energy', label: '能量', latex: String.raw`E = mc^2` },
  { id: 'fraction', label: '分式', latex: String.raw`\frac{a}{b}` },
  { id: 'photosynthesis', label: '光合作用总式', latex: String.raw`6\,\mathrm{CO_2} + 6\,\mathrm{H_2O} \xrightarrow{\text{光能}} \mathrm{C_6H_{12}O_6} + 6\,\mathrm{O_2}` },
  { id: 'quadratic', label: '一元二次求根', latex: String.raw`x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}` },
  { id: 'average', label: '算术平均', latex: String.raw`\bar{x} = \frac{1}{n}\sum_{i=1}^{n} x_i` },
];
export const MAX_FORMULA_LENGTH = 2000;
const greek: Record<string,string> = { 'α':'alpha','β':'beta','γ':'gamma','δ':'delta','θ':'theta','λ':'lambda','μ':'mu','π':'pi','σ':'sigma','φ':'phi','ω':'omega','Δ':'Delta','Σ':'Sigma','Ω':'Omega' };
function field(value: unknown, chemical = false): string {
  if (typeof value !== 'string' || !value.trim()) throw Error('请填写所有结构字段');
  const text = value.trim();
  if (text.length > 120) throw Error('每个结构字段最多 120 个字符');
  // Delimiters and commands are not accepted by the visual builder. Raw LaTeX has its own input.
  if (!/^[A-Za-z0-9 αβγδθλμπσφωΔΣΩ+\-=().,·×÷−]+$/.test(text)) throw Error('结构字段仅支持字母、数字、常用希腊字母及 + − = ( ) . , · × ÷；复杂语法请使用 LaTeX 输入');
  let safe = text.replace(/−/g, '-');
  if (chemical) safe = safe.replace(/([A-Za-z)])(\d+)/g, '$1_{$2}');
  safe = safe.replace(/[αβγδθλμπσφωΔΣΩ]/g, c => '\\'+greek[c]+' ').replace(/·|×/g, '\\cdot ').replace(/÷/g,'\\div ');
  return chemical ? '\\mathrm{'+safe+'}' : safe;
}
export function buildFormula(kind: FormulaKind, values: Record<string,string>): string {
  if (!formulaStructures.some(s => s.id === kind)) throw Error('请选择有效的公式结构');
  const a = field(values.first,kind==='reaction');
  const b = kind==='root' ? '' : field(values.second,kind==='reaction');
  switch(kind) {
    case 'fraction': return `\\frac{${a}}{${b}}`;
    case 'power': return `{${a}}^{${b}}`;
    case 'subscript': return `{${a}}_{${b}}`;
    case 'root': return `\\sqrt{${a}}`;
    case 'equation': return `${a} = ${b}`;
    case 'reaction':
      if (!['forward','reversible'].includes(values.arrow)) throw Error('请选择有效的反应箭头');
      return `${a} ${values.arrow==='reversible'?'\\rightleftharpoons':'\\rightarrow'} ${b}`;
  }
}
export function formulaSourceError(source: unknown): string {
  if (typeof source !== 'string' || !source.trim()) return '请填写有效的 LaTeX 公式';
  if (source.length > MAX_FORMULA_LENGTH) return `公式最多 ${MAX_FORMULA_LENGTH} 个字符`;
  if (/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(source)) return '公式含有不支持的控制字符';
  if (/\\(?:href|url|includegraphics|html\w*|def|gdef|edef|xdef|let|futurelet|newcommand|renewcommand|providecommand|input|include|catcode|require)\b/i.test(source)) return '公式不支持外部链接、HTML、资源加载或自定义宏命令';
  return '';
}
