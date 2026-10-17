/** Export only document text. Course feedback, evidence and version metadata remain in the platform. */
export function toMarkdown(doc: any): string {
  const children = (n: any) => (n.content || []).map(inline).join("");
  function inline(n: any): string {
    if (n.type === "hardBreak") return "  \n";
    if (n.type !== "text") return children(n);
    let text = String(n.text || "").replace(/([\\`*_\[\]<>])/g, "\\$1");
    for (const m of n.marks || []) {
      if (m.type === "code") {
        const fence = "`".repeat(
          Math.max(
            0,
            ...(String(n.text).match(/`+/g) || []).map((v: string) => v.length),
          ) + 1,
        );
        text = `${fence} ${n.text} ${fence}`;
      } else if (m.type === "bold") text = `**${text}**`;
      else if (m.type === "italic") text = `*${text}*`;
      else if (m.type === "strike") text = `~~${text}~~`;
      else if (m.type === "link" && /^https?:\/\//i.test(m.attrs?.href || ""))
        text = `[${text}](${String(m.attrs.href).replace(/ /g, "%20").replace(/\(/g, "%28").replace(/\)/g, "%29")})`;
    }
    return text;
  }
  function block(n: any): string {
    const inner = (n.content || []).map(block).join("\n\n");
    switch (n.type) {
      case "doc":
        return inner;
      case "paragraph":
        return children(n).replace(/^(#{1,6}\s|>\s|[-+]\s|\d+\.\s)/, "\\$1");
      case "heading":
        return (
          "#".repeat(Math.min(6, Math.max(1, n.attrs?.level || 2))) +
          " " +
          children(n)
        );
      case "blockquote":
        return inner
          .split("\n")
          .map((l: string) => "> " + l)
          .join("\n");
      case "bulletList":
      case "orderedList":
        return (n.content || [])
          .map((li: any, i: number) => {
            const prefix =
              n.type === "orderedList"
                ? `${(n.attrs?.start || 1) + i}. `
                : "- ";
            return (
              prefix +
              block(li)
                .split("\n")
                .join("\n" + " ".repeat(prefix.length))
            );
          })
          .join("\n");
      case "listItem":
        return inner;
      case "codeBlock": {
        const text = (n.content || []).map((c: any) => c.text || "").join("");
        const fence = "`".repeat(
          Math.max(
            2,
            ...(text.match(/`+/g) || []).map((v: string) => v.length),
          ) + 1,
        );
        return `${fence}${String(n.attrs?.language || "").replace(/[^\w+-]/g, "")}\n${text}\n${fence}`;
      }
      case "horizontalRule":
        return "---";
      default:
        return children(n);
    }
  }
  return block(doc).trim() + "\n";
}
