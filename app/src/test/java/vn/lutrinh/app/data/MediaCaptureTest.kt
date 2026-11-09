package vn.lutrinh.app.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** R28/R29: the system camera writes into a temporary file of ours; whatever happens, it is gone afterwards. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MediaCaptureTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val capture = MediaCapture(ctx)
    private lateinit var photos: File
    private val captureDir get() = File(ctx.cacheDir, MediaCapture.DIR)

    @Before fun setUp() {
        photos = File(ctx.filesDir, "photos-capture-test").apply { deleteRecursively(); mkdirs() }
        File(photos, "existing.jpg").writeText("kept")
    }

    @After fun tearDown() { photos.deleteRecursively(); captureDir.deleteRecursively() }

    /** Stands in for PhotoStorage.import: copies the camera's file into the photo folder. */
    private val imports = mutableListOf<Uri>()
    private val import: suspend (Uri) -> String? = { uri ->
        imports += uri
        val name = "stored-${imports.size}.jpg"
        File(uri.path!!).copyTo(File(photos, name))
        name
    }

    private fun photoCount() = photos.listFiles()!!.size
    private fun captureCount() = captureDir.listFiles()?.size ?: 0

    @Test fun aCancelledCaptureLeavesNoFile() = runTest {
        val target = capture.newTarget(MediaCapture.Kind.PHOTO)
        assertTrue(target.exists())
        assertEquals(1, captureCount())
        target.writeText("half-written by the camera")

        assertNull(capture.finish(target, ok = false, import = import))
        assertTrue(imports.isEmpty())
        assertFalse(target.exists())
        assertEquals(0, captureCount())
        assertEquals(1, photoCount())
    }

    @Test fun anOkResultWithNothingWrittenLeavesNoFile() = runTest {
        val target = capture.newTarget(MediaCapture.Kind.VIDEO)
        assertTrue(target.name.endsWith(".mp4"))
        assertNull(capture.finish(target, ok = true, import = import))
        assertTrue(imports.isEmpty())
        assertEquals(0, captureCount())
        assertEquals(1, photoCount())
    }

    @Test fun aTakenPhotoIsImportedAndTheTemporaryFileRemoved() = runTest {
        val target = capture.newTarget(MediaCapture.Kind.PHOTO)
        target.writeText("jpeg bytes")
        assertEquals("stored-1.jpg", capture.finish(target, ok = true, import = import))
        assertEquals(listOf(Uri.fromFile(target)), imports)
        assertEquals("jpeg bytes", File(photos, "stored-1.jpg").readText())
        assertEquals(0, captureCount())
        assertEquals(2, photoCount())
    }

    @Test fun aCameraThatReturnsItsOwnUriIsImportedFromThere() = runTest {
        val own = File(ctx.cacheDir, "camera-own.mp4").apply { writeText("video bytes") }
        val target = capture.newTarget(MediaCapture.Kind.VIDEO)
        assertEquals("stored-1.jpg", capture.finish(target, ok = true, fallback = Uri.fromFile(own), import = import))
        assertEquals(listOf(Uri.fromFile(own)), imports)
        assertEquals(0, captureCount())
        own.delete()
    }

    @Test fun aFailedImportStillRemovesTheTemporaryFile() = runTest {
        val target = capture.newTarget(MediaCapture.Kind.PHOTO).apply { writeText("not an image") }
        assertNull(capture.finish(target, ok = true) { null })
        assertEquals(0, captureCount())
        assertEquals(1, photoCount())
    }

    @Test fun leftoversAreClearedAndOnlyPlainNamesResolve() = runTest {
        capture.newTarget(MediaCapture.Kind.PHOTO); capture.newTarget(MediaCapture.Kind.VIDEO)
        assertEquals(2, captureCount())
        capture.clearLeftovers()
        assertEquals(0, captureCount())
        assertEquals(File(captureDir, "a.jpg"), capture.target("a.jpg"))
        assertNull(capture.target("../databases/lutrinh.db"))
        assertNull(capture.target("x/y.jpg"))
        assertNull(capture.target(""))
    }
}
