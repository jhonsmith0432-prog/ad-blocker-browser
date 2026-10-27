/* Browser VPN: sends page requests through the chosen Nexcraft HTTPS proxy while enabled.
 * The app (NexVpn.kt) owns the on/off state and the server; it pushes config over a native port
 * and also answers a poll, so a lapsed Premium turns routing off within a minute. */
"use strict";

let cfg = { enabled: false };

function apply(next) {
  cfg = next && typeof next === "object" ? next : { enabled: false };
  browser.storage.local.set({ cfg }).catch(() => {});
}

browser.storage.local.get("cfg").then((r) => { if (r && r.cfg) cfg = r.cfg; }).catch(() => {});

try {
  const port = browser.runtime.connectNative("nexvpn");
  port.onMessage.addListener((m) => { if (m && m.type === "config") apply(m.config); });
} catch (e) { /* app side not ready yet; the poll below covers it */ }

async function poll() {
  try {
    const reply = await browser.runtime.sendNativeMessage("nexvpn", { type: "get" });
    if (reply && typeof reply === "object" && "enabled" in reply) apply(reply);
  } catch (e) { /* ignore */ }
}
poll();
setInterval(poll, 60 * 1000);

const LOCAL = /^(localhost|127\.\d+\.\d+\.\d+|10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[01])\.\d+\.\d+|\[::1\])$/;

browser.proxy.onRequest.addListener((info) => {
  if (!cfg.enabled || !cfg.host) return { type: "direct" };
  let host = "";
  try { host = new URL(info.url).hostname; } catch (e) { return { type: "direct" }; }
  if (LOCAL.test(host) || host.endsWith(".local")) return { type: "direct" };
  const p = { type: "https", host: cfg.host, port: cfg.port || 443, failoverTimeout: 8 };
  if (cfg.auth) p.proxyAuthorizationHeader = "Basic " + cfg.auth;
  return p;
}, { urls: ["<all_urls>"] });

browser.proxy.onError.addListener(() => { /* keep going; Gecko reports the page error */ });
