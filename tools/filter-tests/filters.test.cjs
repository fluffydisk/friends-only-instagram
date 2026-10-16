"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { test } = require("node:test");
const { JSDOM } = require("jsdom");

const source = fs.readFileSync(path.resolve(__dirname, "../../app/src/main/assets/filters.js"), "utf8");
const marker = "data-friends-only-filtered";
const settle = (window) => new Promise((resolve) => window.setTimeout(resolve, 180));

function fixture(t, html, pathname = "/") {
  const dom = new JSDOM(html, {
    url: "https://www.instagram.com" + pathname,
    runScripts: "outside-only"
  });
  const window = dom.window;
  t.after(() => {
    window.FriendsOnlyFilters?.disconnect();
    window.close();
  });
  window.eval(source);
  const get = (id) => window.document.getElementById(id);
  const hidden = (id) => get(id).hasAttribute(marker);
  return { window, get, hidden, filters: window.FriendsOnlyFilters };
}

test("hides only exact Instagram collection navigation, preserving media and individual post links", (t) => {
  const { filters, hidden } = fixture(t, `
    <nav>
      <a id="reels" href="/reels/">Reels</a>
      <a id="explore" href="https://m.instagram.com/explore?ref=nav">Explore</a>
      <a id="individual" href="/reel/abc123/">Friend's regular video</a>
      <a id="tag" href="/explore/tags/friends/">Tag</a>
      <a id="external" href="https://example.com/reels/">External</a>
      <a id="lookalike" href="https://instagram.com.evil.test/explore/">External</a>
      <a id="insecure" href="http://www.instagram.com/explore/">HTTP</a>
      <a id="port" href="https://www.instagram.com:8443/explore/">Other origin</a>
      <a id="media" href="/reels/"><video></video></a>
    </nav>
    <article><a id="postCollection" href="/reels/">Reels</a><video id="video"></video></article>
    <div role="dialog"><a id="dialog" href="/explore/">Explore</a></div>
  `);
  filters.configure();
  assert.equal(hidden("reels"), true);
  assert.equal(hidden("explore"), true);
  for (const id of ["individual", "tag", "external", "lookalike", "insecure", "port", "media", "postCollection", "dialog", "video"]) {
    assert.equal(hidden(id), false, id);
  }
});

test("fallback requires a Home accessible nav label, and excludes ordinary content", (t) => {
  const { filters, hidden } = fixture(t, `
    <a id="label" href="/reels/" aria-label="Reels"></a>
    <a id="icon" href="/explore/"><svg aria-label="Explore"></svg></a>
    <a id="plain" href="/reels/">Reels</a>
    <a id="wrongLabel" href="/explore/" aria-label="Find friends"></a>
    <article><a id="article" href="/reels/" aria-label="Reels"></a></article>
    <a id="image" href="/explore/" aria-label="Explore"><img></a>
  `);
  filters.configure();
  assert.equal(hidden("label"), true);
  assert.equal(hidden("icon"), true);
  for (const id of ["plain", "wrongLabel", "article", "image"]) assert.equal(hidden(id), false, id);
});

test("setting changes restore links without deleting elements or altering inline styles", (t) => {
  const { filters, hidden, get } = fixture(t, `
    <nav><a id="reels" href="/reels/" style="display:flex;color:red">Reels</a>
    <a id="explore" href="/explore/">Explore</a></nav>
  `);
  const reels = get("reels");
  filters.configure();
  filters.configure({ hideReels: false });
  assert.equal(hidden("reels"), false);
  assert.equal(hidden("explore"), true);
  assert.equal(get("reels"), reels);
  assert.equal(reels.getAttribute("style"), "display:flex;color:red");
  filters.configure({ hideExplore: false });
  assert.equal(hidden("explore"), false);
  filters.configure({ hideReels: true });
  assert.equal(hidden("reels"), true);
});

test("handles inserted navigation, href removal, and accessible icon label changes", async (t) => {
  const { filters, window, get, hidden } = fixture(t, `<nav id="nav"></nav>
    <a id="fallback" href="/reels/"><svg id="svg" aria-label="Friends"></svg></a>`);
  filters.configure();
  get("nav").innerHTML = '<a id="added" href="/reels/">Reels</a>';
  get("svg").setAttribute("aria-label", "Reels");
  await settle(window);
  assert.equal(hidden("added"), true);
  assert.equal(hidden("fallback"), true);
  get("added").removeAttribute("href");
  get("svg").setAttribute("aria-label", "Friends");
  await settle(window);
  assert.equal(hidden("added"), false);
  assert.equal(hidden("fallback"), false);
});

test("moving a hidden nav link into an article restores it", async (t) => {
  const { filters, get, hidden, window } = fixture(t, `<nav><a id="link" href="/explore/">Explore</a></nav><article id="post"></article>`);
  filters.configure();
  assert.equal(hidden("link"), true);
  get("post").appendChild(get("link"));
  await settle(window);
  assert.equal(hidden("link"), false);
});

const recommendation = (id = "suggested") => `<article id="${id}">
  <h2>Suggested for you</h2><header><a href="/someone/">Someone</a></header>
  <video></video><a href="/p/suggestedPost/">View post</a>
</article>`;

test("recommendations are opt-in and require an explicit leading label on a single post", (t) => {
  const { filters, hidden } = fixture(t, `
    ${recommendation()}
    <article id="friend"><header><a href="/friend/">Friend</a></header><video></video><a href="/reel/friend123/">Video</a></article>
    <article id="caption"><header>Friend</header><video></video><p>Suggested for you</p><a href="/p/normal/">Post</a></article>
    <article id="ambiguous"><h2>Suggested for you</h2><video></video><a href="/p/one/">Post</a></article>
    <article id="multiple"><h2>Suggested posts</h2><header><a href="/someone/">Someone</a></header><img><a href="/p/one/">One</a><a href="/p/two/">Two</a></article>
    <article id="nested"><h2>Suggested posts</h2><header><a href="/someone/">Someone</a></header>${recommendation("nestedPost")}</article>
  `);
  filters.configure();
  assert.equal(hidden("suggested"), false);
  filters.configure({ hideSuggestions: true });
  assert.equal(hidden("suggested"), true);
  for (const id of ["friend", "caption", "ambiguous", "multiple", "nested"]) assert.equal(hidden(id), false, id);
  filters.configure({ hideSuggestions: false });
  assert.equal(hidden("suggested"), false);
});

test("suggestion heading updates re-evaluate the enclosing article", async (t) => {
  const { filters, get, hidden, window } = fixture(t, recommendation());
  filters.configure({ hideSuggestions: true });
  assert.equal(hidden("suggested"), true);
  get("suggested").querySelector("h2").firstChild.data = "Friend's post";
  await settle(window);
  assert.equal(hidden("suggested"), false);
});

for (const route of ["/direct/inbox/", "/stories/friend/123/", "/friend/"]) {
  test(`preserves content, Stories, and recommendation-like message text on ${route}`, (t) => {
    const { filters, hidden } = fixture(t, `
      <nav><a id="nav" href="/reels/">Reels</a></nav>
      <a id="messageLink" href="/explore/" aria-label="Explore">Explore</a>
      <a id="story" href="/stories/friend/123/"><img></a>
      ${recommendation()}
    `, route);
    filters.configure({ hideSuggestions: true });
    assert.equal(hidden("nav"), true);
    for (const id of ["messageLink", "story", "suggested"]) assert.equal(hidden(id), false, id);
  });
}

for (const route of ["/accounts/login/", "/challenge/123/", "/checkpoint/"]) {
  test(`leaves authentication untouched on ${route}`, (t) => {
    const { filters, hidden } = fixture(t, `<nav><a id="nav" href="/explore/">Explore</a></nav>${recommendation()}`, route);
    filters.configure({ hideSuggestions: true });
    assert.equal(hidden("nav"), false);
    assert.equal(hidden("suggested"), false);
  });
}

test("route changes restore content before applying the new route's rules", async (t) => {
  const { filters, get, hidden, window } = fixture(t, `${recommendation()}<div id="changed"></div>`);
  filters.configure({ hideSuggestions: true });
  assert.equal(hidden("suggested"), true);
  window.history.pushState({}, "", "/direct/inbox/");
  get("changed").appendChild(window.document.createElement("span"));
  await settle(window);
  assert.equal(hidden("suggested"), false);
});

test("repeated evaluation/configuration uses one observer and disconnect restores/restarts", async (t) => {
  const { window, filters, get, hidden } = fixture(t, `<nav id="nav"><a id="reels" href="/reels/">Reels</a></nav>`);
  const RealObserver = window.MutationObserver;
  let creates = 0;
  let callbacks = 0;
  window.MutationObserver = class extends RealObserver {
    constructor(callback) {
      creates++;
      super((mutations) => { callbacks++; callback(mutations); });
    }
  };
  filters.configure();
  window.eval(source);
  filters.configure();
  assert.equal(window.FriendsOnlyFilters, filters);
  assert.equal(creates, 1);
  assert.equal(window.document.querySelectorAll("#friends-only-filter-style").length, 1);
  get("nav").appendChild(window.document.createElement("span"));
  await settle(window);
  const settledCallbacks = callbacks;
  await settle(window);
  assert.equal(callbacks, settledCallbacks, "own marker writes must not cause an observer loop");
  filters.disconnect();
  assert.equal(hidden("reels"), false);
  assert.equal(window.document.getElementById("friends-only-filter-style"), null);
  get("nav").innerHTML += '<a id="later" href="/explore/">Explore</a>';
  await settle(window);
  assert.equal(hidden("later"), false);
  filters.configure();
  assert.equal(creates, 2);
  assert.equal(hidden("later"), true);
});

test("a large mutation burst is batched into one bounded fallback scan", async (t) => {
  const { filters, window, get } = fixture(t, `<nav id="nav"></nav><main id="main"></main>`);
  filters.configure();
  const document = window.document;
  const originalQuery = document.querySelectorAll.bind(document);
  let documentScans = 0;
  document.querySelectorAll = (selector) => {
    if (selector === "a") documentScans++;
    return originalQuery(selector);
  };
  // Distinct mutation targets overflow the 32-root queue. The batch collapses
  // to one full scan rather than retaining/scanning hundreds of roots.
  const roots = Array.from({ length: 100 }, () => document.createElement("div"));
  roots.forEach((root) => get("main").appendChild(root));
  await settle(window);
  documentScans = 0;
  roots.forEach((root) => {
    root.innerHTML = '<a href="/reel/friendVideo/">Friend video</a>';
  });
  await settle(window);
  assert.equal(documentScans, 1);
  assert.equal(document.querySelectorAll(`[${marker}]`).length, 0);
  await settle(window);
  assert.equal(documentScans, 1, "idle document must not be polled");
});

test("restores detached hidden elements, releasing controller-owned mutations", async (t) => {
  const { filters, get, hidden, window } = fixture(t, `<nav><a id="reels" href="/reels/">Reels</a></nav>`);
  filters.configure();
  assert.equal(hidden("reels"), true);
  const removed = get("reels");
  removed.remove();
  await settle(window);
  assert.equal(removed.hasAttribute(marker), false);
});

test("text-only mutations are observed only for opt-in recommendation filtering on Home", async (t) => {
  const { filters, window, get } = fixture(t, `<p id="text">Original</p>`);
  const RealObserver = window.MutationObserver;
  const textOptions = [];
  let callbacks = 0;
  window.MutationObserver = class extends RealObserver {
    constructor(callback) {
      super((mutations) => { callbacks++; callback(mutations); });
    }
    observe(target, options) {
      textOptions.push(options.characterData);
      super.observe(target, options);
    }
  };
  filters.configure();
  get("text").firstChild.data = "Changed";
  await settle(window);
  assert.equal(callbacks, 0);
  filters.configure({ hideSuggestions: true });
  get("text").firstChild.data = "Changed again";
  await settle(window);
  assert.equal(callbacks, 1);
  window.history.pushState({}, "", "/direct/inbox/");
  filters.configure();
  get("text").firstChild.data = "Message fixture";
  await settle(window);
  assert.equal(callbacks, 1);
  assert.deepEqual(textOptions, [false, true, false]);
});

test("an early injection can configure before the document root exists", async (t) => {
  const { filters, window, hidden } = fixture(t, "");
  const document = window.document;
  document.documentElement.remove();
  assert.doesNotThrow(() => filters.configure());
  const html = document.createElement("html");
  html.innerHTML = '<head></head><body><nav><a id="early" href="/reels/">Reels</a></nav></body>';
  document.appendChild(html);
  await settle(window);
  assert.equal(hidden("early"), true);
});
