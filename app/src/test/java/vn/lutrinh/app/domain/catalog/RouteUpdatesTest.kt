package vn.lutrinh.app.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** R40: update check at most once a day, online, shared routes only; the sheet once per version; D-4 replace. */
class RouteUpdatesTest {
    private val day = RouteUpdates.CHECK_EVERY_MS
    private val link = RouteLink("t", "rt_5f3c9a1e", RouteCode.of("HG4N7KQE")!!, 3, "x", 0L, checkedAt = 1_000L)

    @Test fun checkIsDueOncePerDayOnlineAndWhileShared() {
        assertFalse(RouteUpdates.due(link, 1_000L + day - 1, online = true))
        assertTrue(RouteUpdates.due(link, 1_000L + day, online = true))
        assertFalse(RouteUpdates.due(link, 1_000L + day, online = false))
        assertTrue(RouteUpdates.due(link.copy(checkedAt = null), 0L, online = true))
        assertFalse(RouteUpdates.due(link.copy(remoteStatus = RemoteStatus.REVOKED, checkedAt = null), 0L, online = true))
    }

    @Test fun aNewerVersionShowsTheSheetUntilLater() {
        val ref = LinkRef(link.code, link.routeId, link.version)
        val newer = RouteStatus(ref, RemoteStatus.ACTIVE, RouteChange(4, null, listOf("Du Già"), emptyList(), 0, 1, 10, null))
        val after = RouteUpdates.apply(link, newer, 5L)
        assertEquals(5L to 4, after.checkedAt to after.latestVersion)
        assertTrue(after.hasNewer)
        assertTrue(RouteUpdates.showSheet(after))
        assertFalse(RouteUpdates.showSheet(after.copy(skippedVersion = 4)))
        // A still newer version opens it again.
        assertTrue(RouteUpdates.showSheet(after.copy(skippedVersion = 4, latestVersion = 5)))
        // "current" keeps what we knew.
        assertEquals(4, RouteUpdates.apply(after, RouteStatus(ref, RemoteStatus.ACTIVE), 6L).latestVersion)
    }

    @Test fun unpublishedRevokedAndExpiredAreRemembered() {
        val ref = LinkRef(link.code, link.routeId, link.version)
        assertEquals(RemoteStatus.UNPUBLISHED, RouteUpdates.apply(link, RouteStatus(ref, RemoteStatus.UNPUBLISHED), 1).remoteStatus)
        assertEquals(RemoteStatus.REVOKED, RouteUpdates.apply(link, RouteStatus(ref, RemoteStatus.REVOKED), 1).remoteStatus)
        assertEquals(RemoteStatus.REVOKED, RouteUpdates.apply(link, RouteStatus(ref, RemoteStatus.EXPIRED), 1).remoteStatus)
    }

    @Test fun replaceOnlyWhileUnedited() {
        assertTrue(RouteUpdates.canReplace(link))
        assertFalse(RouteUpdates.canReplace(link.copy(edited = true)))
    }

    @Test fun badgeAndDefaults() {
        assertEquals("Lộ trình HG4N-7KQE · v3", link.badge)
        // Default start date: the next Saturday (D-5 picker opens on it).
        assertEquals(LocalDate.of(2026, 11, 14), RouteImport.defaultStartDate(LocalDate.of(2026, 11, 10)))
        assertEquals(LocalDate.of(2026, 11, 21), RouteImport.defaultStartDate(LocalDate.of(2026, 11, 14)))
        assertEquals(LocalDate.of(2026, 11, 17), RouteImport.endDate(LocalDate.of(2026, 11, 14), 4))
        assertEquals(16L * 1024 * 1024 + 200, RouteDownload.neededBytes(100))
    }
}
