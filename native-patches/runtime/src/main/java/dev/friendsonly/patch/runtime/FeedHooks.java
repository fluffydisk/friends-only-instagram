/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patch.runtime;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Runs only at the native Home-feed item boundary. Reads existing public model getters;
 * it neither obtains a session nor issues requests, traverses accounts, logs or persists data.
 * Getter identities and their native field semantics are verified by the version-pinned patch.
 */
public final class FeedHooks {
    private static volatile boolean followingOnly = FeedPolicy.DEFAULT_FOLLOWING_ONLY;
    private static volatile Contract cachedContract;

    private FeedHooks() {}

    public static void setFollowingOnly(boolean enabled) { followingOnly = enabled; }
    public static boolean isFollowingOnlyEnabled() { return followingOnly; }

    /**
     * Returns the same media object, or null for a non-followed, sponsored or unverified author.
     * Public getters are cached as a single immutable contract; no reflection over private fields.
     * The RelationshipInfoDict interface method is used, so package-private implementations work.
     */
    public static Object filterHomeMedia(Object media) {
        if (!followingOnly || media == null) return media;
        try {
            Contract contract = cachedContract;
            if (contract == null || !contract.mediaClass.isInstance(media)) {
                contract = new Contract(media.getClass());
                cachedContract = contract;
            }
            if (contract.injected.invoke(media) != null) return null;
            Object author = contract.author.invoke(media);
            if (author == null) return null;
            Object relationship = contract.relationship.invoke(author);
            if (relationship == null) return null;
            Object following = contract.following.invoke(relationship);
            return FeedPolicy.keepHomeMedia(following instanceof Boolean ? (Boolean) following : null,
                    false, true) ? media : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Strict mode must never turn missing/changed metadata into an allowed recommendation.
            return null;
        }
    }

    private static final class Contract {
        final Class<?> mediaClass;
        final Method author;
        final Method injected;
        final Method relationship;
        final Method following;

        Contract(Class<?> mediaClass) throws NoSuchMethodException {
            this.mediaClass = mediaClass;
            author = publicGetter(mediaClass, "A3U");
            injected = publicGetter(mediaClass, "A3F");
            relationship = publicGetter(author.getReturnType(), "A1E");
            following = publicGetter(relationship.getReturnType(), "CEK");
            if (following.getReturnType() != Boolean.class) {
                throw new NoSuchMethodException("Unsupported Home relationship getter");
            }
        }

        private static Method publicGetter(Class<?> owner, String name) throws NoSuchMethodException {
            Method method = owner.getMethod(name);
            if (!Modifier.isPublic(owner.getModifiers()) || !Modifier.isPublic(method.getModifiers())
                    || Modifier.isStatic(method.getModifiers()) || method.getParameterTypes().length != 0) {
                throw new NoSuchMethodException("Unsupported Home model getter");
            }
            return method;
        }
    }
}
