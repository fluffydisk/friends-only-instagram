package dev.friendsonly.patch.runtime;

import java.net.URI;

/** Host-side behavioral tests; no Instagram traffic, credentials or Android runtime needed. */
public final class FocusPolicyTest {
    private static int assertions;

    public static void main(String[] args) {
        check(FocusPolicy.DEFAULT_HIDE_REELS && FocusPolicy.DEFAULT_HIDE_EXPLORE
                && FocusPolicy.DEFAULT_HIDE_EXTRAS, "focus defaults");
        String[] blocked = {
                "/api/v1/clips/home/", "/clips/home/", "/api/v1/clips/discover/", "/clips/discover/",
                "/api/v1/discover/topical_explore/", "/discover/topical_explore/",
                "/api/v1/discover/explore/", "/discover/explore/"
        };
        for (String path : blocked) {
            check(block("https://i.instagram.com" + path), "known recommendation collection " + path);
            check(block("https://instagram.com" + path.substring(0, path.length() - 1)), "optional slash " + path);
            check(block("HTTPS://I.INSTAGRAM.COM" + path + "?rank_token=test#section"), "case-insensitive origin/query ignored");
            check(!FocusPolicy.shouldBlock(URI.create("https://i.instagram.com" + path), false, false), "filters off " + path);
            check(!block("https://i.instagram.com" + path + "another/"), "unknown child fails open " + path);
            check(!block("https://i.instagram.com" + path.substring(0, path.length() - 1) + "ish/"), "path boundary " + path);
        }
        String[] essential = {
                "/api/v1/direct_v2/inbox/", "/api/v1/direct_v2/threads/123/",
                "/api/v1/direct_v2/threads/broadcast/text/", "/api/v1/direct_v2/create_group_thread/",
                "/api/v1/feed/reels_tray/", "/api/v1/feed/reels_media/", "/api/v1/feed/timeline/",
                "/api/v1/feed/user/123/", "/api/v1/feed/only_me_feed/", "/api/v1/media/123/info/",
                "/api/v1/media/123/like/", "/api/v1/media/seen/", "/api/v1/clips/item/",
                "/api/v1/clips/metadata/", "/api/v1/users/123/info/", "/api/v1/friendships/123/follow/",
                "/accounts/login/", "/api/v1/accounts/login/", "/api/v1/accounts/two_factor_login/",
                "/challenge/", "/api/v1/challenge/", "/api/v1/accounts/current_user/",
                "/api/v1/feed/timeline/?next=/api/v1/clips/home/",
                "/api/v1/direct_v2/threads/1/?message=reels&url=/discover/explore/",
                "/api/v1/clips/home/../item/", "/api/v1/clips/%68ome/", "/api/v1/clips%2Fhome/"
        };
        for (String path : essential) check(!block("https://i.instagram.com" + path), "essential/unknown endpoint preserved " + path);
        String[] otherOrigins = {
                "http://i.instagram.com", "ftp://instagram.com", "https://instagram.com.evil.example",
                "https://evilinstagram.com", "https://evil.example", "https://instagram.com.",
                "https://user@instagram.com", "https://instagram_com", "https://127.0.0.1",
                "https://[::1]", "https://instagram.com@evil.example", "https://i..instagram.com",
                "https://-a.instagram.com", "https://a-.instagram.com", "https://a_b.instagram.com"
        };
        for (String origin : otherOrigins) check(!block(origin + "/api/v1/clips/home/"), "untrusted/uncertain origin fails open " + origin);
        check(!FocusPolicy.shouldBlock(null, true, true), "null request fails open");
        check(!block("/api/v1/clips/home/"), "relative request fails open");
        check(!block("https://instagram.com"), "empty path fails open");
        check(FocusPolicy.shouldBlock(URI.create("https://instagram.com/clips/home/"), true, false), "Reels independent");
        check(!FocusPolicy.shouldBlock(URI.create("https://instagram.com/clips/home/"), false, true), "Reels switch respected");
        check(FocusPolicy.shouldBlock(URI.create("https://instagram.com/discover/explore/"), false, true), "Explore independent");
        check(!FocusPolicy.shouldBlock(URI.create("https://instagram.com/discover/explore/"), true, false), "Explore switch respected");
        check(FocusPolicy.shouldHideTab("clips_tab", true, false, false), "hide exact Reels tab");
        check(FocusPolicy.shouldHideTab("search_tab", false, true, false), "hide exact Explore/Search tab");
        check(FocusPolicy.shouldHideTab("creation_tab", false, false, true), "hide exact Create tab");
        String[] preservedTabs = {"feed_tab", "direct_tab", "profile_tab", "story_viewer", "reel_video", "cf_hub_recycler_view", "clips_tab_badge", "", null};
        for (String tab : preservedTabs) check(!FocusPolicy.shouldHideTab(tab, true, true, true), "unrelated native view preserved " + tab);
        check(!FocusPolicy.shouldHideTab("clips_tab", false, true, true), "Reels tab toggle");
        check(!FocusPolicy.shouldHideTab("search_tab", true, false, true), "Explore tab toggle");
        check(!FocusPolicy.shouldHideTab("creation_tab", true, true, false), "Create tab toggle");
        System.out.println("FocusPolicyTest: " + assertions + " assertions passed");
    }

    private static boolean block(String uri) { return FocusPolicy.shouldBlock(URI.create(uri), true, true); }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}
