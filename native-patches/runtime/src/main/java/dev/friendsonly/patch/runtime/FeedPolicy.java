/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patch.runtime;

/** Decisions for the supported native Home-feed parser, never Stories or Direct. */
public final class FeedPolicy {
    private FeedPolicy() {}

    public static final boolean DEFAULT_FOLLOWING_ONLY = true;
    /** Missing/unknown relationship metadata is never evidence that an author is followed. */
    public static boolean keepHomeMedia(Boolean following, boolean sponsored, boolean followingOnly) {
        return !followingOnly || (!sponsored && Boolean.TRUE.equals(following));
    }
}
