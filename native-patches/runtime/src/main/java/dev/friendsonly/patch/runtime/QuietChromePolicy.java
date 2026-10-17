package dev.friendsonly.patch.runtime;

/** Exact, version-pinned surface decisions. No captions, account names or translated UI matching. */
public final class QuietChromePolicy {
    private QuietChromePolicy() {}

    /** The supported Home-only header is a FrameLayout, not the shared Direct/profile action bar. */
    public static boolean isHomeHeaderClass(String name) {
        return "instagram.features.feed.mainfeed.actionbar.MainFeedActionBar".equals(name);
    }

    /** Unknown and future tabs keep their original UI rather than receiving a misleading label. */
    public static String tabLabel(String resourceName, String language) {
        boolean tr = "tr".equals(language);
        if ("feed_tab".equals(resourceName)) return tr ? "Arkadaşlar" : "Friends";
        if ("direct_tab".equals(resourceName)) return tr ? "Sohbetler" : "Chats";
        if ("profile_tab".equals(resourceName)) return tr ? "Ben" : "Me";
        return null;
    }
}
