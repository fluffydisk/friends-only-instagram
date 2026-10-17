package dev.friendsonly.patch.runtime;

import java.net.URI;
import java.util.Locale;

/** Pure decisions shared by the injected hooks and host-side tests. */
public final class FocusPolicy {
    public static final boolean DEFAULT_HIDE_REELS = true;
    public static final boolean DEFAULT_HIDE_EXPLORE = true;
    public static final boolean DEFAULT_HIDE_EXTRAS = true;
    public static final boolean DEFAULT_BLOCK_FEED_REELS_OPEN = true;

    private static final String[] REELS_PATHS = {
            "/api/v1/clips/home/", "/clips/home/",
            "/api/v1/clips/discover/", "/clips/discover/"
    };
    private static final String[] EXPLORE_PATHS = {
            "/api/v1/discover/topical_explore/", "/discover/topical_explore/",
            "/api/v1/discover/explore/", "/discover/explore/"
    };

    private FocusPolicy() {}

    /**
     * Only known recommendation collection endpoints are eligible. Unknown requests fail open.
     * The raw path is deliberately not decoded, normalized or matched by substring. An individual
     * clip, ordinary video, story, message, authentication request or query never matches here.
     */
    public static boolean shouldBlock(URI request, boolean hideReels, boolean hideExplore) {
        if (request == null || !"https".equalsIgnoreCase(request.getScheme())
                || request.getRawUserInfo() != null || !isInstagramHost(request.getHost())) {
            return false;
        }
        String path = request.getRawPath();
        return (hideReels && matchesExactEndpoint(path, REELS_PATHS))
                || (hideExplore && matchesExactEndpoint(path, EXPLORE_PATHS));
    }

    public static boolean shouldHideTab(String resourceName, boolean hideReels,
            boolean hideExplore, boolean hideExtras) {
        return (hideReels && "clips_tab".equals(resourceName))
                || (hideExplore && "search_tab".equals(resourceName))
                || (hideExtras && "creation_tab".equals(resourceName));
    }

    /**
     * Stop only supported Home-feed transitions into the Reels viewer. Inline media, Stories,
     * conversations, profiles and unknown launch sources remain outside this navigation guard.
     * This choice is independent of hiding the Reels navigation tab.
     */
    public static boolean shouldBlockFeedReelsOpen(String sourceName, boolean enabled) {
        return enabled && ("FEED_TIMELINE".equals(sourceName)
                || "FEED_TIMELINE_POST_TAP".equals(sourceName)
                || "FEED_TIMELINE_FOLLOWING".equals(sourceName)
                || "FEED_TIMELINE_FAVORITES".equals(sourceName)
                || "FEED_TIMELINE_ALL".equals(sourceName)
                || "FEED_TIMELINE_MIXED_UNCONNECTED".equals(sourceName));
    }

    private static boolean matchesExactEndpoint(String path, String[] endpoints) {
        if (path == null) return false;
        for (String endpoint : endpoints) {
            if (endpoint.equals(path)
                    || endpoint.substring(0, endpoint.length() - 1).equals(path)) return true;
        }
        return false;
    }

    private static boolean isInstagramHost(String candidate) {
        if (candidate == null || candidate.length() > 253) return false;
        String host = candidate.toLowerCase(Locale.ROOT);
        if (!(host.equals("instagram.com") || host.endsWith(".instagram.com"))) return false;
        for (String label : host.split("\\.", -1)) {
            if (label.length() == 0 || label.length() > 63
                    || label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') {
                return false;
            }
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) {
                    return false;
                }
            }
        }
        return true;
    }
}
