package app.gabrielaraujo.gymtrack.domain.statistics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StreakTest {
    private val zone = ZoneOffset.UTC
    private val day0 = LocalDate.of(2026, 9, 7) // segunda

    private fun at(date: LocalDate, hour: Int = 18) =
        date.atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun streak(dates: List<LocalDate>, today: LocalDate) =
        computeStreak(dates.map { at(it) }, today, zone)

    @Test
    fun `sem treinos a ofensiva e zero`() {
        val s = computeStreak(emptyList(), day0, zone)
        assertEquals(0, s.count)
        assertFalse(s.active)
        assertNull(s.deadline)
    }

    @Test
    fun `cada treino soma um, inclusive no mesmo dia`() {
        val s = streak(listOf(day0, day0, day0.plusDays(2)), today = day0.plusDays(2))
        assertEquals(3, s.count)
    }

    @Test
    fun `treino semanal no mesmo dia da semana mantem a ofensiva`() {
        val dates = (0L..4L).map { day0.plusWeeks(it) }
        val s = streak(dates, today = dates.last())
        assertEquals(5, s.count)
        assertEquals(5, s.best)
    }

    @Test
    fun `intervalo de oito dias entre treinos reinicia a contagem`() {
        val s = streak(listOf(day0, day0.plusDays(1), day0.plusDays(9)), today = day0.plusDays(9))
        assertEquals(1, s.count)
        assertEquals(2, s.best)
    }

    @Test
    fun `ofensiva continua viva ate o setimo dia sem treino`() {
        val s = streak(listOf(day0, day0.plusDays(1)), today = day0.plusDays(8))
        assertEquals(2, s.count)
        assertEquals(day0.plusDays(8), s.deadline)
        assertEquals(0, s.daysLeft)
    }

    @Test
    fun `mais de sete dias sem treinar zera a ofensiva`() {
        val s = streak(listOf(day0, day0.plusDays(1)), today = day0.plusDays(9))
        assertEquals(0, s.count)
        assertFalse(s.active)
        assertEquals(2, s.best)
        assertEquals(day0.plusDays(1), s.lastWorkout)
    }

    @Test
    fun `dias restantes contam a partir do ultimo treino`() {
        val s = streak(listOf(day0), today = day0.plusDays(3))
        assertTrue(s.active)
        assertEquals(4, s.daysLeft)
    }

    @Test
    fun `ordem de entrada nao importa`() {
        val s = streak(listOf(day0.plusDays(5), day0, day0.plusDays(2)), today = day0.plusDays(5))
        assertEquals(3, s.count)
    }
}
