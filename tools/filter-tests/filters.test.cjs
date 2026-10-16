"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { test } = require("node:test");
const { JSDOM } = require("jsdom");

const source = fs.readFileSync(path.resolve(__dirname, "../../app/src/main/assets/filters.js"), "utf8");
const appearanceCss = fs.readFileSync(path.resolve(__dirname, "../../app/src/main/assets/focus.css"), "utf8");
const marker = "data-friends-only-filtered";
const appearanceMarker = "data-friends-only-appearance";
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

test("recommendations can be disabled and require an explicit leading label on a single post", (t) => {
  const { filters, hidden } = fixture(t, `
    ${recommendation()}
    <article id="friend"><header><a href="/friend/">Friend</a></header><video></video><a href="/reel/friend123/">Video</a></article>
    <article id="caption"><header>Friend</header><video></video><p>Suggested for you</p><a href="/p/normal/">Post</a></article>
    <article id="ambiguous"><h2>Suggested for you</h2><video></video><a href="/p/one/">Post</a></article>
    <article id="multiple"><h2>Suggested posts</h2><header><a href="/someone/">Someone</a></header><img><a href="/p/one/">One</a><a href="/p/two/">Two</a></article>
    <article id="nested"><h2>Suggested posts</h2><header><a href="/someone/">Someone</a></header>${recommendation("nestedPost")}</article>
  `);
  filters.configure({ hideSuggestions: false });
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

test("text-only mutations are observed only with recommendation filtering enabled on Home", async (t) => {
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
  filters.configure({ hideSuggestions: false });
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

const globalNavigation = `<nav id="global">
  <a id="home" href="/" aria-label="Home"><svg></svg></a>
  <a id="messages" href="/direct/inbox/" aria-label="Messages"><svg></svg></a>
  <a id="reels" href="/reels/" aria-label="Reels"><svg></svg></a>
  <a id="explore" href="/explore/" aria-label="Explore"><svg></svg></a>
  <a id="profile" href="/myself/" aria-label="Profile"><img></a>
  <a id="create" href="/create/select/" aria-label="New post"><svg></svg></a>
  <a id="notifications" href="/accounts/activity/" aria-label="Notifications"><svg></svg></a>
</nav>`;

test("minimal appearance removes only a coherent duplicate global navigation", (t) => {
  const { filters, hidden, get } = fixture(t, `${globalNavigation}
    <header id="followingHeader"><button id="followingMenu">Following</button><a id="following" href="/?variant=following">Following</a></header>
    <div role="navigation" id="profileControls"><a id="friendProfile" href="/friend/" aria-label="Profile"><img></a><button id="follow">Follow</button></div>
    <main><article id="friendPost"><header><a id="author" href="/friend/">Friend</a></header><video id="friendVideo"></video><a id="permalink" href="/reel/ordinaryVideo/">Video</a><button id="like">Like</button></article></main>
    <a id="story" href="/stories/friend/123/"><img></a>`);
  filters.configure({ styleSheet: appearanceCss });
  for (const id of ["global", "home", "messages", "reels", "explore", "profile", "create", "notifications"]) assert.equal(hidden(id), true, id);
  for (const id of ["followingHeader", "followingMenu", "following", "profileControls", "friendProfile", "follow", "friendPost", "author", "friendVideo", "permalink", "like", "story"]) assert.equal(hidden(id), false, id);
  assert.equal(get("friendVideo").tagName, "VIDEO");
  filters.configure({ minimalAppearance: false });
  for (const id of ["global", "home", "messages", "profile"]) assert.equal(hidden(id), false, id);
  assert.equal(hidden("reels"), true, "the separate Reels filter remains enabled");
});

test("unknown nav controls and Following routes preserve the navigation wrapper", (t) => {
  const { filters, hidden } = fixture(t, `${globalNavigation.replace("</nav>", '<button id="followingButton">Following</button><a id="following" href="/?variant=following">Following</a><a id="unknown" href="/some-new-feature/">New feature</a><a id="folder" href="/direct/inbox/?folder=requests">Requests</a><a id="inboxHash" href="/direct/inbox/#requests">Requests</a></nav>')}`);
  filters.configure({ styleSheet: appearanceCss });
  for (const id of ["global", "followingButton", "following", "unknown", "folder", "inboxHash"]) assert.equal(hidden(id), false, id);
  assert.equal(hidden("home"), true);
});

test("standalone navigation media survives wrapper cleanup", (t) => {
  const { filters, hidden } = fixture(t, globalNavigation.replace("</nav>", '<img id="storyImage"><picture id="storyPicture"></picture></nav>'));
  filters.configure({ styleSheet: appearanceCss });
  for (const id of ["global", "storyImage", "storyPicture"]) assert.equal(hidden(id), false, id);
});

test("discovery links require exact targets and a navigation context", (t) => {
  const { filters, hidden } = fixture(t, `<nav>
    <a id="create" href="/create/select/">Create</a>
    <a id="activity" href="/accounts/activity/">Activity</a>
    <a id="shop" href="/shop/">Shop</a>
    <a id="shopping" href="https://www.instagram.com/shopping?ref=nav">Shopping</a>
    <a id="people" href="/explore/people/">Find people</a>
    <a id="shopItem" href="/shop/item/123/">Shared product</a>
    <a id="createOther" href="/create/story/">Story</a>
    <a id="followingList" href="/friend/following/">Following</a>
    <a id="externalShop" href="https://example.com/shop/">External shop</a>
    <a id="mediaShop" href="/shop/"><img></a></nav>
    <a id="outside" href="/explore/people/">People</a>
    <article><header><a id="postCreate" href="/create/select/">Create</a></header></article>
    <div role="dialog"><nav><a id="dialogCreate" href="/create/select/">Create</a></nav></div>`);
  filters.configure();
  for (const id of ["create", "activity", "shop", "shopping", "people"]) assert.equal(hidden(id), true, id);
  for (const id of ["shopItem", "createOther", "followingList", "externalShop", "mediaShop", "outside", "postCreate", "dialogCreate"]) assert.equal(hidden(id), false, id);
  filters.configure({ hideDistractions: false });
  for (const id of ["create", "activity", "shop", "shopping", "people"]) assert.equal(hidden(id), false, id);
});

test("Direct keeps its conversation header, shared media, group controls, and composer", (t) => {
  const { filters, hidden, get, window } = fixture(t, `${globalNavigation}
    <header id="threadHeader"><a id="person" href="/friend/" aria-label="Profile"><img></a><button id="groupDetails">Group details</button></header>
    <main><div id="chat"><a id="sharedReel" href="/reel/friend123/"><video id="sharedVideo"></video></a><p id="message">Suggested for you</p></div></main>
    <form id="composer"><textarea id="input"></textarea><button id="send">Send</button></form>`, "/direct/t/group123/");
  filters.configure({ styleSheet: appearanceCss });
  assert.equal(window.document.documentElement.getAttribute(appearanceMarker), "light");
  assert.equal(hidden("global"), true);
  for (const id of ["threadHeader", "person", "groupDetails", "chat", "sharedReel", "sharedVideo", "message", "composer", "input", "send"]) assert.equal(hidden(id), false, id);
  assert.equal(get("input").getAttribute("style"), null);
  assert.equal(get("composer").getAttribute("style"), null);
});

test("appearance CSS is idempotent, respects theme changes, and leaves inline content styles alone", (t) => {
  const { filters, get, window } = fixture(t, `<main><article id="post" style="display:flex"><video id="video" style="width:100%"></video></article></main>`);
  const root = window.document.documentElement;
  filters.configure({ styleSheet: appearanceCss, darkMode: false });
  filters.configure({ styleSheet: appearanceCss, darkMode: false });
  assert.equal(root.getAttribute(appearanceMarker), "light");
  assert.equal(window.document.querySelectorAll("#friends-only-appearance-style").length, 1);
  assert.equal(window.getComputedStyle(root).getPropertyValue("--ig-primary-background").trim(), "248, 249, 246");
  assert.equal(window.getComputedStyle(get("post")).borderRadius, "18px");
  assert.equal(window.getComputedStyle(get("video")).width, "100%");
  assert.equal(window.getComputedStyle(get("video")).height, "");
  assert.equal(window.getComputedStyle(get("post")).overflow, "");
  filters.configure({ darkMode: true });
  assert.equal(root.getAttribute(appearanceMarker), "dark");
  assert.equal(window.getComputedStyle(root).getPropertyValue("--ig-primary-background").trim(), "20, 25, 22");
  assert.equal(get("post").getAttribute("style"), "display:flex");
  assert.equal(get("video").getAttribute("style"), "width:100%");
});

test("disabling all settings restores every app-owned marker, style, and preexisting attribute", (t) => {
  const { filters, window, get, hidden } = fixture(t, `${globalNavigation}${recommendation()}`);
  const root = window.document.documentElement;
  root.setAttribute(appearanceMarker, "site-original");
  get("reels").setAttribute(marker, "site-original");
  filters.configure({ styleSheet: appearanceCss });
  assert.equal(hidden("global"), true);
  filters.configure({ minimalAppearance: false, hideReels: false, hideExplore: false, hideDistractions: false, hideSuggestions: false });
  assert.equal(root.getAttribute(appearanceMarker), "site-original");
  assert.equal(get("reels").getAttribute(marker), "site-original");
  assert.equal(window.document.querySelectorAll(`[${marker}]`).length, 1);
  assert.equal(window.document.getElementById("friends-only-filter-style"), null);
  assert.equal(window.document.getElementById("friends-only-appearance-style"), null);
  assert.equal(hidden("global"), false);
  filters.disconnect();
  assert.equal(root.getAttribute(appearanceMarker), "site-original");
});

for (const route of ["/accounts/login/", "/accounts/edit/", "/challenge/123/", "/checkpoint/", "/oauth/authorize/", "/login/"]) {
  test(`appearance and filter styles leave authentication/account route untouched: ${route}`, (t) => {
    const { filters, window, hidden, get } = fixture(t, `${globalNavigation}<form><input id="password" type="password" style="color:blue"></form>${recommendation()}`, route);
    const original = window.document.documentElement.outerHTML;
    filters.configure({ styleSheet: appearanceCss });
    assert.equal(window.document.documentElement.outerHTML, original);
    assert.equal(hidden("global"), false);
    assert.equal(hidden("suggested"), false);
    assert.equal(get("password").getAttribute("style"), "color:blue");
  });
}

for (const route of ["/stories/friend/123/", "/reel/friend123/", "/p/friend123/", "/friend/"]) {
  test(`viewer/profile route retains its presentation: ${route}`, (t) => {
    const { filters, window, hidden } = fixture(t, `<main><article id="content"><video></video></article></main>`, route);
    filters.configure({ styleSheet: appearanceCss });
    assert.equal(window.document.documentElement.hasAttribute(appearanceMarker), false);
    assert.equal(window.document.getElementById("friends-only-appearance-style"), null);
    assert.equal(hidden("content"), false);
  });
}

test("SPA route changes remove the theme on auth/Stories and restore it on Home", async (t) => {
  const { filters, window, get, hidden } = fixture(t, `${globalNavigation}<div id="updates"></div>`);
  filters.configure({ styleSheet: appearanceCss });
  assert.equal(hidden("global"), true);
  window.history.pushState({}, "", "/accounts/login/");
  get("updates").appendChild(window.document.createElement("span"));
  await settle(window);
  assert.equal(hidden("global"), false);
  assert.equal(window.document.documentElement.hasAttribute(appearanceMarker), false);
  assert.equal(window.document.getElementById("friends-only-appearance-style"), null);
  assert.equal(window.document.getElementById("friends-only-filter-style"), null);
  window.history.pushState({}, "", "/stories/friend/123/");
  window.dispatchEvent(new window.PopStateEvent("popstate"));
  await settle(window);
  assert.equal(window.document.documentElement.hasAttribute(appearanceMarker), false);
  assert.equal(hidden("home"), false);
  window.history.pushState({}, "", "/");
  filters.configure(); // Host's doUpdateVisitedHistory reconfigures even without DOM mutation.
  assert.equal(window.document.documentElement.getAttribute(appearanceMarker), "light");
  assert.equal(hidden("global"), true);
});

test("inserting/removing a global-nav signature re-evaluates its sibling links", async (t) => {
  const { filters, get, hidden, window } = fixture(t, `<nav id="global"><a id="home" href="/">Home</a><a id="reels" href="/reels/">Reels</a></nav>`);
  filters.configure({ styleSheet: appearanceCss });
  assert.equal(hidden("home"), false);
  get("global").insertAdjacentHTML("beforeend", '<a id="messages" href="/direct/inbox/">Messages</a>');
  await settle(window);
  assert.equal(hidden("home"), true);
  assert.equal(hidden("global"), true);
  get("messages").remove();
  await settle(window);
  assert.equal(hidden("home"), false);
  assert.equal(hidden("global"), false);
});

test("English/Turkish explicit recommendation headings work while ordinary captions and unknown labels stay", (t) => {
  const { filters, hidden } = fixture(t, `${recommendation("english")}
    ${recommendation("turkish").replace("Suggested for you", "Senin için önerilenler")}
    ${recommendation("turkishPosts").replace("Suggested for you", "Önerilen gönderiler")}
    ${recommendation("unknown").replace("Suggested for you", "A friend's recommendation")}
    <article id="caption"><header><a href="/friend/">Friend</a></header><img><p>Senin için önerilenler</p><a href="/p/caption/">Post</a></article>`);
  filters.configure();
  for (const id of ["english", "turkish", "turkishPosts"]) assert.equal(hidden(id), true, id);
  for (const id of ["unknown", "caption"]) assert.equal(hidden(id), false, id);
});

test("a hidden navigation wrapper restores when its role is removed and it becomes conversation content", async (t) => {
  const html = globalNavigation.replace('<nav id="global">', '<div id="global" role="navigation">').replace('</nav>', '</div>');
  const { filters, window, get, hidden } = fixture(t, html, "/direct/t/group123/");
  filters.configure({ styleSheet: appearanceCss });
  assert.equal(hidden("global"), true);
  get("global").removeAttribute("role");
  get("global").innerHTML = '<p id="message">Normal group conversation</p><textarea id="composer"></textarea>';
  await settle(window);
  for (const id of ["global", "message", "composer"]) assert.equal(hidden(id), false, id);
});
