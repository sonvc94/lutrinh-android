package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.lutrinh.app.domain.usecase.KmScale

/** The km readout's scale bar (docs/08 §5 "Stats"): round blocks, at most five, labels. */
class KmScaleTest {
    @Test
    fun roundStepsAndTicks() {
        val hg = KmScale.of(139.0)!!
        assertEquals(50.0, hg.step, 0.0)
        assertEquals(listOf(0.0, 50.0, 100.0), hg.ticks)
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0, 8.0, 10.0), KmScale.of(10.0)!!.ticks)
        assertEquals(500.0, KmScale.of(1_726.0)!!.step, 0.0)
        assertEquals(0.1, KmScale.of(0.4)!!.step, 1e-9)
        assertEquals(listOf(0.0, 0.1, 0.2, 0.3, 0.4), KmScale.of(0.4)!!.ticks)
    }

    @Test
    fun neverMoreThanFiveBlocks() {
        listOf(0.05, 0.9, 3.0, 7.7, 12.0, 49.0, 99.9, 101.0, 250.0, 999.0, 2_345.6).forEach { km ->
            val s = KmScale.of(km)!!
            val blocks = km / s.step
            assert(blocks <= KmScale.MAX_BLOCKS + 1e-9) { "$km km: $blocks blocks" }
            assert(blocks >= 2.0 - 1e-9) { "$km km: only $blocks blocks" }
            assert(s.ticks.last() <= km + 1e-9)
        }
    }

    @Test
    fun noBarForNothing() {
        assertNull(KmScale.of(0.0))
        assertNull(KmScale.of(Double.NaN))
        assertNull(KmScale.of(-3.0))
    }

    @Test
    fun labelsInVietnameseFormat() {
        assertEquals("1.000", KmScale.of(1_726.0)!!.label(1_000.0))
        assertEquals("0,5", KmScale.of(2.0)!!.label(0.5))
        assertEquals(0.5, KmScale.of(139.0)!!.fraction(69.5), 1e-9)
    }
}
