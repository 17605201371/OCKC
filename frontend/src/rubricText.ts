// 量规里由系统自动生成的套话：只是把指标名套进同一句话，或是开发期的免责说明，阅读时不显示。
// 数据本身不改（已启用的量规版本是提交时的快照），教师编辑草稿时仍能看到并替换这些文字。
const INSTRUCTION_BOILERPLATE = '以下为通用锚点，不代表特定学科的标准答案。';

export function rubricInstructions(text: unknown): string {
  return String(text ?? '').replace(INSTRUCTION_BOILERPLATE, '').trim();
}

export function criterionDescription(item: any): string {
  const text = String(item?.description ?? '').trim();
  return text === `依据作品实际内容判断「${item?.name}」，评分应与可核验文本相对应。` ? '' : text;
}
