/* Premium tools: Data saver (skips images, fonts and video/audio downloads) and Hide anything you tap.
 * The app (NexPlus.kt) owns the switches. It pushes them over a native port and also answers a poll,
 * so a lapsed Premium turns everything off within a minute. Nothing here leaves the phone. */
"use strict";

const AVG_KB = { image: 45, imageset: 45, font: 35, media: 250 };

let cfg = { lite: false, hide: false };
let stats = { images: 0, fonts: 0, media: 0, kb: 0 };
let rules = {};
let port = null;

function save() {
  browser.storage.local.set({ cfg, stats, rules }).catch(() => {});
}

browser.storage.local.get(["cfg", "stats", "rules"]).then((r) => {
  if (r && r.cfg) cfg = r.cfg;
  if (r && r.stats) stats = r.stats;
  if (r && r.rules) rules = r.rules;
}).catch(() => {});

function ruleCount() {
  return Object.values(rules).reduce((n, list) => n + list.length, 0);
}

function report() {
  return { type: "stats", stats, rules: ruleCount() };
}

async function pickOnActiveTab() {
  try {
    const tabs = await browser.tabs.query({ active: true, currentWindow: true });
    const tab = tabs && tabs[0];
    if (tab && tab.id !== undefined) await browser.tabs.sendMessage(tab.id, { type: "pick" });
  } catch (e) { /* page without our content script (about: pages) */ }
}

function onAppMessage(m) {
  if (!m || typeof m !== "object") return;
  if (m.type === "config" && m.config) {
    cfg = { lite: !!m.config.lite, hide: !!m.config.hide };
    save();
  } else if (m.type === "pick") {
    pickOnActiveTab();
  } else if (m.type === "clearRules") {
    rules = {};
    save();
    if (port) port.postMessage(report());
  }
}

try {
  port = browser.runtime.connectNative("nexplus");
  port.onMessage.addListener(onAppMessage);
  port.onDisconnect.addListener(() => { port = null; });
} catch (e) { /* app side not ready; the poll covers it */ }

async function poll() {
  try {
    const reply = await browser.runtime.sendNativeMessage("nexplus", report());
    if (reply && typeof reply === "object" && "lite" in reply) {
      cfg = { lite: !!reply.lite, hide: !!reply.hide };
      save();
    }
  } catch (e) { /* ignore */ }
}
poll();
setInterval(poll, 60 * 1000);

browser.webRequest.onBeforeRequest.addListener((d) => {
  if (!cfg.lite || d.tabId < 0) return {};
  if (d.type === "image" || d.type === "imageset") stats.images++;
  else if (d.type === "font") stats.fonts++;
  else if (d.type === "media") stats.media++;
  else return {};
  stats.kb += AVG_KB[d.type] || 0;
  return { cancel: true };
}, { urls: ["<all_urls>"], types: ["image", "imageset", "font", "media"] }, ["blocking"]);

setInterval(save, 15 * 1000);

browser.runtime.onMessage.addListener((m, sender) => {
  if (!m || typeof m !== "object") return undefined;
  if (m.type === "rulesFor") {
    return Promise.resolve({ enabled: cfg.hide, selectors: cfg.hide ? (rules[m.host] || []) : [] });
  }
  if (m.type === "addRule" && cfg.hide && m.host && m.selector) {
    const list = rules[m.host] || [];
    if (!list.includes(m.selector)) list.push(m.selector);
    rules[m.host] = list.slice(-200);
    save();
    if (port) port.postMessage(report());
    return Promise.resolve({ ok: true });
  }
  if (m.type === "canPick") {
    return Promise.resolve({ ok: cfg.hide });
  }
  return undefined;
});
