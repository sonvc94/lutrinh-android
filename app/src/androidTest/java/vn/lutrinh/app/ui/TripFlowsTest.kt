package vn.lutrinh.app.ui

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.lutrinh.app.LuTrinhApp
import vn.lutrinh.app.MainActivity
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.TransportMode
import vn.lutrinh.app.domain.usecase.SaveResult
import vn.lutrinh.app.domain.usecase.StepInput
import vn.lutrinh.app.domain.usecase.TripInput
import java.time.LocalDate

/**
 * End-to-end UI flows on a device (docs/03-screens.md). Every trip made here starts with
 * [PREFIX] and is removed again in [cleanUp], so the tests can run on a device with real data.
 */
@RunWith(AndroidJUnit4::class)
class TripFlowsTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val title = "$PREFIX ${System.nanoTime() % 100_000}"

    @After
    fun cleanUp() = runBlocking {
        val c = ApplicationProvider.getApplicationContext<LuTrinhApp>().container
        c.trips.observeTripSummaries().first()
            .filter { it.trip.title.startsWith(PREFIX) }
            .forEach { c.deleteTrip(it.trip.id) }
    }

    @Test
    fun createTrip_opensItsDetailAndListsIt() {
        createTrip(title)
        waitForText("Chưa có điểm dừng")
        rule.onAllNodesWithText(title).onFirst().assertExists()

        rule.onNodeWithContentDescription("Quay lại").performClick()
        waitForText("Chuyến đi của tôi")
        waitForText(title)
    }

    /** R16: the trip screen offers "Ghi hành trình" (not tapped: it would ask for location). */
    @Test
    fun tripDetail_showsTheRecordTrackControl() {
        createTrip(title)
        waitForText("Chưa có điểm dừng")
        rule.waitUntil(TIMEOUT) { clickable("Ghi hành trình").fetchSemanticsNodes().isNotEmpty() }
        clickable("Ghi hành trình").onFirst().assertExists()
    }

    @Test
    fun editTrip_changesTheTitle() {
        createTrip(title)
        waitForText("Chưa có điểm dừng")
        rule.onNodeWithContentDescription("Thêm tùy chọn").performClick()
        rule.onNodeWithText("Sửa chuyến đi").performClick()

        val field = rule.onNode(hasSetTextAction() and hasText("Tên chuyến đi"))
        field.performTextClearance()
        field.performTextInput("$title (đã sửa)")
        clickable("Lưu").onFirst().performClick()

        waitForText("$title (đã sửa)")
        waitForText("Chưa có điểm dừng") // back on the trip detail
    }

    @Test
    fun deleteTrip_removesItFromTheList() {
        createTrip(title)
        waitForText("Chưa có điểm dừng")
        rule.onNodeWithContentDescription("Thêm tùy chọn").performClick()
        rule.onNodeWithText("Xóa chuyến đi").performClick()
        waitForText("Xóa chuyến đi này?")
        rule.onNodeWithText("Xóa").performClick()

        waitForText("Chuyến đi của tôi")
        rule.waitUntil(TIMEOUT) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun addStep_withQuickPlaceChip_showsItInTheTimeline() {
        createTrip(title)
        waitForText("Chưa có điểm dừng")
        clickable("Thêm điểm dừng").onFirst().performClick()

        waitForText("Điểm dừng mới")
        rule.onNode(hasSetTextAction() and hasText("Tên địa điểm")).performTextInput("Đèo Mã Pí Lèng")
        rule.onNodeWithText("Chọn trên bản đồ").performClick()

        waitForText("Dùng vị trí này")
        rule.onNodeWithText("Mã Pí Lèng").performClick()
        // The centre coordinate updates when the camera animation to the chip's place ends.
        waitForText("23.23970, 105.40960")
        rule.onNodeWithText("Dùng vị trí này").performClick()

        waitForText("23.23970, 105.40960") // shown on the location card of the form
        clickable("Lưu").onFirst().performClick()

        waitForText(title)
        waitForText("Đèo Mã Pí Lèng")
        rule.waitUntil(TIMEOUT) { rule.onAllNodesWithText("Chưa có điểm dừng").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun setLegTransport_fromTheTimeline_showsAndStoresTheMode() {
        val secondStepId = seedTripWithTwoSteps(title)
        rule.onNodeWithText(title).performClick()
        waitForText("Hồ Gươm")
        clickable("Chọn cách di chuyển", substring = true).onFirst().performClick()

        waitForText("Từ Hồ Gươm đến Tràng An")
        rule.onNodeWithText("Xe máy").performClick()

        waitForText("Xe máy ·")
        rule.waitUntil(TIMEOUT) {
            runBlocking { container().steps.getStep(secondStepId)?.arrivalTransport } == TransportMode.MOTORBIKE
        }
    }

    @Test
    fun openMyMap_fromTheTripList_showsTotalsAndTrips() {
        seedTripWithTwoSteps(title)
        rule.onNodeWithContentDescription("Bản đồ của tôi").performClick()

        waitForText("Bản đồ của tôi")
        waitForText("chuyến đi")
        waitForText("điểm dừng")
        waitForText(title) // newest trip first in the card row
    }

    @Test
    fun openOfflineMaps_fromTheTripListMenu_listsTheReadyMadeRegions() {
        rule.waitUntil(TIMEOUT) { rule.onAllNodesWithContentDescription("Chuyến đi mới").fetchSemanticsNodes().isNotEmpty() }
        // Trip cards have a "Thêm tùy chọn" menu too; the top bar's one is the highest on screen.
        val menus = rule.onAllNodesWithContentDescription("Thêm tùy chọn")
        val top = menus.fetchSemanticsNodes().withIndex().minBy { it.value.boundsInRoot.top }.index
        menus[top].performClick()
        rule.onNodeWithText("Bản đồ offline").performClick()

        waitForText("Vùng có sẵn")
        waitForText("Hà Giang")
        waitForText("Mèo Vạc") // the Hà Giang loop's places
        waitForText("Ninh Bình")
        waitForText("Cúc Phương")
        // Each region shows its estimate ("Tải về") or, when the device already has it, its state.
        val statuses = listOf("Ước tính ~", "Đã tải · ", "Đang tải ", "Đã tạm dừng · ", "Mất mạng · ")
        try {
            rule.waitUntil(TIMEOUT) {
                statuses.sumOf { rule.onAllNodes(hasText(it, substring = true)).fetchSemanticsNodes().size } >= 2
            }
        } catch (e: ComposeTimeoutException) {
            rule.onRoot(useUnmergedTree = false).printToLog("TripFlowsTest")
            throw AssertionError("No status line for both ready-made regions", e)
        }
    }

    @Test
    fun openOfflineMaps_fromATripMap_offersTheTripArea() {
        seedTripWithTwoSteps(title)
        rule.onAllNodesWithText(title).onFirst().performClick()
        waitForText("Hồ Gươm")
        rule.onAllNodesWithContentDescription("Xem bản đồ").onFirst().performClick()
        rule.waitUntil(TIMEOUT) { rule.onAllNodesWithContentDescription("Bản đồ offline").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Bản đồ offline").performClick()

        waitForText("Tải vùng của chuyến đi này")
        waitForText(title)
        waitForText("Hà Giang")
        waitForText("Ninh Bình")
    }

    /** Creates a trip (today) with two steps through the app container; returns the second step id. */
    private fun seedTripWithTwoSteps(name: String): String = runBlocking {
        val c = container()
        val today = LocalDate.now()
        val tripId = (c.saveTrip(null, TripInput(name, "", today, null)) as SaveResult.Saved).id
        c.saveStep(tripId, null, StepInput("Hồ Gươm", "", GeoPoint(21.0287, 105.8524), today.atTime(8, 0), emptyList()))
        val second = c.saveStep(tripId, null, StepInput("Tràng An", "", GeoPoint(20.2546, 105.9161), today.atTime(11, 0), emptyList()))
        rule.waitUntil(TIMEOUT) { rule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        (second as SaveResult.Saved).id
    }

    private fun container() = ApplicationProvider.getApplicationContext<LuTrinhApp>().container

    private fun createTrip(name: String) {
        rule.waitUntil(TIMEOUT) { rule.onAllNodesWithContentDescription("Chuyến đi mới").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Chuyến đi mới").performClick()
        rule.onNode(hasSetTextAction() and hasText("Tên chuyến đi")).performTextInput(name)
        clickable("Tạo chuyến đi").onFirst().performClick() // the button, not the top bar title
    }

    private fun clickable(text: String, substring: Boolean = false) =
        rule.onAllNodesWithText(text, substring = substring).filter(hasClickAction())

    private fun waitForText(text: String) {
        try {
            rule.waitUntil(TIMEOUT) { rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            rule.onRoot(useUnmergedTree = false).printToLog("TripFlowsTest")
            throw AssertionError("Text not shown within ${TIMEOUT}ms: \"$text\"", e)
        }
    }

    private companion object {
        const val PREFIX = "UITEST"
        const val TIMEOUT = 15_000L
    }
}
