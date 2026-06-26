package at.aau.pulverfass.client.util

import kotlin.time.TimeSource

private val monotonicTimeOrigin = TimeSource.Monotonic.markNow()

/**
 * Monotone Zeitbasis in Millisekunden für relative Deadlines und
 * Dauer-Messungen (multiplatform-Ersatz für `System.currentTimeMillis`;
 * nur Differenzen werden verglichen).
 */
internal fun monotonicNowMillis(): Long = monotonicTimeOrigin.elapsedNow().inWholeMilliseconds
