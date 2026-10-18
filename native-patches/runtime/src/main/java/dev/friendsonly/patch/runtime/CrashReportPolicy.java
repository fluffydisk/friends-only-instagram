package dev.friendsonly.patch.runtime;

/** Allowlisted structural data only. Exception messages, paths and runtime values are excluded. */
final class CrashReportPolicy {
    static final int LIMIT = 6000;
    static String identifier(String text) {
        if (text == null || text.length() > 180 || !text.matches("[A-Za-z0-9_.$<>-]+")) return "unknown";
        return text;
    }
    static String format(Throwable error) {
        StringBuilder report = new StringBuilder();
        for (int cause = 0; error != null && cause < 4; cause++) {
            report.append("Cause ").append(cause).append(": ").append(identifier(error.getClass().getName())).append('\n');
            StackTraceElement[] stack = error.getStackTrace();
            for (int i = 0; i < stack.length && i < 12; i++) {
                StackTraceElement frame = stack[i];
                report.append("  at ").append(identifier(frame.getClassName())).append('.').append(identifier(frame.getMethodName()))
                    .append(':').append(frame.getLineNumber()).append('\n');
            }
            error = error.getCause();
        }
        return report.substring(0, Math.min(report.length(), LIMIT));
    }
}
