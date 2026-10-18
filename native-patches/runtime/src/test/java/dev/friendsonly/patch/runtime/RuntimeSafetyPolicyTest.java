package dev.friendsonly.patch.runtime;

public final class RuntimeSafetyPolicyTest {
    static int checks;
    static void verify(boolean condition) { if (!condition) throw new AssertionError(); checks++; }
    public static void main(String[] args) {
        verify(LoginDesignPolicy.host("com.instagram.nux.activity.BloksSignedOutFragmentActivity"));
        verify(LoginDesignPolicy.host("com.instagram.nux.activity.SignedOutFragmentActivity"));
        verify(LoginDesignPolicy.host("com.instagram.nux.activity.SessionlessNuxActivity"));
        verify(LoginDesignPolicy.host("com.instagram.login.loggedoutapp.activity.LoggedOutAppActivity"));
        for (String outside : new String[]{null,"","com.instagram.mainactivity.InstagramMainActivity","com.instagram.challenge.activity.ChallengeActivity","other.BloksSignedOutFragmentActivity"})
            verify(!LoginDesignPolicy.host(outside));
        verify(LoginDesignPolicy.blue(0xff0064e0)); verify(LoginDesignPolicy.blue(0xff0095f6));
        for (int outside : new int[]{0x000064e0,0xffdd0000,0xff747474,0xffffee00,0xff1a3377}) verify(!LoginDesignPolicy.blue(outside));
        verify(LoginDesignPolicy.canvas(0xff162127));
        for (int outside : new int[]{0x00162127,0xff000000,0xffffffff,0xff0064e0}) verify(!LoginDesignPolicy.canvas(outside));
        Throwable cause = new IllegalArgumentException("secret-password-and-message");
        cause.setStackTrace(new StackTraceElement[]{new StackTraceElement("example.Native", "render", "secret-private-path", 42)});
        Throwable failure = new IllegalStateException("secret-session-cookie", cause);
        failure.setStackTrace(new StackTraceElement[]{new StackTraceElement("example.Avatar", "onDraw", "secret-username", 21)});
        String report = CrashReportPolicy.format(failure);
        verify(report.contains("java.lang.IllegalStateException")); verify(report.contains("java.lang.IllegalArgumentException"));
        verify(report.contains("example.Avatar.onDraw:21")); verify(report.contains("example.Native.render:42"));
        verify(!report.contains("secret")); verify(report.length() <= CrashReportPolicy.LIMIT);
        verify(CrashReportPolicy.format(null).isEmpty());
        for (String invalid : new String[]{null,"value\nInjected","https://private/url", "person@email.example", "a b"}) verify("unknown".equals(CrashReportPolicy.identifier(invalid)));
        verify("<init>".equals(CrashReportPolicy.identifier("<init>")));
        Throwable cycle = new Throwable("secret") { @Override public synchronized Throwable getCause() { return this; } };
        verify(CrashReportPolicy.format(cycle).length() <= CrashReportPolicy.LIMIT);
        System.out.println("RuntimeSafetyPolicyTest: " + checks + " assertions passed");
    }
}
