import assert from "node:assert/strict";
import { toMarkdown } from "../frontend/src/markdown.ts";
const p = (text, marks = []) => ({
  type: "paragraph",
  content: [{ type: "text", text, marks }],
});
const doc = (content) => ({ type: "doc", content });
assert.equal(
  toMarkdown(
    doc([
      {
        type: "heading",
        attrs: { level: 2 },
        content: [{ type: "text", text: "论点" }],
      },
      p("结论"),
    ]),
  ),
  "## 论点\n\n结论\n",
);
assert.ok(
  toMarkdown(
    doc([
      p("来源", [
        { type: "link", attrs: { href: "https://example.org/a(b)" } },
      ]),
    ]),
  ).includes("[来源](https://example.org/a%28b%29)"),
);
assert.equal(
  toMarkdown(
    doc([p("x", [{ type: "link", attrs: { href: "javascript:alert(1)" } }])]),
  ),
  "x\n",
);
assert.ok(
  toMarkdown(
    doc([
      { type: "codeBlock", content: [{ type: "text", text: "```nested" }] },
    ]),
  ).startsWith("````\n"),
);
assert.equal(
  toMarkdown(
    doc([
      {
        type: "bulletList",
        content: [
          { type: "listItem", content: [p("甲")] },
          { type: "listItem", content: [p("乙")] },
        ],
      },
    ]),
  ),
  "- 甲\n- 乙\n",
);
assert.equal(
  toMarkdown(doc([{ type: "blockquote", content: [p("引用")] }])),
  "> 引用\n",
);
assert.ok(toMarkdown(doc([p("a*b")])).includes("a\\*b"));
console.log("Markdown serializer: 7 assertions PASS");
