package com.benchfirewatch.eula

/**
 * Bump [VERSION] whenever the text in assets/eula.txt changes, so people who
 * already accepted an older license are asked again.
 */
object Eula {
    const val VERSION = 1

    fun needsAcceptance(acceptedVersion: Int, currentVersion: Int = VERSION): Boolean =
        acceptedVersion != currentVersion
}
