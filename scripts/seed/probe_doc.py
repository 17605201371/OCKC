#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys, json
sys.path.insert(0, ".")
from seed_lib import Client

c = Client("teacher@example.test").login()
sid = int(sys.argv[1]) if len(sys.argv) > 1 else 18
sub = c.get(f"/api/submissions/{sid}")
print("keys:", list(sub.keys()))
doc = sub.get("document") or sub.get("content")
if isinstance(doc, str):
    doc = json.loads(doc)
blocks = (doc or {}).get("content", [])
print("block count:", len(blocks))
for b in blocks[:12]:
    print(b.get("type"), "| blockId=", (b.get("attrs") or {}).get("blockId"))
fb = sub.get("feedback") or []
print("feedback count:", len(fb))
for f in fb[:8]:
    print("  fb", f.get("id"), "para=", f.get("paragraph_id"), "quote=", f.get("quote_text"), "| ind=", f.get("indicator"))
# 检查 paragraphId 是否存在于 blockIds
ids = {(b.get("attrs") or {}).get("blockId") for b in blocks}
print("doc blockIds sample:", list(ids)[:5])
