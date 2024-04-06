package com.example.camunda8demo.service

import com.example.camunda8demo.DATETIME_PATTERN
import com.example.camunda8demo.util.RetryableJobException
import com.fasterxml.jackson.annotation.JsonFormat
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service
class SlaService {
    private val slaDatabase = ConcurrentHashMap<String, Sla>()
    private val slaStartWork = 120L
    private val slaResolution = 600L
    private val recalcSla = 720L

    fun get(id: String): Sla = slaDatabase[id] ?: throwNotFoundErr(id)

    fun createRouterSla(): List<Sla> {
        val now = LocalDateTime.now(ZoneId.of("UTC"))

        val id1 = UUID.randomUUID().toString()
        val startWorkSla = Sla(
            id = id1,
            status = SlaStatus.RUNNING,
            type = SlaType.START_WORK,
            warnDate = now.plusSeconds(slaStartWork / 2),
            expirationDate = now.plusSeconds(slaStartWork),
            createdAt = now,
        )

        val id2 = UUID.randomUUID().toString()
        val resolutionSla = Sla(
            id = id2,
            status = SlaStatus.RUNNING,
            type = SlaType.RESOLUTION,
            warnDate = now.plusSeconds(slaResolution / 2),
            expirationDate = now.plusSeconds(slaResolution),
            createdAt = now
        )

        slaDatabase.put(id1, startWorkSla)
        slaDatabase.put(id2, resolutionSla)
        return listOf(startWorkSla, resolutionSla)
    }

    fun createTaskSla(): List<Sla> {
        val now = LocalDateTime.now(ZoneId.of("UTC"))

        val id1 = UUID.randomUUID().toString()
        val delaySla = Sla(
            id = id1,
            status = SlaStatus.RUNNING,
            type = SlaType.DELAY,
            expirationDate = now.plusSeconds(slaStartWork),
            createdAt = now,
        )

        slaDatabase.put(id1, delaySla)
        return listOf(delaySla)
    }

    fun change(id: String): Sla = slaDatabase.computeIfPresent(id) { _, v ->
        if (setOf(SlaStatus.EXPIRED, SlaStatus.COMPLETED).contains(v.status))
            throw IllegalStateException("SLA '$v' is in wrong status for changing")

        // если recalcSla < стартовой длительности и в результате даты будут ранее текущего времени, то схема просто сразу триггернет таймеры
        val warnDate = v.createdAt.plusSeconds(recalcSla / 2).toInstant(ZoneOffset.UTC).plusMillis(v.sumPausedMs)
        val expirationDate = v.createdAt.plusSeconds(recalcSla).toInstant(ZoneOffset.UTC).plusMillis(v.sumPausedMs)

        v.copy(
            expirationDate = LocalDateTime.ofInstant(expirationDate, ZoneId.of("UTC")),
            warnDate = LocalDateTime.ofInstant(warnDate, ZoneId.of("UTC"))
        )
    } ?: throwNotFoundErr(id)

    fun complete(id: String): Sla = slaDatabase.computeIfPresent(id) { _, v ->
        if (setOf(SlaStatus.EXPIRED, SlaStatus.COMPLETED).contains(v.status))
            throw IllegalStateException("SLA '$v' is in wrong status for completion")

        v.copy(status = SlaStatus.COMPLETED)
    } ?: throwNotFoundErr(id)

    fun warn(id: String): Sla = slaDatabase.computeIfPresent(id) { _, v ->
        if (v.status == SlaStatus.EXPIRED)
            throw IllegalStateException("SLA '$v' is expired, cannot be warned again")

        v.copy(status = SlaStatus.WARNING)
    } ?: throwNotFoundErr(id)

    fun expire(id: String): Sla = slaDatabase.computeIfPresent(id) { _, v -> v.copy(status = SlaStatus.EXPIRED) }
        ?: throwNotFoundErr(id)

    fun pause(id: String): Sla? = slaDatabase.computeIfPresent(id) { _, v ->
        if (setOf(SlaStatus.PAUSED, SlaStatus.EXPIRED, SlaStatus.COMPLETED).contains(v.status))
            throw IllegalStateException("SLA '$v' is in wrong status for pausing")

        v.copy(
            status = SlaStatus.PAUSED,
            lastPausedAt = LocalDateTime.now()
        )
    } ?: throwRetryableNotFoundErr(id)

    fun resume(id: String): Sla? = slaDatabase.computeIfPresent(id) { _, v ->
        if (v.status != SlaStatus.PAUSED)
            throw IllegalStateException("SLA '$v' is already resumed")

        val lastPausedAt = v.lastPausedAt ?: throw IllegalStateException("SLA '$v' pausedAt is null")
        val lastPausedMs = Instant.now().toEpochMilli() - lastPausedAt.toInstant(ZoneOffset.UTC).toEpochMilli()
        val warnDate = v.warnDate?.toInstant(ZoneOffset.UTC)?.plusMillis(lastPausedMs)
        val expirationDate = v.expirationDate.toInstant(ZoneOffset.UTC).plusMillis(lastPausedMs)

        v.copy(
            status = SlaStatus.RUNNING,
            sumPausedMs = v.sumPausedMs + lastPausedMs,
            expirationDate = LocalDateTime.ofInstant(expirationDate, ZoneId.of("UTC")),
            warnDate = LocalDateTime.ofInstant(warnDate, ZoneId.of("UTC"))
        )
    } ?: throwRetryableNotFoundErr(id)

    private fun throwNotFoundErr(id: String): Nothing =
        throw IllegalStateException("Not found SLA '$id'")

    private fun throwRetryableNotFoundErr(id: String): Nothing =
        throw RetryableJobException("Not found SLA '$id', maybe it hasn't created yet")
}

data class Sla(
    val id: String,
    val status: SlaStatus,
    val type: SlaType,
    @JsonFormat(pattern = DATETIME_PATTERN)
    val expirationDate: LocalDateTime,
    @JsonFormat(pattern = DATETIME_PATTERN)
    val warnDate: LocalDateTime? = null,
    @JsonFormat(pattern = DATETIME_PATTERN)
    val createdAt: LocalDateTime,
    val sumPausedMs: Long = 0,
    @JsonFormat(pattern = DATETIME_PATTERN)
    val lastPausedAt: LocalDateTime? = null,
)

enum class SlaStatus {
    RUNNING, PAUSED, WARNING, EXPIRED, COMPLETED
}

enum class SlaType {
    START_WORK, RESOLUTION, DELAY
}
