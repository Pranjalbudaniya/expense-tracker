package com.example.expensetracker.core.common.time

import java.time.Clock
import java.time.Instant

interface ClockProvider {
    fun getClock(): Clock
    fun now(): Instant = getClock().instant()
    fun currentEpochMillis(): Long = getClock().millis()
}

class DefaultClockProvider(
    private val clock: Clock = Clock.systemDefaultZone()
) : ClockProvider {
    override fun getClock(): Clock = clock
}
