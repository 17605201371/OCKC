// 页面上统一的时间显示：「10/28 12:41」这种月日 + 24 小时制时分。
export function fmt(d: string) {
  if (!d) return "—";
  return new Date(d).toLocaleString("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}
