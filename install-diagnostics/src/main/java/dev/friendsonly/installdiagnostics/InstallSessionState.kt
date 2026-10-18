package dev.friendsonly.installdiagnostics

/** Persisted ownership of one attempt; independent of Android and process lifetime. */
internal data class InstallSessionState(
    val phase: Phase = Phase.IDLE,
    val sessionId: Int? = null,
    val confirmationLaunchCount: Int = 0,
) {
    enum class Phase { IDLE, COPYING, WAITING_RESULT, PENDING_CONFIRMATION, CONFIRMATION_LAUNCHED, CANCELLING, FINISHED }

    val active: Boolean get() = phase != Phase.IDLE && phase != Phase.FINISHED
    val copying: Boolean get() = phase == Phase.COPYING
    val canConfirm: Boolean get() = phase == Phase.PENDING_CONFIRMATION && confirmationLaunchCount == 0

    fun begin(): InstallSessionState? = if (active) null else InstallSessionState(Phase.COPYING)
    fun attachSession(id: Int): InstallSessionState? =
        if (copying && sessionId == null) copy(sessionId = id) else null

    fun committed(): InstallSessionState = if (copying) copy(phase = Phase.WAITING_RESULT) else this

    fun pending(): InstallSessionState = when (phase) {
        Phase.COPYING, Phase.WAITING_RESULT -> copy(phase = Phase.PENDING_CONFIRMATION)
        else -> this
    }

    // Persist this transition before handing the Intent to Android. Never replay after process death.
    fun claimConfirmation(): InstallSessionState? =
        if (canConfirm) copy(phase = Phase.CONFIRMATION_LAUNCHED, confirmationLaunchCount = 1) else null

    fun cancelling(): InstallSessionState = if (active) copy(phase = Phase.CANCELLING) else this
    fun finish(): InstallSessionState = copy(phase = Phase.FINISHED, sessionId = null)
}
