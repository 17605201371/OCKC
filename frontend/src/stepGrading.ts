// 步骤型作业的评分常量与正文定位，批改面板和阅读页共用。
// 标签与后端 CommunityService.STEP_TAGS 保持一致，新增要两边同时改。
export const STEP_TAGS = ["忽略题设条件", "计算或单位错误", "概念混淆", "论证跳跃", "表述不清"];
export const STEP_DEFAULT_MAX = 10;
export function scrollToStep(index: number) {
  const el = document.getElementById("step-card-" + index);
  if (!el) return;
  el.scrollIntoView({ behavior: "smooth", block: "center" });
  // 右侧点一下，正文里对应的那一步短暂高亮，避免滚过去还找不到。
  el.classList.add("step-card-focus");
  setTimeout(() => el.classList.remove("step-card-focus"), 1400);
}
