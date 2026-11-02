package app.gabrielaraujo.gymtrack.domain

import java.time.LocalDate
import java.time.ZoneId

/**
 * Período dos relatórios. O início é sempre o começo de um dia no fuso do aparelho
 * ("30 dias" = hoje e os 29 dias anteriores), para o resultado não mudar com a hora da consulta.
 */
enum class ReportPeriod(val label: String, val days: Long?) {
    DAYS_30("30 dias", 30),
    DAYS_90("90 dias", 90),
    MONTHS_6("6 meses", 182),
    YEAR_1("1 ano", 365),
    ALL("Tudo", null);

    /** Primeiro dia incluído, ou null para todo o histórico. */
    fun startDate(today: LocalDate): LocalDate? = days?.let { today.minusDays(it - 1) }

    /** Início em epoch ms, ou null para todo o histórico. */
    fun startMillis(today: LocalDate, zone: ZoneId): Long? =
        startDate(today)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()

    fun contains(time: Long, today: LocalDate, zone: ZoneId): Boolean {
        val start = startMillis(today, zone) ?: return true
        return time >= start
    }
}
