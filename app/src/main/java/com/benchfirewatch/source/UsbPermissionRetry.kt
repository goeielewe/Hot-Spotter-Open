package com.benchfirewatch.source

/**
 * After the user denies USB permission, do not raise the dialog again on the
 * next frame. Wait, unless the camera is unplugged and plugged back in.
 */
object UsbPermissionRetry {
    const val QUIET_MS = 20_000L
    const val DENIED_DETAIL = "USB permission was denied. Accept the prompt and choose Always."

    fun quietUntil(nowMs: Long): Long = nowMs + QUIET_MS

    /**
     * True when the watch should sit on the status banner instead of asking again.
     * An unplug clears the wait so the next plug can show the prompt.
     */
    fun shouldWait(nowMs: Long, quietUntilMs: Long, devicePresent: Boolean): Boolean {
        if (quietUntilMs <= nowMs) return false
        return devicePresent
    }
}
