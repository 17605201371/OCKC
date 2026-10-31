import { createRequire } from "node:module";
import fs from "node:fs";
import assert from "node:assert/strict";
const { chromium } = createRequire(
  new URL("../frontend/package.json", import.meta.url),
)("playwright");
const base = process.env.E2E_BASE_URL || "http://127.0.0.1:8084";
const out =
  process.env.E2E_OUTPUT_DIR ||
  decodeURIComponent(new URL("../.qa/writer", import.meta.url).pathname);
const password = process.env.E2E_PASSWORD;
if (!password) throw Error("Set E2E_PASSWORD for an isolated demo database");
fs.mkdirSync(out, { recursive: true });
const browser = await chromium.launch({
  headless: true,
  args: ["--no-sandbox"],
  ...(process.env.CHROMIUM_PATH
    ? { executablePath: process.env.CHROMIUM_PATH }
    : {}),
});
const page = await browser.newPage({
  viewport: { width: 1440, height: 1000 },
  colorScheme: "light",
  acceptDownloads: true,
});
const errors = [];
page.on("pageerror", (e) => {
  errors.push(e.message);
  console.error("Page error:", e.message);
});
page.on("dialog", (d) => d.accept());
const checks = [],
  snapshots = [];
const pass = (name) => checks.push(name);
async function capture(name) {
  await page.waitForTimeout(250);
  assert.equal(
    await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth,
    ),
    false,
    name + " overflow",
  );
  await page.screenshot({ path: `${out}/${name}.png`, fullPage: true });
  fs.writeFileSync(
    `${out}/${name}.html`,
    (await page.content())
      .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, "")
      .replace(/href="\/assets\//g, `href="${base}/assets/`),
  );
  snapshots.push(name);
}
async function endDocument(doc) {
  await doc.evaluate((el) => {
    el.focus();
    const r = document.createRange();
    r.selectNodeContents(el.lastElementChild);
    r.collapse(false);
    const s = getSelection();
    s.removeAllRanges();
    s.addRange(r);
    document.dispatchEvent(new Event("selectionchange"));
  });
  await page.waitForTimeout(80);
}
async function selectText(locator) {
  await locator.evaluate((el) => {
    el.closest("[contenteditable]")?.focus();
    const r = document.createRange();
    r.selectNodeContents(el);
    const s = window.getSelection();
    s.removeAllRanges();
    s.addRange(r);
    document.dispatchEvent(new Event("selectionchange"));
  });
  await page.getByRole("toolbar", { name: "选中文字格式" }).waitFor();
}
try {
  await page.goto(base);
  await page.getByLabel("账号邮箱").fill("student@example.test");
  await page.getByLabel("密码", { exact: true }).fill(password);
  const loginResponse = page.waitForResponse(
    (r) =>
      r.url().endsWith("/api/auth/login") && r.request().method() === "POST",
  );
  await page.getByRole("button", { name: "登录工作空间" }).click();
  assert.equal(
    (await loginResponse).status(),
    200,
    "demo login should succeed",
  );
  await page.getByRole("button", { name: "继续创作", exact: true }).click();
  const doc = page.locator('.prose-editor[contenteditable="true"]');
  await doc.waitFor();
  const original = await page.request
    .get(base + "/api/entries/1")
    .then((r) => r.json());
  const seedIds = ["intro", "p-light", "p-cycle", "p-gap"];
  await capture("01-writer");
  const light = doc.locator('[data-block-id="p-light"]');
  await light.evaluate((el) => {
    el.closest("[contenteditable]")?.focus();
    const r = document.createRange();
    r.selectNodeContents(el);
    r.collapse(true);
    const sel = getSelection();
    sel.removeAllRanges();
    sel.addRange(r);
    document.dispatchEvent(new Event("selectionchange"));
  });
  await page.waitForTimeout(80);
  await page.keyboard.type("/h2");
  await page.getByRole("dialog", { name: "插入内容菜单" }).waitFor();
  await page.keyboard.press("Enter");
  assert.equal(await light.evaluate((el) => el.tagName), "H2");
  pass(
    "paragraph-to-heading conversion preserves the existing evidence anchor",
  );
  await light.evaluate((el) => {
    el.closest("[contenteditable]")?.focus();
    const r = document.createRange();
    r.selectNodeContents(el);
    r.collapse(true);
    const sel = getSelection();
    sel.removeAllRanges();
    sel.addRange(r);
    document.dispatchEvent(new Event("selectionchange"));
  });
  await page.waitForTimeout(80);
  await page.keyboard.type("/正文");
  await page.getByRole("dialog", { name: "插入内容菜单" }).waitFor();
  await page.keyboard.press("Enter");
  assert.equal(await light.evaluate((el) => el.tagName), "P");
  pass("heading-to-paragraph conversion retains text and stable ID");

  await page
    .getByRole("button", { name: "Markdown 写作帮助", exact: true })
    .click();
  await page.getByRole("heading", { name: "像 Markdown 一样写作" }).waitFor();
  await capture("02-markdown-help");
  await page.getByRole("button", { name: "开始写作", exact: true }).click();
  await doc.click();
  await endDocument(doc);
  await page.keyboard.press("Enter");
  await page.keyboard.type("/");
  await page.getByRole("dialog", { name: "插入内容菜单" }).waitFor();
  await capture("03-slash-menu");
  await page.keyboard.type("h2");
  await page.keyboard.press("Enter");
  await page.keyboard.type("材料与能量的联系");
  assert.equal(
    await doc.locator("h2").last().textContent(),
    "材料与能量的联系",
  );
  pass("slash filtering and Enter insertion");
  await page.keyboard.press("Enter");
  await page.keyboard.type(
    "通过返回物质与反应条件，解释两个阶段如何相互支持。",
  );
  await page.keyboard.press("Enter");
  await page.keyboard.type("## ");
  await page.keyboard.type("返回物质与反应条件");
  assert.equal(
    await doc.locator("h2").last().textContent(),
    "返回物质与反应条件",
  );
  pass("Markdown heading shortcut");
  await page.keyboard.press("Enter");
  await page.keyboard.type("- ");
  await page.keyboard.type("ATP 被利用后形成 ADP 和无机磷酸。");
  await page.keyboard.press("Enter");
  await page.keyboard.type("NADPH 被利用后形成 NADP+。");
  await page.keyboard.press("Enter");
  await page.keyboard.press("Enter");
  assert.equal(await doc.locator("ul li").count(), 2);
  pass("Markdown bullet list");
  await page.keyboard.type("> ");
  await page.keyboard.type(
    "物质可以在两个阶段之间往返，能量转化不等于能量无限循环。",
  );
  await page.keyboard.press("Enter");
  await page.keyboard.press("Enter");
  assert.equal(await doc.locator("blockquote").count(), 1);
  pass("Markdown blockquote");
  await page.keyboard.type("``` ");
  await page.keyboard.type("ATP -> ADP + Pi\nNADPH -> NADP+");
  await page.keyboard.press("Control+Enter");
  assert.equal(await doc.locator("pre").count(), 1);
  pass("Markdown code fence");
  await page.keyboard.type("**反应条件**");
  assert.equal(await doc.locator("strong").last().textContent(), "反应条件");
  pass("Markdown bold shortcut");
  await page.keyboard.press("Enter");
  await page.keyboard.type("补充说明：材料与能量需要分别解释。");
  await selectText(
    doc
      .locator("p")
      .filter({ hasText: "补充说明：材料与能量需要分别解释。" })
      .last(),
  );
  await capture("04-selection-toolbar");
  await page.getByRole("button", { name: "加粗", exact: true }).click();
  pass("contextual bold formatting");
  const note = doc
    .locator("p")
    .filter({ hasText: "补充说明：材料与能量需要分别解释。" })
    .last();
  assert.equal(await note.locator("strong").count(), 1);
  await page.getByRole("button", { name: "撤销", exact: true }).click();
  assert.equal(await note.locator("strong").count(), 0);
  await page.getByRole("button", { name: "重做", exact: true }).click();
  assert.equal(await note.locator("strong").count(), 1);
  await selectText(note);
  pass("undo and redo formatting without losing document text");

  await page.keyboard.press("Control+k");
  await page.getByRole("heading", { name: "设置链接", exact: true }).waitFor();
  assert.equal(
    await page.getByRole("textbox", { name: "全局搜索" }).count(),
    0,
  );
  await capture("05-link-dialog");
  await page.getByLabel("链接地址").fill("https://example.org/reference");
  await page.getByRole("button", { name: "应用链接", exact: true }).click();
  await doc.locator('a[href="https://example.org/reference"]').waitFor();
  pass("link shortcut does not trigger global search");
  await selectText(
    doc
      .locator("p")
      .filter({ hasText: "补充说明：材料与能量需要分别解释。" })
      .last(),
  );
  await page
    .getByRole("button", { name: "为选中段落挂载证据", exact: true })
    .click();
  await page
    .getByRole("heading", { name: "挂载论断证据", exact: true })
    .waitFor();
  const anchor = await page.locator("select").inputValue();
  assert.ok(anchor);
  await page.getByRole("button", { name: "取消", exact: true }).click();
  pass("selection evidence action retains paragraph");
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await page.waitForTimeout(2200);
  const saved = await page.request
    .get(base + "/api/entries/1")
    .then((r) => r.json());
  const ids = [];
  const walk = (n) => {
    if (n.attrs?.blockId) ids.push(n.attrs.blockId);
    for (const c of n.content || []) walk(c);
  };
  walk(saved.content);
  for (const id of seedIds) assert.ok(ids.includes(id));
  assert.equal(ids.length, new Set(ids).size);
  assert.ok(JSON.stringify(saved.content).includes("codeBlock"));
  assert.ok(
    JSON.stringify(saved.content).includes("https://example.org/reference"),
  );
  pass(
    "saved structured blocks and safe link; existing paragraph IDs retained; no duplicates",
  );
  const frozen = await page.request
    .get(base + "/api/submissions/1")
    .then((r) => r.json());
  assert.ok(!JSON.stringify(frozen.content).includes("返回物质与反应条件"));
  pass("published old version unchanged");
  await page.getByRole("button", { name: "评分与反馈", exact: true }).click();
  await doc.click();
  await page.keyboard.press("Control+Home");
  await capture("06-document-focus");
  const downloadPromise = page.waitForEvent("download");
  await page
    .getByRole("button", { name: "导出 Markdown", exact: true })
    .click();
  const download = await downloadPromise;
  await download.saveAs(`${out}/document.md`);
  const md = fs.readFileSync(`${out}/document.md`, "utf8");
  assert.ok(
    md.includes("## 返回物质与反应条件") &&
      md.includes("```") &&
      md.includes("> 物质") &&
      md.includes("https://example.org/reference"),
  );
  assert.ok(!md.includes("blockId") && !md.includes("indicator"));
  pass(
    "Markdown export contains document formatting, excludes teaching metadata",
  );
  await page.reload();
  await page.getByRole("button", { name: "继续创作", exact: true }).click();
  await doc.locator("h2").filter({ hasText: "返回物质与反应条件" }).waitFor();
  assert.equal(await doc.locator("pre").count(), 1);
  pass("save and reload retain Markdown-rendered formatting");
  await page.emulateMedia({ colorScheme: "dark" });
  await page.getByRole("button", { name: "评分与反馈", exact: true }).click();
  await doc.click();
  await endDocument(doc);
  await capture("07-dark-document");
  await page.emulateMedia({ colorScheme: "light" });
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.getByRole("button", { name: "评分与反馈", exact: true }).click();
  await capture("08-pc-1280");
  assert.deepEqual(errors, []);
  pass("zero browser script errors; 1440 and 1280 no horizontal overflow");
  console.log(
    JSON.stringify(
      { status: "PASS", checks, snapshots, consoleErrors: errors },
      null,
      2,
    ),
  );
} finally {
  await browser.close();
}
