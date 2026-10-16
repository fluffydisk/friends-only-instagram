package dev.friendsonly.installdiagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallResultTest {
    @Test fun pendingConfirmationIsNeverReportedAsSuccess() {
        val report = InstallResult(-1, null, "User confirmation required", null).report("Android: 17")
        assertTrue(report.contains("PENDING_USER_ACTION (-1)"))
        assertFalse(report.contains("SUCCESS"))
    }

    @Test fun conflictPreservesAndroidLegacyCodeAndPackageName() {
        val report = InstallResult(5, -7, "Signatures do not match", "com.instagram.android").report("Android: 17")
        assertTrue(report.contains("FAILURE_CONFLICT (5)"))
        assertTrue(report.contains("Legacy status: -7"))
        assertTrue(report.contains("Other package: com.instagram.android"))
        assertTrue(report.contains("Signatures do not match"))
    }

    @Test fun unknownStatusRemainsUnknown() {
        assertEquals("UNKNOWN_STATUS", InstallResult(987, null, "", null).outcome)
    }

    @Test fun successRequiresExactSuccessStatus() {
        assertEquals("SUCCESS", InstallResult(0, 1, "Success", null).outcome)
        assertEquals("FAILURE_ABORTED", InstallResult(3, -115, "Cancelled", null).outcome)
    }

    @Test fun optionalValuesAreOmittedAndLongSystemMessagesAreBounded() {
        val report = InstallResult(4, null, "x".repeat(20000), null).report("test")
        assertFalse(report.contains("Legacy status"))
        assertFalse(report.contains("Other package"))
        assertTrue(report.length < 8500)
    }

    @Test fun android17VerificationExtrasPreserveBothFalseAndReasonCode() {
        val report = InstallResult(2, null, "Blocked", null, 2, false).report("Android: 17")
        assertTrue(report.contains("Developer verification failure reason: 2"))
        assertTrue(report.contains("Developer verification lite performed: false"))
        assertFalse(InstallResult(2, null, "Blocked", null).report("test").contains("Developer verification"))
    }
}
