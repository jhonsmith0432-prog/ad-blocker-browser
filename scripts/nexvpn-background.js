/* Browser VPN: sends page requests through the chosen Nexcraft HTTPS proxy while enabled.
 * The app (NexVpn.kt) owns the on/off state and the server; it pushes config over a native port
 * and also answers a poll, so a lapsed Premium turns routing off within a minute. */
"use strict";

let cfg = { enabled: false };
let checking = 0;

function apply(next, check) {
  const prev = cfg;
  cfg = next && typeof next === "object" ? next : { enabled: false };
  browser.storage.local.set({ cfg }).catch(() => {});
  const changed = prev.enabled !== cfg.enabled || prev.host !== cfg.host || prev.port !== cfg.port;
  if (cfg.enabled && cfg.host && (check || changed)) verify();
}

function report(state, ip) {
  browser.runtime.sendNativeMessage("nexvpn", { type: "status", state, ip: ip || "" }).catch(() => {});
}

/* Connection check: fetch Cloudflare's trace page (it echoes the caller's IP) through the proxy and
 * compare that IP with the server's address. Only the IP the site sees is reported to the app. */
async function verify() {
  const run = ++checking;
  const host = cfg.host;
  let addrs = [];
  if (/^\d+\.\d+\.\d+\.\d+$/.test(host)) addrs = [host];
  else {
    try { addrs = ((await browser.dns.resolve(host)).addresses) || []; } catch (e) { addrs = []; }
  }
  let ip = "";
  for (const url of ["https://www.cloudflare.com/cdn-cgi/trace", "https://1.1.1.1/cdn-cgi/trace"]) {
    try {
      const ctl = new AbortController();
      const t = setTimeout(() => ctl.abort(), 10000);
      const res = await fetch(url + "?nexvpn=" + Date.now(), { cache: "no-store", signal: ctl.signal });
      clearTimeout(t);
      const m = (await res.text()).match(/^ip=(.+)$/m);
      if (m) { ip = m[1].trim(); break; }
    } catch (e) { /* try the next one */ }
  }
  if (run !== checking || !cfg.enabled) return;
  report(ip && (addrs.length === 0 || addrs.includes(ip)) ? "on" : "failed", ip);
}

browser.storage.local.get("cfg").then((r) => { if (r && r.cfg) cfg = r.cfg; }).catch(() => {});

try {
  const port = browser.runtime.connectNative("nexvpn");
  port.onMessage.addListener((m) => { if (m && m.type === "config") apply(m.config, !!m.check); });
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
