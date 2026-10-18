package dev.friendsonly.patch.runtime;

/** Exact UI-only scopes and presentation-role precedence. No content or account-name matching. */
public final class ReferenceDesignPolicy {
    private ReferenceDesignPolicy() {}
    public static boolean storyContainer(String name) { return "X.06hD".equals(name); }
    public static boolean header(String name) {
        return "instagram.features.feed.mainfeed.actionbar.MainFeedActionBar".equals(name);
    }
    public static int merge(int previous, int next) {
        if (next < 10) return previous;
        if (previous < 10 || previous == next) return next;
        return 12; // A single native root holding media and actions is styled as a complete card.
    }
}
