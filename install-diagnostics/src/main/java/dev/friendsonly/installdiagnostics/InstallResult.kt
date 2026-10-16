package dev.friendsonly.installdiagnostics

/** Pure formatter: pending consent must never be mistaken for installation success. */
internal data class InstallResult(
    val status: Int,
    val legacyStatus: Int?,
    val message: String,
    val otherPackage: String?,
    val developerVerificationFailureReason: Int? = null,
    val developerVerificationLitePerformed: Boolean? = null,
) {
    val outcome: String get() = when (status) {
        -1 -> "PENDING_USER_ACTION"
        0 -> "SUCCESS"
        1 -> "FAILURE"
        2 -> "FAILURE_BLOCKED"
        3 -> "FAILURE_ABORTED"
        4 -> "FAILURE_INVALID"
        5 -> "FAILURE_CONFLICT"
        6 -> "FAILURE_STORAGE"
        7 -> "FAILURE_INCOMPATIBLE"
        8 -> "FAILURE_TIMEOUT"
        else -> "UNKNOWN_STATUS"
    }

    fun report(device: String): String = buildString {
        appendLine("Friends Only installation diagnostic v1")
        appendLine(device.take(1024))
        appendLine("Package: com.instagram.android")
        appendLine("Status: $outcome ($status)")
        legacyStatus?.let { appendLine("Legacy status: $it") }
        developerVerificationFailureReason?.let { appendLine("Developer verification failure reason: $it") }
        developerVerificationLitePerformed?.let { appendLine("Developer verification lite performed: $it") }
        otherPackage?.takeIf { it.isNotBlank() }?.let { appendLine("Other package: ${it.take(512)}") }
        append("Android message: ${message.take(8192)}")
    }
}
