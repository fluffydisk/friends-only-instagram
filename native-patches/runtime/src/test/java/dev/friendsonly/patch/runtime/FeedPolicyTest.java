/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patch.runtime;

/** Behavioral host tests for strict scope-boundary decisions and the actual public-getter hook. */
public final class FeedPolicyTest {
    private static int assertions;

    public static void main(String[] args) {
        check(FeedPolicy.DEFAULT_FOLLOWING_ONLY, "strict mode defaults on before Android config exists");
        check(FeedHooks.isFollowingOnlyEnabled(), "runtime is strict before initialization");
        for (Boolean following : new Boolean[]{Boolean.TRUE, Boolean.FALSE, null}) {
            for (boolean sponsored : new boolean[]{false, true}) {
                check(FeedPolicy.keepHomeMedia(following, sponsored, true)
                        == (Boolean.TRUE.equals(following) && !sponsored), "strict relationship/ad matrix");
                check(FeedPolicy.keepHomeMedia(following, sponsored, false), "disabled preserves every original post");
            }
        }
        Media followed = media(Boolean.TRUE, Boolean.FALSE, false);
        check(FeedHooks.filterHomeMedia(followed) == followed, "keeps identical followed post object");
        check(FeedHooks.filterHomeMedia(media(Boolean.FALSE, Boolean.TRUE, false)) == null,
                "a follower whom we do not follow is blocked; CE9 is not used");
        check(FeedHooks.filterHomeMedia(media(Boolean.TRUE, Boolean.FALSE, false)) != null,
                "mutual following is not required");
        check(FeedHooks.filterHomeMedia(media(null, Boolean.TRUE, false)) == null,
                "unknown following stays hidden even if author follows us");
        check(FeedHooks.filterHomeMedia(media(Boolean.FALSE, Boolean.FALSE, false)) == null,
                "recommended ordinary-media wrapper is blocked");
        check(FeedHooks.filterHomeMedia(media(Boolean.TRUE, Boolean.TRUE, true)) == null,
                "sponsored media is blocked even if its author is followed");
        check(FeedHooks.filterHomeMedia(null) == null, "null media stays null");
        check(FeedHooks.filterHomeMedia(new Media(null, null)) == null, "missing primary author stays hidden");
        check(FeedHooks.filterHomeMedia(new Media(new User(null), null)) == null,
                "missing friendship metadata stays hidden");
        check(FeedHooks.filterHomeMedia(new Object()) == null, "unsupported model stays hidden");
        check(FeedHooks.filterHomeMedia(new ThrowingMedia(new User(new PrivateRelationship(true, false)), null)) == null,
                "getter exceptions fail closed without crashing");
        check(FeedHooks.filterHomeMedia(followed) == followed, "contract can recover after unsupported model");
        Media partnership = new Media(new User(new PrivateRelationship(true, false)), null);
        check(partnership.A5W() && FeedHooks.filterHomeMedia(partnership) == partnership,
                "ordinary followed paid-partnership post is not automatically an injected ad");
        FeedHooks.setFollowingOnly(false);
        Object unsupported = new Object();
        check(!FeedHooks.isFollowingOnlyEnabled(), "cached toggle updates immediately");
        check(FeedHooks.filterHomeMedia(unsupported) == unsupported, "disabled does not inspect arbitrary model");
        Media recommended = media(false, true, true);
        check(FeedHooks.filterHomeMedia(recommended) == recommended, "disabled restores sponsored/unfollowed original object");
        FeedHooks.setFollowingOnly(true);
        check(FeedHooks.filterHomeMedia(recommended) == null, "reenabling restores strict decision");
        check(FeedHooks.filterHomeMedia(followed) == followed, "cached public interface works with private implementation");
        System.out.println("FeedPolicyTest: " + assertions + " assertions passed");
    }

    private static Media media(Boolean following, Boolean followedBy, boolean sponsored) {
        return new Media(new User(new PrivateRelationship(following, followedBy)), sponsored ? new Object() : null);
    }

    public interface Relationship {
        Boolean CEK();
        Boolean CE9();
    }

    private static final class PrivateRelationship implements Relationship {
        private final Boolean following;
        private final Boolean followedBy;
        PrivateRelationship(Boolean following, Boolean followedBy) {
            this.following = following;
            this.followedBy = followedBy;
        }
        @Override public Boolean CEK() { return following; }
        @Override public Boolean CE9() { return followedBy; }
    }

    public static final class User {
        private final Relationship relationship;
        public User(Relationship relationship) { this.relationship = relationship; }
        public Relationship A1E() { return relationship; }
    }

    public static class Media {
        private final User author;
        private final Object injected;
        public Media(User author, Object injected) { this.author = author; this.injected = injected; }
        public User A3U() { return author; }
        public Object A3F() { return injected; }
        public boolean A5W() { return true; }
    }

    public static final class ThrowingMedia extends Media {
        public ThrowingMedia(User author, Object injected) { super(author, injected); }
        @Override public User A3U() { throw new IllegalStateException("missing native metadata"); }
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}
