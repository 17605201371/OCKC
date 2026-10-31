# 主题与媒体状态

- 同源 `/assets/theme-init.js` 在 Vue 挂载前解析 light/dark/system；不放宽 script CSP。
- data-theme 是解析后的 light/dark；data-theme-preference 保存用户选择；原生 color-scheme 和 theme-color 同步。
- 系统变化仅影响 system；storage 事件同步同源标签页，移除/清空恢复 system；禁用 localStorage 仍可在内存中切换。
- 工作区、图注、图片工具、表头、提示块和证据按钮使用语义颜色；不反转原始图片颜色。
- 图片失败不删除来源、图注或证据；重新加载只重建图片元素，不写入草稿或更改 URL。
- 主题单测覆盖 10 组初始组合及系统/跨窗/存储故障；浏览器专项覆盖首屏、真实系统偏好、富文本组件与加载重试。
