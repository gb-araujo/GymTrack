package app.gabrielaraujo.gymtrack.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val ptBR = Locale("pt", "BR")
private val zone: ZoneId = ZoneId.systemDefault()

private fun fmt(pattern: String) = DateTimeFormatter.ofPattern(pattern, ptBR).withZone(zone)

fun formatDate(epochMillis: Long, pattern: String): String =
    fmt(pattern).format(Instant.ofEpochMilli(epochMillis))

/** Equivale a format(ts, 'd MMM · HH:mm'). */
fun formatShortDateTime(ts: Long): String = formatDate(ts, "d MMM · HH:mm")

/** Equivale a format(ts, 'MMMM yyyy'), capitalizado. */
fun formatMonthYear(ts: Long): String = formatDate(ts, "MMMM yyyy").replaceFirstChar { it.uppercase() }

/** Equivale a format(ts, "EEEE, d 'de' MMMM 'de' yyyy"). */
fun formatFullDate(ts: Long): String =
    formatDate(ts, "EEEE, d 'de' MMMM 'de' yyyy").replaceFirstChar { it.uppercase() }

/** Aproxima o formatDistanceToNow (pt-BR) do date-fns: "há 5 min", "há 2 h", "há 3 dias". */
fun timeAgo(ts: Long, addSuffix: Boolean = true): String {
    val diff = System.currentTimeMillis() - ts
    val future = diff < 0
    val s = abs(diff) / 1000
    val core = when {
        s < 60 -> "menos de 1 min"
        s < 3600 -> "${s / 60} min"
        s < 86400 -> {
            val h = s / 3600
            if (h == 1L) "1 hora" else "$h horas"
        }
        s < 2592000 -> {
            val d = s / 86400
            if (d == 1L) "1 dia" else "$d dias"
        }
        s < 31536000 -> {
            val m = s / 2592000
            if (m == 1L) "1 mês" else "$m meses"
        }
        else -> {
            val y = s / 31536000
            if (y == 1L) "1 ano" else "$y anos"
        }
    }
    if (!addSuffix) return core
    return if (future) "em $core" else "há $core"
}

/** Duração em minutos → "1h 5min" / "45 min". */
fun formatDuration(minutes: Int?): String {
    if (minutes == null || minutes == 0) return "—"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}min" else "$m min"
}

/** Volume em kg → "1.2t" / "850kg". */
fun formatVolume(volume: Double?): String {
    val v = volume ?: 0.0
    if (v <= 0) return "—"
    return if (v >= 1000) "${(v / 1000 * 10).roundToInt() / 10.0}t" else "${v.roundToInt()}kg"
}

/** Remove ".0" de pesos inteiros para exibição (60.0 → "60", 62.5 → "62.5"). */
fun formatWeight(weight: Double): String =
    if (weight == weight.toLong().toDouble()) weight.toLong().toString() else weight.toString()
