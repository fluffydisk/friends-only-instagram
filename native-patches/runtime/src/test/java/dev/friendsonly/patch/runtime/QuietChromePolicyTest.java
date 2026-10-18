package dev.friendsonly.patch.runtime;

/** Host-side scope checks: message, Story and future action bars must retain their native UI. */
public final class QuietChromePolicyTest {
    private static int checks;

    public static void main(String[] args) {
        check(QuietChromePolicy.isHomeHeaderClass("instagram.features.feed.mainfeed.actionbar.MainFeedActionBar"));
        for (String other : new String[] {null, "", "MainFeedActionBar", "android.widget.FrameLayout",
                "com.instagram.actionbar.ActionBarTitleViewSwitcher", "instagram.features.direct.actionbar.DirectActionBar",
                "instagram.features.reels.viewer.StoryActionBar", "instagram.features.profile.actionbar.ProfileActionBar",
                "instagram.features.feed.mainfeed.actionbar.MainFeedActionBarExtra"}) {
            check(!QuietChromePolicy.isHomeHeaderClass(other));
        }
        check("Arkadaşlar".equals(QuietChromePolicy.tabLabel("feed_tab", "tr")));
        check("Sohbetler".equals(QuietChromePolicy.tabLabel("direct_tab", "tr")));
        check("Ben".equals(QuietChromePolicy.tabLabel("profile_tab", "tr")));
        check("Freunde".equals(QuietChromePolicy.tabLabel("feed_tab", "de")));
        check("Chats".equals(QuietChromePolicy.tabLabel("direct_tab", "en")));
        check("Me".equals(QuietChromePolicy.tabLabel("profile_tab", null)));
        for (String unknown : new String[] {null, "", "search_tab", "clips_tab", "creation_tab",
                "feed_tab_badge", "DIRECT_TAB", "profile_tab_icon_view", "direct_thread", "new_unknown_tab"}) {
            check(QuietChromePolicy.tabLabel(unknown, "tr") == null);
        }
        System.out.println("QuietChromePolicyTest: " + checks + " assertions passed");
    }

    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Quiet chrome policy assertion " + checks);
    }
}
