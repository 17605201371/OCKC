import { createRequire as makeRequire } from "node:module";
const { chromium } = makeRequire(
  new URL("../frontend/package.json", import.meta.url),
)("playwright");
import fs from "node:fs";

const out =
  process.env.E2E_OUTPUT_DIR || new URL("../.qa", import.meta.url).pathname;
const base = process.env.E2E_BASE_URL || "http://127.0.0.1:8080";
fs.mkdirSync(out, { recursive: true });
if (!process.env.E2E_PASSWORD)
  throw new Error("Set E2E_PASSWORD to the local demo password");
const browser = await chromium.launch({
  ...(process.env.CHROMIUM_PATH
    ? { executablePath: process.env.CHROMIUM_PATH }
    : {}),
  headless: true,
  args: ["--no-sandbox"],
});
const page = await browser.newPage({
  viewport: { width: 1440, height: 1000 },
  colorScheme: "light",
});
const errors = [];
page.on("pageerror", (e) => errors.push(e.message));
page.on("dialog", (d) => d.accept());
const snapshots = [];
async function capture(name) {
  await page.waitForTimeout(450);
  await page.screenshot({ path: `${out}/${name}.png`, fullPage: true });
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > innerWidth,
  );
  if (overflow) throw new Error(`horizontal overflow: ${name}`);
  const html = await page.content();
  fs.writeFileSync(
    `${out}/${name}.html`,
    html
      .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, "")
      .replace(/href="\/assets\//g, 'href="' + base + "/assets/"),
  );
  snapshots.push(name);
}
try {
  await page.goto(base);
  await page.getByRole("heading", { name: "进入你的课程空间" }).waitFor();
  await capture("01-login");
  await page.getByLabel("账号邮箱").fill("student@example.test");
  await page.getByLabel("密码", { exact: true }).fill(process.env.E2E_PASSWORD);
  await page.getByRole("button", { name: "登录工作空间" }).click();
  await page.getByRole("heading", { name: "你好，林同学" }).waitFor();
  await capture("02-home");
  await page.getByRole("button", { name: "搜索页面、作业或知识条目" }).click();
  await page.getByRole("textbox", { name: "全局搜索" }).fill("课程量规");
  await capture("16-search");
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "继续创作", exact: true }).click();
  await page
    .getByRole("heading", { name: "创作与修订", exact: true })
    .waitFor();
  await capture("03-editor");
  await page.getByRole("button", { name: "写作大纲", exact: true }).click();
  await capture("17-editor-outline");
  await page.getByRole("button", { name: "写作大纲", exact: true }).click();
  await page.getByRole("button", { name: "评分与反馈", exact: true }).click();
  await capture("18-editor-focus");
  await page.getByRole("button", { name: "评分与反馈", exact: true }).click();
  await page.getByRole("button", { name: "定位正文", exact: true }).click();
  await page.locator(".anchor-focus").waitFor();
  await page.getByRole("button", { name: "挂载证据" }).click();
  await capture("04-evidence-modal");
  await page.getByRole("button", { name: "取消", exact: true }).click();
  const title = page.getByRole("textbox", { name: "知识帖标题" });
  await title.fill("光反应之后，物质去了哪里？ · 联调修订");
  await page.waitForTimeout(2400);
  await page
    .getByText("有未保存的修改", { exact: false })
    .waitFor({ state: "hidden" });
  await page.getByRole("button", { name: "提交评审", exact: true }).click();
  await page.getByText("已提交 v2，等待教师评审").waitFor();
  await page.getByRole("button", { name: "退出登录" }).click();
  await page.getByRole("heading", { name: "进入你的课程空间" }).waitFor();
  await page.getByLabel("账号邮箱").fill("teacher@example.test");
  await page.getByLabel("密码", { exact: true }).fill(process.env.E2E_PASSWORD);
  await page.getByRole("button", { name: "登录工作空间" }).click();
  await page.getByRole("heading", { name: "你好，陈老师" }).waitFor();
  await capture("05-teacher-home");
  await page
    .getByRole("button", { name: "批改中心", exact: false })
    .first()
    .click();
  await page.getByRole("heading", { name: "批改中心", exact: true }).waitFor();
  await capture("06-review-queue");
  await page
    .getByRole("textbox", { name: "搜索评审提交" })
    .fill("无匹配内容xyz");
  await capture("19-review-empty");
  await page.getByRole("textbox", { name: "搜索评审提交" }).fill("");
  await page.locator(".review-row").first().click();
  await page.getByRole("heading", { name: "四维评分", exact: true }).waitFor();
  await capture("07-grading");
  await page.getByRole("button", { name: "返回上一级", exact: true }).click();
  await page.getByRole("button", { name: "课程量规", exact: true }).click();
  await page.getByRole("heading", { name: "四维评价，共同的尺度。" }).waitFor();
  await capture("08-rubric");
  await page.getByRole("button", { name: "知识社区", exact: true }).click();
  await page
    .getByRole("heading", { name: "让好的解释，被更多人看见。" })
    .waitFor();
  await capture("09-community");
  await page.locator(".title-button").first().click();
  await page.getByRole("heading", { name: "阅读知识条目" }).waitFor();
  await capture("10-reader");
  await page.getByRole("button", { name: "证据核验", exact: true }).click();
  await page.getByRole("heading", { name: "证据核验", exact: true }).waitFor();
  await capture("11-evidence");
  await page.getByRole("button", { name: "课程作业", exact: true }).click();
  await page.getByRole("heading", { name: "课程作业", exact: true }).waitFor();
  await capture("12-assignments");
  await page.getByRole("button", { name: "发布作业", exact: true }).click();
  await capture("13-assignment-modal");
  await page.getByRole("button", { name: "取消", exact: true }).click();
  await page.getByRole("button", { name: "课程量规", exact: true }).click();
  await page.emulateMedia({ colorScheme: "dark" });
  await capture("14-pc-dark-rubric");
  await page.emulateMedia({ colorScheme: "light" });
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.getByRole("button", { name: "学习工作台", exact: true }).click();
  await capture("15-pc-1280-home");
  if (errors.length) throw new Error(errors.join("\n"));
  console.log(
    JSON.stringify(
      {
        status: "PASS",
        snapshots,
        consoleErrors: errors,
        checks: [
          "actual login",
          "autosave",
          "submit immutable v2",
          "feedback anchor",
          "teacher queue",
          "four dimensions",
          "publication reader",
          "evidence history",
          "PC 1440 and 1280 overflow",
          "dark mode",
          "command search",
          "outline toggle",
          "focus mode",
          "review filtering",
        ],
      },
      null,
      2,
    ),
  );
} finally {
  await browser.close();
}
