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
assert.equal(toMarkdown(doc([{type:'mathBlock',attrs:{latex:'E=mc^2'}}])), '$$\nE=mc^2\n$$\n');
assert.ok(toMarkdown(doc([{type:'figure',attrs:{src:'/api/media/test',alt:'图[1]',caption:'示意图',source:'自绘'}}])).includes('![图\\[1\\]](/api/media/test)'));
assert.ok(toMarkdown(doc([{type:'callout',content:[p('提示内容')]}])).includes('> **提示**'));
assert.equal(toMarkdown(doc([{type:'table',content:[{type:'tableRow',content:[{type:'tableHeader',content:[p('名称')]},{type:'tableHeader',content:[p('值')]}]},{type:'tableRow',content:[{type:'tableCell',content:[p('ATP')]},{type:'tableCell',content:[p('ADP')]}]}]}])), '| 名称 | 值 |\n| --- | --- |\n| ATP | ADP |\n');
console.log("Markdown serializer: 11 assertions PASS");
