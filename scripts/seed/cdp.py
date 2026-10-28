#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""极简 CDP 客户端：启动 headless Chrome，提供 eval / 点击 / 截图。"""
import json, os, subprocess, time, urllib.request, shutil
import websocket

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
PORT = 9333


class Chrome:
    def __init__(self, width=1440, height=1000, profile="/tmp/cdp-profile"):
        shutil.rmtree(profile, ignore_errors=True)
        os.makedirs(profile, exist_ok=True)
        self.proc = subprocess.Popen([
            CHROME, "--headless=new", "--no-sandbox", "--disable-gpu",
            "--disable-dev-shm-usage", "--remote-allow-origins=*",
            f"--remote-debugging-port={PORT}", f"--user-data-dir={profile}",
            f"--window-size={width},{height}", "about:blank",
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        ws_url = None
        for _ in range(60):
            try:
                data = json.load(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list", timeout=2))
                pages = [t for t in data if t.get("type") == "page"]
                if pages:
                    ws_url = pages[0]["webSocketDebuggerUrl"]
                    break
            except Exception:
                pass
            time.sleep(0.3)
        if not ws_url:
            raise RuntimeError("无法连接 Chrome 调试端口")
        self.ws = websocket.create_connection(ws_url, timeout=180, max_size=None)
        self.mid = 0
        self.send("Page.enable")
        self.send("Runtime.enable")
        self.send("Network.enable")
        self.console = []
        self.send("Emulation.setDeviceMetricsOverride", {
            "width": width, "height": height, "deviceScaleFactor": 2, "mobile": False})

    def send(self, method, params=None):
        self.mid += 1
        self.ws.send(json.dumps({"id": self.mid, "method": method, "params": params or {}}))
        while True:
            msg = json.loads(self.ws.recv())
            if msg.get("method") == "Runtime.consoleAPICalled":
                try:
                    parts = [a.get("value", a.get("description", "")) for a in msg["params"]["args"]]
                    self.console.append(f'{msg["params"]["type"]}: ' + " ".join(str(p) for p in parts))
                except Exception:
                    pass
            if msg.get("method") == "Runtime.exceptionThrown":
                try:
                    d = msg["params"]["exceptionDetails"]
                    self.console.append("EXC: " + (d.get("exception", {}).get("description") or d.get("text", "")))
                except Exception:
                    pass
            if msg.get("id") == self.mid:
                if "error" in msg:
                    raise RuntimeError(f'{method} -> {msg["error"]}')
                return msg.get("result", {})

    def eval(self, expr, await_promise=False):
        r = self.send("Runtime.evaluate", {
            "expression": expr, "returnByValue": True, "awaitPromise": await_promise,
            "userGesture": True})
        if r.get("exceptionDetails"):
            d = r["exceptionDetails"]
            raise RuntimeError("JS 异常: " + str(d.get("exception", {}).get("description") or d.get("text")))
        return r.get("result", {}).get("value")

    def goto(self, url, wait=1.5):
        self.send("Page.navigate", {"url": url})
        time.sleep(wait)

    def wait_for(self, js, timeout=20, interval=0.3):
        end = time.time() + timeout
        while time.time() < end:
            try:
                if self.eval(js):
                    return True
            except Exception:
                pass
            time.sleep(interval)
        return False

    def click_text(self, selector, text):
        js = f"""(function(){{
          const els=[...document.querySelectorAll({json.dumps(selector)})];
          const t=els.find(e=>(e.textContent||'').replace(/\\s+/g,'').includes({json.dumps(text)}));
          if(!t) return 'no-match';
          t.scrollIntoView({{block:'center'}}); t.click(); return 'ok';
        }})()"""
        return self.eval(js)

    def shot(self, path):
        r = self.send("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": False})
        import base64
        with open(path, "wb") as f:
            f.write(base64.b64decode(r["data"]))
        return path

    def close(self):
        try:
            self.send("Browser.close")
        except Exception:
            pass
        try:
            self.proc.terminate()
        except Exception:
            pass
