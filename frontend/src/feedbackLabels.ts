// 批注相关的显示文字：四维指标名、处理动作名，以及学生最近一次处理。
const DIM_LABELS: Record<string, string> = {
  S: "科学性", A: "准确性", C: "完整性", L: "逻辑性",
  S1: "科学性", S2: "科学性", S3: "科学性",
  A1: "准确性", A2: "准确性", A3: "准确性",
  C1: "完整性", C2: "完整性",
  L1: "逻辑性", L2: "逻辑性", L3: "逻辑性",
};
export function dimLabel(code: string) { return DIM_LABELS[code] || code || "批注"; }

// 学生最近一次处理：ADOPT 表示「我已修改」，只是声明，不等于问题已解决。
// priorFeedback 由后端直接带 student_action；本版批注只有 actions 列表，回退到列表里取最后一条。
const FEEDBACK_ACTION_LABELS: Record<string, string> = { REPLY: "回复", ADOPT: "已采纳", IGNORE: "未采纳", RESOLVE: "已解决", REOPEN: "重新打开" };
export function actionLabel(type: string) { return FEEDBACK_ACTION_LABELS[type] || type; }
export function studentAction(f: any) {
  if (f.student_action) return f.student_action;
  const acts = (f.actions || []).filter((a: any) => ["ADOPT", "REPLY", "IGNORE"].includes(a.action_type));
  return acts.length ? acts[acts.length - 1].action_type : "";
}
