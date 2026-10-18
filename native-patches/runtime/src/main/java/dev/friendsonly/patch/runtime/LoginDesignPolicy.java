package dev.friendsonly.patch.runtime;

/** Exact signed-out hosts only. No route bundles, text or account values inspected. */
final class LoginDesignPolicy {
    private LoginDesignPolicy() {}
    static boolean host(String name) {
        return "com.instagram.nux.activity.BloksSignedOutFragmentActivity".equals(name)
            || "com.instagram.nux.activity.SignedOutFragmentActivity".equals(name)
            || "com.instagram.nux.activity.SessionlessNuxActivity".equals(name)
            || "com.instagram.login.loggedoutapp.activity.LoggedOutAppActivity".equals(name);
    }
    static boolean blue(int color) {
        int a = color >>> 24, r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
        return a == 255 && r <= 32 && g >= 80 && g <= 170 && b >= 190;
    }
    static boolean canvas(int color) {
        return (color >>> 24) == 255 && Math.abs(((color >>> 16) & 255) - 22) <= 4
            && Math.abs(((color >>> 8) & 255) - 33) <= 4 && Math.abs((color & 255) - 39) <= 4;
    }
}
