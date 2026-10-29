/* Hide anything you tap: applies the user's saved rules for this site, and runs the picker when the
 * app asks ("Hide something on this page" in the menu). Runs only in the page, stores rules locally. */
"use strict";

(function () {
  const host = location.hostname;
  if (!host) return;
  let styleEl = null;

  function applyRules(selectors) {
    if (!selectors || !selectors.length) return;
    const css = selectors.map((s) => s + "{display:none!important;}").join("\n");
    if (!styleEl) {
      styleEl = document.createElement("style");
      styleEl.setAttribute("data-nex", "hide");
      (document.head || document.documentElement).appendChild(styleEl);
    }
    styleEl.textContent = css;
  }

  browser.runtime.sendMessage({ type: "rulesFor", host }).then((r) => {
    if (r && r.enabled) applyRules(r.selectors);
  }).catch(() => {});

  function cssEscape(s) {
    return window.CSS && CSS.escape ? CSS.escape(s) : s.replace(/[^a-zA-Z0-9_-]/g, "\\$&");
  }

  function selectorFor(el) {
    if (el.id && !/\d{4,}/.test(el.id)) return "#" + cssEscape(el.id);
    const parts = [];
    let node = el;
    for (let depth = 0; node && node.nodeType === 1 && node !== document.body && depth < 4; depth++) {
      let part = node.tagName.toLowerCase();
      const classes = Array.from(node.classList).filter((c) => !/\d{3,}/.test(c)).slice(0, 2);
      if (classes.length) part += "." + classes.map(cssEscape).join(".");
      const parent = node.parentElement;
      if (parent) {
        const same = Array.from(parent.children).filter((c) => c.tagName === node.tagName);
        if (same.length > 1) part += ":nth-of-type(" + (same.indexOf(node) + 1) + ")";
      }
      parts.unshift(part);
      if (node.id && !/\d{4,}/.test(node.id)) { parts[0] = "#" + cssEscape(node.id); break; }
      node = parent;
    }
    return parts.join(" > ");
  }

  let picking = false;

  function startPicker() {
    if (picking) return;
    picking = true;
    let target = null;

    const box = document.createElement("div");
    box.style.cssText = "position:fixed;z-index:2147483646;pointer-events:none;border:3px solid #3DF5B0;" +
      "background:rgba(61,245,176,.18);border-radius:6px;display:none;box-sizing:border-box;";
    const panel = document.createElement("div");
    panel.style.cssText = "position:fixed;left:0;right:0;bottom:0;z-index:2147483647;";
    const shadow = panel.attachShadow({ mode: "closed" });
    shadow.innerHTML =
      '<style>' +
      '.bar{margin:10px;padding:14px;border-radius:20px;background:#10142A;color:#F4F6FF;font:15px/1.35 system-ui,sans-serif;' +
      'box-shadow:0 8px 30px rgba(0,0,0,.45);display:flex;flex-direction:column;gap:12px}' +
      '.row{display:flex;gap:8px}button{flex:1;height:46px;border:0;border-radius:14px;font:600 15px system-ui,sans-serif}' +
      '.hide{background:#3DF5B0;color:#062018}.more{background:#1B2042;color:#F4F6FF}.cancel{background:transparent;color:#A3A9C7;border:1px solid #2A3058}' +
      'button:disabled{opacity:.4}</style>' +
      '<div class="bar"><span class="msg">Tap the thing you want gone.</span>' +
      '<div class="row"><button class="cancel">Cancel</button><button class="more" disabled>Bigger</button>' +
      '<button class="hide" disabled>Hide it</button></div></div>';
    const msg = shadow.querySelector(".msg");
    const hideBtn = shadow.querySelector(".hide");
    const moreBtn = shadow.querySelector(".more");
    const cancelBtn = shadow.querySelector(".cancel");
    document.documentElement.appendChild(box);
    document.documentElement.appendChild(panel);

    function outline(el) {
      target = el;
      const r = el.getBoundingClientRect();
      box.style.display = "block";
      box.style.left = r.left + "px";
      box.style.top = r.top + "px";
      box.style.width = r.width + "px";
      box.style.height = r.height + "px";
      hideBtn.disabled = false;
      moreBtn.disabled = !el.parentElement || el.parentElement === document.body;
      msg.textContent = "Hide this on every visit to " + location.hostname + "?";
    }

    function onTap(e) {
      if (e.composedPath && e.composedPath().includes(panel)) return;
      e.preventDefault();
      e.stopPropagation();
      const el = document.elementFromPoint(e.clientX, e.clientY);
      if (el && el !== document.documentElement && el !== document.body) outline(el);
    }

    function stop() {
      picking = false;
      document.removeEventListener("click", onTap, true);
      box.remove();
      panel.remove();
    }

    document.addEventListener("click", onTap, true);
    cancelBtn.addEventListener("click", stop);
    moreBtn.addEventListener("click", () => {
      if (target && target.parentElement && target.parentElement !== document.body) outline(target.parentElement);
    });
    hideBtn.addEventListener("click", () => {
      if (!target) return;
      const selector = selectorFor(target);
      target.style.setProperty("display", "none", "important");
      browser.runtime.sendMessage({ type: "addRule", host: location.hostname, selector }).catch(() => {});
      stop();
    });
  }

  browser.runtime.onMessage.addListener((m) => {
    if (m && m.type === "pick") {
      browser.runtime.sendMessage({ type: "canPick" }).then((r) => { if (r && r.ok) startPicker(); }).catch(() => {});
    }
    return undefined;
  });
})();
