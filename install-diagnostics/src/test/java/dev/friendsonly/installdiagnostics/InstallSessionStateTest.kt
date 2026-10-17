package dev.friendsonly.installdiagnostics

import dev.friendsonly.installdiagnostics.InstallSessionState.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallSessionStateTest {
    @Test fun retryCannotReplaceAnActiveAttemptAtAnyStage() {
        val activePhases = listOf(
            Phase.COPYING,
            Phase.WAITING_RESULT,
            Phase.PENDING_CONFIRMATION,
            Phase.CONFIRMATION_LAUNCHED,
            Phase.CANCELLING,
        )

        for (phase in activePhases) {
            val state = InstallSessionState(phase, sessionId = 42)
            assertTrue("$phase must retain ownership of the attempt", state.active)
            assertNull("$phase must block a second installation", state.begin())
        }
    }

    @Test fun duplicatePendingCallbacksAllowOnlyOneConfirmationLaunch() {
        val waiting = InstallSessionState(Phase.WAITING_RESULT, sessionId = 42)
        val pending = waiting.pending()
        assertEquals(pending, pending.pending())
        assertTrue(pending.active)
        assertTrue(pending.canConfirm)

        val launched = pending.claimConfirmation()!!
        assertEquals(Phase.CONFIRMATION_LAUNCHED, launched.phase)
        assertEquals(1, launched.confirmationLaunchCount)
        assertEquals(42, launched.sessionId)
        assertFalse(launched.canConfirm)
        assertEquals(launched, launched.pending())
        assertNull(launched.pending().claimConfirmation())
        assertNull(launched.claimConfirmation())
    }

    @Test fun restoredPendingAttemptRetainsOwnershipAndCanBeClaimedOnce() {
        val restored = InstallSessionState(
            phase = Phase.PENDING_CONFIRMATION,
            sessionId = 42,
            confirmationLaunchCount = 0,
        )

        assertTrue(restored.active)
        assertNull(restored.begin())
        val claimed = restored.claimConfirmation()!!
        assertEquals(42, claimed.sessionId)
        assertNull(claimed.claimConfirmation())
    }

    @Test fun restoredLaunchedConfirmationCannotReplay() {
        val restored = InstallSessionState(
            phase = Phase.CONFIRMATION_LAUNCHED,
            sessionId = 42,
            confirmationLaunchCount = 1,
        )

        assertTrue(restored.active)
        assertFalse(restored.canConfirm)
        assertNull(restored.begin())
        assertNull(restored.claimConfirmation())
        assertNull(restored.pending().claimConfirmation())
    }

    @Test fun finishingReleasesSessionAndNextAttemptResetsConfirmationState() {
        val launched = InstallSessionState(Phase.PENDING_CONFIRMATION, sessionId = 42)
            .claimConfirmation()!!
        val finished = launched.finish()

        assertEquals(Phase.FINISHED, finished.phase)
        assertFalse(finished.active)
        assertFalse(finished.canConfirm)
        assertNull(finished.sessionId)
        assertNull(finished.claimConfirmation())

        val next = finished.begin()!!
        assertEquals(Phase.COPYING, next.phase)
        assertTrue(next.copying)
        assertNull(next.sessionId)
        assertEquals(0, next.confirmationLaunchCount)
        assertNotNull(next.attachSession(43))
    }

    @Test fun synchronousCallbackBeforeCommitReturnsCannotRegressState() {
        val copying = InstallSessionState().begin()!!.attachSession(42)!!
        val pending = copying.pending()
        val launched = pending.claimConfirmation()!!
        val cancelling = copying.cancelling()
        val finished = copying.finish()

        for (state in listOf(pending, launched, cancelling, finished)) {
            assertEquals("post-commit bookkeeping must preserve ${state.phase}", state, state.committed())
        }
    }

    @Test fun latePendingCannotReopenCancelledOrFinishedAttempt() {
        val waiting = InstallSessionState(Phase.WAITING_RESULT, sessionId = 42)
        val cancelling = waiting.cancelling()
        val finished = waiting.finish()

        assertEquals(cancelling, cancelling.pending())
        assertEquals(finished, finished.pending())
        assertFalse(cancelling.canConfirm)
        assertFalse(finished.canConfirm)
    }

    @Test fun terminalResultReleasesOwnershipEvenDuringCancellation() {
        val cancelling = InstallSessionState(Phase.CONFIRMATION_LAUNCHED, sessionId = 42, confirmationLaunchCount = 1)
            .cancelling()
        val terminal = cancelling.finish()
        assertEquals(Phase.FINISHED, terminal.phase)
        assertFalse(terminal.active)
        assertNull(terminal.sessionId)
        assertNotNull(terminal.begin())
    }

    @Test fun sessionCanBeAttachedOnlyOnceBeforeCommit() {
        val copying = InstallSessionState().begin()!!
        val attached = copying.attachSession(42)!!
        assertNull(attached.attachSession(43))
        val waiting = attached.committed()
        assertEquals(Phase.WAITING_RESULT, waiting.phase)
        assertEquals(42, waiting.sessionId)
        assertNull(waiting.attachSession(43))
    }
}
