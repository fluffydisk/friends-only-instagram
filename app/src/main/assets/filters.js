/* Friends Only: local, reversible presentation. No bridge, I/O, or account data storage. */
(function () {
  "use strict";

  if (window.FriendsOnlyFilters) return;

  var MARKER = "data-friends-only-filtered";
  var APPEARANCE = "data-friends-only-appearance";
  var STYLE_ID = "friends-only-filter-style";
  var APPEARANCE_STYLE_ID = "friends-only-appearance-style";
  var NAV_SELECTOR = "nav, [role='navigation']";
  var CONTENT_SELECTOR = "article, [role='article'], [role='dialog']";
  var FLUSH_DELAY_MS = 120;
  var MAX_PENDING_ROOTS = 32;
  var options = {
    hideReels: true, hideExplore: true, hideSuggestions: true,
    hideDistractions: true, minimalAppearance: true, darkMode: false, styleSheet: ""
  };
  var originals = new Map();
  var appearanceRoot = null;
  var originalAppearance = null;
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

  function appearanceRoute() {
    // Viewers, profiles, account settings, and authentication retain site styling.
    return homeRoute() || /^\/direct(?:\/|$)/.test(window.location.pathname);
  }

  function removeStyle(id) {
    var style = document.getElementById(id);
    if (style) style.remove();
  }

  function ensureStyle(id, css) {
    var parent = document.head || document.documentElement;
    if (!parent) return; // An early host injection can precede documentElement.
    var style = document.getElementById(id);
    if (!style) {
      style = document.createElement("style");
      style.id = id;
      style.textContent = css;
      parent.appendChild(style);
    } else if (style.textContent !== css) {
      style.textContent = css;
    }
  }

  function restoreAppearance() {
    if (appearanceRoot) {
      if (originalAppearance === null) appearanceRoot.removeAttribute(APPEARANCE);
      else appearanceRoot.setAttribute(APPEARANCE, originalAppearance);
    }
    appearanceRoot = null;
    originalAppearance = null;
    removeStyle(APPEARANCE_STYLE_ID);
  }

  function syncStyles() {
    if (!authenticationRoute() && (options.hideReels || options.hideExplore ||
        options.hideSuggestions || options.hideDistractions || options.minimalAppearance)) {
      ensureStyle(STYLE_ID, "[" + MARKER + "] { display: none !important; }");
    } else {
      removeStyle(STYLE_ID);
    }
    if (!options.minimalAppearance || !appearanceRoute() || !options.styleSheet || !document.documentElement) {
      restoreAppearance();
      return;
    }
    if (appearanceRoot !== document.documentElement) {
      restoreAppearance();
      appearanceRoot = document.documentElement;
      originalAppearance = appearanceRoot.getAttribute(APPEARANCE);
    }
    appearanceRoot.setAttribute(APPEARANCE, options.darkMode ? "dark" : "light");
    ensureStyle(APPEARANCE_STYLE_ID, options.styleSheet);
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

  function isProfileLink(anchor) {
    var url = instagramUrl(anchor.getAttribute("href"));
    if (!url || !/^\/[A-Za-z0-9_.]+\/$/.test(url.pathname)) return false;
    return !/^\/(accounts|direct|explore|reels|stories|about|legal|p|reel|shop|shopping)\/$/.test(url.pathname);
  }

  function coreDestination(url) {
    // A Following menu link such as /?variant=following must remain usable.
    if (url.pathname === "/" && !url.search && !url.hash) return "home";
    if (/^\/direct(?:\/inbox)?\/?$/.test(url.pathname) && !url.search && !url.hash) return "messages";
    if (/^\/reels\/?$/.test(url.pathname)) return "reels";
    if (/^\/explore\/?$/.test(url.pathname)) return "explore";
    return null;
  }

  function coherentGlobalNavigation(nav, cache) {
    if (!nav || nav.closest(CONTENT_SELECTOR + ", form")) return false;
    if (cache.has(nav)) return cache.get(nav);
    var links = Array.from(nav.querySelectorAll("a[href]"));
    var destinations = new Set();
    // A small primary-navigation group must contain all three independent
    // signals. Never classify a conversation header or a Following menu by label.
    if (links.length <= 20) {
      links.forEach(function (anchor) {
        if (anchor.closest(CONTENT_SELECTOR) || anchor.querySelector("video, audio")) return;
        var url = instagramUrl(anchor.getAttribute("href"));
        if (url) destinations.add(coreDestination(url));
      });
    }
    var result = destinations.has("home") && destinations.has("messages") &&
      (destinations.has("reels") || destinations.has("explore"));
    cache.set(nav, result);
    return result;
  }

  function navigationReason(anchor, cache) {
    if (!anchor.hasAttribute("href") || anchor.closest(CONTENT_SELECTOR)) return null;
    var url = instagramUrl(anchor.getAttribute("href"));
    if (!url) return null;
    var nav = anchor.closest(NAV_SELECTOR);
    var scope = nav || anchor.closest("header");
    var media = anchor.querySelector("img, picture, video, audio");
    var destination = coreDestination(url);
    if (!media) {
      if ((destination === "reels" && options.hideReels) || (destination === "explore" && options.hideExplore)) {
        if (scope || (homeRoute() && navigationLabel(anchor) === destination)) return destination + "-navigation";
      }
      if (options.hideDistractions && scope &&
          /^\/(create\/select|accounts\/activity|shop|shopping|explore\/people)\/?$/.test(url.pathname)) {
        return "discovery-navigation";
      }
    }
    if (!options.minimalAppearance || !appearanceRoute() || !coherentGlobalNavigation(nav, cache)) return null;
    if (!media && (destination === "home" || destination === "messages")) return "duplicate-navigation";
    // Only explicitly labelled own-profile entries in an identified global nav
    // are redundant with the native account button. Author/story avatars remain.
    if (!anchor.querySelector("video, audio") && isProfileLink(anchor) &&
        /^(profile|your profile|profil|profilin)$/.test(navigationLabel(anchor))) {
      return "duplicate-profile-navigation";
    }
    return null;
  }

  function explicitRecommendation(article) {
    // This deliberately narrow rule is not a followed-account detector.
    // The label must precede a separate author header in a single-post article.
    var heading = article.firstElementChild;
    if (!heading || !heading.matches("h1, h2, h3, h4, h5, h6, [role='heading']")) return false;
    if (!/^(Suggested posts|Suggested for you|Önerilen gönderiler|Senin için önerilenler|Senin için öneriler)$/i.test(heading.textContent.trim())) return false;
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
    var navCache = new Map();
    var anchors = candidates(root, "a");
    if (root.nodeType === 1) {
      var parentAnchor = root.parentElement && root.parentElement.closest("a");
      if (parentAnchor && !anchors.includes(parentAnchor)) anchors.push(parentAnchor);
    }
    anchors.forEach(function (anchor) {
      var reason = navigationReason(anchor, navCache);
      if (reason) hide(anchor, reason);
      else restore(anchor);
    });
    var navs = candidates(root, NAV_SELECTOR);
    navs.forEach(function (nav) {
      // Remove the now-empty wrapper only when every link was identified and
      // no independent controls/media would be removed with it. Unknown groups
      // and controls remain intact, including Following menus and composers.
      var links = Array.from(nav.querySelectorAll("a[href]"));
      var protectedContent = nav.querySelector("button, input, textarea, select, [contenteditable='true'], [role='button'], video, audio, article, [role='article'], [role='dialog']") ||
        Array.from(nav.querySelectorAll("img, picture")).some(function (media) {
          var owner = media.closest("a");
          return !owner || !originals.has(owner);
        });
      if (options.minimalAppearance && appearanceRoute() && !protectedContent &&
          coherentGlobalNavigation(nav, navCache) && links.length &&
          links.every(function (anchor) { return originals.has(anchor); })) {
        hide(nav, "duplicate-navigation-group");
      } else {
        restore(nav);
      }
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
    if (route !== window.location.pathname) {
      route = window.location.pathname;
      restoreAll();
      fullScan = true;
      observe();
    }
    syncStyles();
    Array.from(originals.keys()).forEach(function (element) {
      if (!element.isConnected || (element.getAttribute(MARKER) === "duplicate-navigation-group" &&
          !element.matches(NAV_SELECTOR))) restore(element);
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
      // A navigation's signature can change when a sibling link appears or is
      // removed. Re-evaluate that small group, never a generic page container.
      root = root.closest(NAV_SELECTOR) || root;
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

  function routeChanged() {
    if (!active) return;
    fullScan = true;
    queue(null);
  }

  function ownedStyle(node) {
    return node && node.nodeType === 1 && (node.id === STYLE_ID || node.id === APPEARANCE_STYLE_ID);
  }

  function observe() {
    if (!observer) {
      observer = new MutationObserver(function (mutations) {
        mutations.forEach(function (mutation) {
          if (ownedStyle(mutation.target)) return;
          if (mutation.type === "characterData") {
            if (options.hideSuggestions && homeRoute()) queue(mutation.target.parentElement);
          } else if (mutation.type === "attributes") {
            queue(mutation.target);
          } else {
            mutation.addedNodes.forEach(function (node) {
              if (ownedStyle(node)) return;
              if (node.nodeType === 1) queue(node);
              else if (options.hideSuggestions && homeRoute()) queue(mutation.target);
            });
            if (Array.from(mutation.removedNodes).some(function (node) { return !ownedStyle(node); })) {
              // Re-evaluate the content owner, not an entire body for every
              // removed post or chat item. A null root still schedules cleanup.
              var target = mutation.target;
              var owner = target.nodeType === 1 ? target.closest("a, " + NAV_SELECTOR) : null;
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
    observer.disconnect();
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
    ["hideReels", "hideExplore", "hideSuggestions", "hideDistractions", "minimalAppearance", "darkMode"].forEach(function (key) {
      if (typeof next[key] === "boolean") options[key] = next[key];
    });
    if (typeof next.styleSheet === "string") options.styleSheet = next.styleSheet;
    if (!active) {
      window.addEventListener("popstate", routeChanged);
      window.addEventListener("hashchange", routeChanged);
    }
    active = true;
    route = window.location.pathname;
    if (observer) observer.disconnect();
    if (timer !== null) window.clearTimeout(timer);
    timer = null;
    pending.clear();
    fullScan = false;
    restoreAll();
    syncStyles();
    apply(document);
    observe();
  }

  function disconnect() {
    active = false;
    if (observer) observer.disconnect();
    observer = null;
    window.removeEventListener("popstate", routeChanged);
    window.removeEventListener("hashchange", routeChanged);
    if (timer !== null) window.clearTimeout(timer);
    timer = null;
    pending.clear();
    fullScan = false;
    restoreAll();
    restoreAppearance();
    removeStyle(STYLE_ID);
  }

  window.FriendsOnlyFilters = Object.freeze({ version: 2, configure: configure, disconnect: disconnect });
}());
