package com.example.expensetracker.core.common.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

interface DateTimeProvider {
    fun nowInstant(): Instant
    fun today(zoneId: ZoneId = currentZoneId()): LocalDate
    fun nowLocalDateTime(zoneId: ZoneId = currentZoneId()): LocalDateTime
    fun currentZoneId(): ZoneId
}

class DefaultDateTimeProvider(
    private val clockProvider: ClockProvider = DefaultClockProvider()
) : DateTimeProvider {

    override fun nowInstant(): Instant = clockProvider.now()

    override fun currentZoneId(): ZoneId = clockProvider.getClock().zone

    override fun today(zoneId: ZoneId): LocalDate =
        nowInstant().atZone(zoneId).toLocalDate()

    override fun nowLocalDateTime(zoneId: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(nowInstant(), zoneId)
}
