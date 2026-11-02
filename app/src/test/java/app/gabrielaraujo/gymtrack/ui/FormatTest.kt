package app.gabrielaraujo.gymtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Apresentação de unidades. */
class FormatTest {
    private val nbsp = " "

    @Test
    fun `volume sempre em kg com separador de milhar por espaco`() {
        assertEquals("600 kg", formatVolume(600.0))
        assertEquals("9${nbsp}870 kg", formatVolume(9_870.0))
        assertEquals("1${nbsp}250${nbsp}000 kg", formatVolume(1_250_000.0))
        assertEquals("1${nbsp}000 kg", formatVolume(999.6))
    }

    @Test
    fun `volume vazio vira travessao`() {
        assertEquals("—", formatVolume(null))
        assertEquals("—", formatVolume(0.0))
    }

    @Test
    fun `carga e serie`() {
        assertEquals("62.5 kg", formatLoad(62.5))
        assertEquals("60 kg", formatLoad(60.0))
        assertEquals("Peso corp.", formatLoad(0.0))
        assertEquals("62.5 kg × 8", formatSet(62.5, 8))
        assertEquals("Peso corp. × 12", formatSet(0.0, 12))
    }

    @Test
    fun `diferencas com sinal e arredondamento`() {
        assertEquals("+2.5 kg", formatDelta(2.5, "kg"))
        assertEquals("−1 reps", formatDelta(-1.0, "reps"))
        assertEquals("=", formatDelta(0.04, "kg"))
        assertEquals("+3", formatDelta(3.0, ""))
        assertEquals("81.7", formatDecimal(70.0 * (1 + 5 / 30.0)))
    }
}
