package vn.lutrinh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.lutrinh.app.domain.usecase.BatteryGuide
import vn.lutrinh.app.domain.usecase.DeviceMaker
import vn.lutrinh.app.domain.usecase.ShowOnce

/** R22: manufacturer → guide section, and the guide opening by itself only once. */
class BatteryGuideTest {
    @Test fun xiaomi_family_shares_a_section() {
        assertEquals(DeviceMaker.XIAOMI, BatteryGuide.makerOf("Xiaomi"))
        assertEquals(DeviceMaker.XIAOMI, BatteryGuide.makerOf("Redmi"))
        assertEquals(DeviceMaker.XIAOMI, BatteryGuide.makerOf("POCO"))
        assertEquals(DeviceMaker.XIAOMI, BatteryGuide.makerOf("unknown", "Redmi"))
    }

    @Test fun oppo_family_shares_a_section() {
        assertEquals(DeviceMaker.OPPO, BatteryGuide.makerOf("OPPO"))
        assertEquals(DeviceMaker.OPPO, BatteryGuide.makerOf("realme"))
        assertEquals(DeviceMaker.OPPO, BatteryGuide.makerOf("OnePlus"))
    }

    @Test fun samsung_and_vivo() {
        assertEquals(DeviceMaker.SAMSUNG, BatteryGuide.makerOf("samsung"))
        assertEquals(DeviceMaker.VIVO, BatteryGuide.makerOf("vivo"))
        assertEquals(DeviceMaker.VIVO, BatteryGuide.makerOf("iQOO"))
    }

    @Test fun others_and_blank_get_the_general_steps_only() {
        assertEquals(DeviceMaker.OTHER, BatteryGuide.makerOf("Google"))
        assertEquals(DeviceMaker.OTHER, BatteryGuide.makerOf("HUAWEI", "HONOR"))
        assertEquals(DeviceMaker.OTHER, BatteryGuide.makerOf(null))
        assertEquals(DeviceMaker.OTHER, BatteryGuide.makerOf("  "))
    }

    @Test fun matching_ignores_case_and_spaces() {
        assertEquals(DeviceMaker.SAMSUNG, BatteryGuide.makerOf("  SAMSUNG "))
    }

    private class Flag { var seen = false }

    @Test fun guide_opens_by_itself_only_the_first_time() {
        val f = Flag()
        val once = ShowOnce({ f.seen }, { f.seen = true })
        assertTrue(once.consume())
        assertFalse(once.consume())
        assertFalse(once.consume())
    }

    @Test fun opening_the_guide_by_hand_uses_up_the_automatic_one() {
        val f = Flag()
        val once = ShowOnce({ f.seen }, { f.seen = true })
        once.markSeen()
        assertTrue(f.seen)
        assertFalse(once.consume())
    }

    @Test fun a_flag_remembered_from_an_earlier_run_is_respected() {
        val f = Flag().apply { seen = true }
        assertFalse(ShowOnce({ f.seen }, { f.seen = true }).consume())
    }
}
