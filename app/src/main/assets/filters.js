/* Friends Only: local, reversible UI filtering. No bridge, I/O, or account data storage. */
(function () {
  "use strict";

  if (window.FriendsOnlyFilters) return;

  var MARKER = "data-friends-only-filtered";
  var STYLE_ID = "friends-only-filter-style";
  var FLUSH_DELAY_MS = 120;
  var MAX_PENDING_ROOTS = 32;
  var options = { hideReels: true, hideExplore: true, hideSuggestions: false };
  var originals = new Map();
  var pending = new Set();
  var fullScan = false;
  var observer = null;
  var timer = null;
  var active = false;
  var route = "";

  function instagramUrl(href) {
    try {
      var url = new URL(href, window.location.href);
      if (url.protocol !== "https:" || url.port || url.username || url.password) return null;
      if (!["instagram.com", "www.instagram.com", "m.instagram.com"].includes(url.hostname)) return null;
      return url;
    } catch (_) {
      return null;
    }
  }

  function authenticationRoute() {
    return /^\/(accounts|challenge|checkpoint|oauth|login)(\/|$)/i.test(window.location.pathname);
  }

  function homeRoute() {
    return window.location.pathname === "/";
  }

  function ensureStyle() {
    if (document.getElementById(STYLE_ID)) return;
    var parent = document.head || document.documentElement;
    if (!parent) return; // An early host injection can precede documentElement.
    var style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = "[" + MARKER + "] { display: none !important; }";
    parent.appendChild(style);
  }

  function hide(element, reason) {
    if (!originals.has(element)) originals.set(element, element.getAttribute(MARKER));
    element.setAttribute(MARKER, reason);
  }

  function restore(element) {
    if (!originals.has(element)) return;
    var original = originals.get(element);
    if (original === null) element.removeAttribute(MARKER);
    else element.setAttribute(MARKER, original);
    originals.delete(element);
  }

  function restoreAll() {
    Array.from(originals.keys()).forEach(restore);
  }

  function navigationLabel(anchor) {
    var ownLabel = anchor.getAttribute("aria-label");
    if (ownLabel) return ownLabel.trim().toLowerCase();
    // Instagram's mobile icon links can lack a semantic nav ancestor. Only an
    // explicit icon label on Home qualifies for this conservative fallback.
    var icon = anchor.querySelector("svg[aria-label]");
    return icon ? icon.getAttribute("aria-label").trim().toLowerCase() : "";
  }

  function navigationReason(anchor) {
    if (!anchor.hasAttribute("href")) return null;
    var url = instagramUrl(anchor.getAttribute("href"));
    if (!url || anchor.querySelector("img, picture, video, audio")) return null;
    var destination;
    if (/^\/reels\/?$/.test(url.pathname) && options.hideReels) destination = "reels";
    else if (/^\/explore\/?$/.test(url.pathname) && options.hideExplore) destination = "explore";
    else return null;

    // A link within content or a dialog is never assumed to be navigation.
    if (anchor.closest("article, [role='article'], [role='dialog']")) return null;
    if (anchor.closest("nav, [role='navigation'], header")) return destination + "-navigation";
    if (homeRoute() && navigationLabel(anchor) === destination) return destination + "-navigation";
    return null;
  }

  function isProfileLink(anchor) {
    var url = instagramUrl(anchor.getAttribute("href"));
    if (!url || !/^\/[A-Za-z0-9_.]+\/$/.test(url.pathname)) return false;
    return !/^\/(accounts|direct|explore|reels|stories|about|legal|p|reel)\/$/.test(url.pathname);
  }

  function explicitRecommendation(article) {
    // This deliberately narrow, opt-in rule is not a followed-account detector.
    // The label must precede a separate author header in a single-post article.
    var heading = article.firstElementChild;
    if (!heading || !heading.matches("h1, h2, h3, h4, h5, h6, [role='heading']")) return false;
    if (!/^(Suggested posts|Suggested for you)$/i.test(heading.textContent.trim())) return false;
    var authorHeader = heading.nextElementSibling;
    if (!authorHeader || authorHeader.tagName !== "HEADER") return false;
    if (!Array.from(authorHeader.querySelectorAll("a[href]")).some(isProfileLink)) return false;
    if (article.querySelector("article, [role='article']") || !article.querySelector("img, video")) return false;
    var posts = new Set();
    article.querySelectorAll("a[href]").forEach(function (anchor) {
      var url = instagramUrl(anchor.getAttribute("href"));
      if (url && /^\/(p|reel)\/[A-Za-z0-9_-]+\/?$/.test(url.pathname)) posts.add(url.pathname.replace(/\/$/, ""));
    });
    return posts.size === 1;
  }

  function candidates(root, selector) {
    var result = [];
    if (root.nodeType === 1 && root.matches(selector)) result.push(root);
    root.querySelectorAll(selector).forEach(function (element) { result.push(element); });
    return result;
  }

  function apply(root) {
    if (authenticationRoute()) return;
    var anchors = candidates(root, "a");
    if (root.nodeType === 1) {
      var parentAnchor = root.parentElement && root.parentElement.closest("a");
      if (parentAnchor && !anchors.includes(parentAnchor)) anchors.push(parentAnchor);
    }
    anchors.forEach(function (anchor) {
      var reason = navigationReason(anchor);
      if (reason) hide(anchor, reason);
      else restore(anchor);
    });
    // Never inspect post headings on Direct, Stories, profiles, or auth pages.
    if (!options.hideSuggestions || !homeRoute()) return;
    var articles = candidates(root, "article, [role='article']");
    if (root.nodeType === 1) {
      var parentArticle = root.parentElement && root.parentElement.closest("article, [role='article']");
      if (parentArticle && !articles.includes(parentArticle)) articles.push(parentArticle);
    }
    articles.forEach(function (article) {
      if (explicitRecommendation(article)) hide(article, "explicit-recommendation");
      else restore(article);
    });
  }

  function flush() {
    timer = null;
    if (!active) return;
    ensureStyle();
    if (route !== window.location.pathname) {
      route = window.location.pathname;
      restoreAll();
      fullScan = true;
      observe();
    }
    Array.from(originals.keys()).forEach(function (element) {
      if (!element.isConnected) restore(element);
    });
    var roots = Array.from(pending);
    pending.clear();
    if (fullScan) {
      fullScan = false;
      apply(document);
    } else {
      roots.forEach(function (root) {
        if (root.isConnected) apply(root);
      });
    }
  }

  function queue(root) {
    if (!fullScan && root && root.nodeType === 1) {
      if (pending.size < MAX_PENDING_ROOTS) pending.add(root);
      else {
        pending.clear();
        fullScan = true;
      }
    }
    // Fixed delay from the first mutation: one batch per 120 ms during a burst,
    // without waiting indefinitely for an animation frame or a quiet document.
    if (timer === null) timer = window.setTimeout(flush, FLUSH_DELAY_MS);
  }

  function observe() {
    if (!observer) {
      observer = new MutationObserver(function (mutations) {
        mutations.forEach(function (mutation) {
          if (mutation.type === "characterData") {
            if (options.hideSuggestions && homeRoute()) queue(mutation.target.parentElement);
          } else if (mutation.type === "attributes") {
            queue(mutation.target);
          } else {
            mutation.addedNodes.forEach(function (node) {
              if (node.nodeType === 1) queue(node);
              else if (options.hideSuggestions && homeRoute()) queue(mutation.target);
            });
            if (mutation.removedNodes.length) {
              // Re-evaluate the content owner, not an entire body for every
              // removed post or chat item. A null root still schedules cleanup.
              var target = mutation.target;
              var owner = target.nodeType === 1 ? target.closest("a") : null;
              if (!owner && target.nodeType === 1 && options.hideSuggestions && homeRoute()) {
                owner = target.closest("article, [role='article']");
              }
              queue(owner);
            }
          }
        });
      });
    }
    // Our style/marker writes are not observed, preventing feedback loops.
    observer.observe(document, {
      subtree: true,
      childList: true,
      characterData: options.hideSuggestions && homeRoute(),
      attributes: true,
      attributeFilter: ["href", "role", "aria-label"]
    });
  }

  function configure(next) {
    next = next || {};
    ["hideReels", "hideExplore", "hideSuggestions"].forEach(function (key) {
      if (typeof next[key] === "boolean") options[key] = next[key];
    });
    active = true;
    route = window.location.pathname;
    if (timer !== null) window.clearTimeout(timer);
    timer = null;
    pending.clear();
    fullScan = false;
    restoreAll();
    ensureStyle();
    apply(document);
    observe();
  }

  function disconnect() {
    active = false;
    if (observer) observer.disconnect();
    observer = null;
    if (timer !== null) window.clearTimeout(timer);
    timer = null;
    pending.clear();
    fullScan = false;
    restoreAll();
    var style = document.getElementById(STYLE_ID);
    if (style) style.remove();
  }

  window.FriendsOnlyFilters = Object.freeze({ version: 1, configure: configure, disconnect: disconnect });
}());
