package com.example.camunda8demo.service

import com.example.camunda8demo.pattern
import com.example.camunda8demo.util.RetryableJobException
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

@Service
class SlaService {
    private val slaDatabase = ConcurrentHashMap<String, Sla>()
    private val sla = 60L
    private val recalcSla = 120L

    fun get(id: String): Sla = slaDatabase[id] ?: throwNotFoundErr(id)

    fun create(id: String): Sla {
        val now = LocalDateTime.now(ZoneId.of("UTC"))
        val warnDate = now.plusSeconds(sla / 2)
        val expirationDate = now.plusSeconds(sla)

        val savedSla = Sla(
            id = id,
            status = SlaStatus.RUNNING,
            warnDate = warnDate.format(pattern),
            expirationDate = expirationDate.format(pattern),
            startTime = LocalDateTime.now().format(pattern),
        )
        slaDatabase.put(id, savedSla)
        return savedSla
    }

    fun recalculate(id: String): Sla = slaDatabase.computeIfPresent(id) { k, v ->
        if (v.status != SlaStatus.CHANGED) throw IllegalStateException("SLA '$v' must be in status CHANGED")
        val spentMs = v.spentMs ?: throw IllegalStateException("SLA '$v' spentMs is null")

        val warnDate = Instant.now().plusSeconds(recalcSla / 2).minusMillis(spentMs)
        val expirationDate = Instant.now().plusSeconds(recalcSla).minusMillis(spentMs)

        Sla(
            id = k,
            status = SlaStatus.RUNNING,
            warnDate = LocalDateTime.ofInstant(warnDate, ZoneId.of("UTC")).format(pattern),
            expirationDate = LocalDateTime.ofInstant(expirationDate, ZoneId.of("UTC")).format(pattern),
            startTime = LocalDateTime.now().format(pattern),
        )
    } ?: throwNotFoundErr(id)

    fun complete(id: String): Sla = slaDatabase.computeIfPresent(id) { _, v -> v.copy(status = SlaStatus.COMPLETED) }
        ?: throwNotFoundErr(id)

    fun expire(id: String): Sla = slaDatabase.computeIfPresent(id) { _, v -> v.copy(status = SlaStatus.EXPIRED) }
        ?: throwNotFoundErr(id)

    fun pause(id: String): Sla? = slaDatabase.computeIfPresent(id) { k, v ->
        if (v.status == SlaStatus.PAUSED)
            throw IllegalStateException("SLA '$v' already paused")

        val expirationDate = v.expirationDate ?: throw IllegalStateException("SLA '$v' expirationDate is null")
        val newExpirationDate = LocalDateTime.parse(expirationDate, pattern)
        val remainingMs =
            newExpirationDate.toInstant(ZoneOffset.UTC).toEpochMilli() - Instant.now().toEpochMilli()

        Sla(
            id = k,
            status = SlaStatus.PAUSED,
            remainingMs = remainingMs
        )
    } ?: throwRetryableNotFoundErr(id)

    fun resume(id: String): Sla? = slaDatabase.computeIfPresent(id) { k, v ->
        if (v.status != SlaStatus.PAUSED)
            throw IllegalStateException("SLA '$v' already resumed")

        val remainingMs = v.remainingMs ?: throw IllegalStateException("SLA '$v' remainingMs is null")
        val warnDate = Instant.now().plusMillis(remainingMs / 2)
        val expirationDate = Instant.now().plusMillis(remainingMs)

        Sla(
            id = k,
            status = SlaStatus.RUNNING,
            warnDate = LocalDateTime.ofInstant(warnDate, ZoneId.of("UTC")).format(pattern),
            expirationDate = LocalDateTime.ofInstant(expirationDate, ZoneId.of("UTC")).format(pattern),
            startTime = LocalDateTime.now().format(pattern),
        )
    } ?: throwRetryableNotFoundErr(id)

    private fun throwNotFoundErr(id: String): Nothing =
        throw RetryableJobException("Not found SLA '$id', maybe it hasn't created yet")

    private fun throwRetryableNotFoundErr(id: String): Nothing =
        throw IllegalStateException("Not found SLA '$id'")
}

data class Sla(
    var id: String,
    var status: SlaStatus,
    var expirationDate: String? = null,
    var warnDate: String? = null,
    var startTime: String? = null,
    var remainingMs: Long? = null,
    var spentMs: Long? = null,
)

enum class SlaStatus {
    RUNNING, PAUSED, EXPIRED, COMPLETED, CHANGED
}
