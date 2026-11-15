package io.github.whywood.deepdeep.screen

import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.SystemClock
import com.wangzan101.mika.cap.phone.a11y.PhoneOcr
import com.wangzan101.mika.cap.phone.a11y.StillFrameRead
import com.wangzan101.mika.cap.phone.display.FrameMath
import com.wangzan101.mika.core.prompt.Feedback
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import com.wangzan101.mika.core.uistate.TreeAdequacy
import com.wangzan101.mika.core.uistate.TreeQuality
import com.wangzan101.mika.core.uistate.UiTreeNode
import com.wangzan101.mika.core.uistate.VisibleText
import com.wangzan101.mika.core.uistate.VisibleTextLine
import com.wangzan101.mika.core.uistate.normalize
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val OCR_WAIT_MS = 2_000L
private const val OCR_WAIT_POOR_MS = 5_000L
private val recognition = Mutex()
private val recognitionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

internal fun isPhongentFrameBlack(frame: Bitmap?): Boolean = frame?.sample(FrameMath::isBlack) == true

internal suspend fun readPhongentVisibleText(
    snapshot: ScreenSnapshot,
    focus: List<Int>? = null,
    preview: () -> Bitmap? = { null },
    capture: suspend () -> ScreenCapture?,
): JsonObject = phongentVisibleTextFields(snapshot, focus) { crop ->
    withContext(Dispatchers.Default) {
        if (preview()?.sample(FrameMath::isBlack) == true) return@withContext null
        StillFrameRead.read(SystemClock::uptimeMillis, { delay(it) }, {
            preview()?.sample(FrameMath::lumaGrid)
        }) {
            if (preview()?.sample(FrameMath::isBlack) == true || !recognition.tryLock()) {
                null
            } else {
                val frame = try {
                    capture()
                } catch (error: Throwable) {
                    recognition.unlock()
                    throw error
                }
                if (frame == null) {
                    recognition.unlock()
                    null
                } else {
                    recognitionScope.async {
                        try {
                            recognizeCrop(frame.png, crop)
                        } finally {
                            recognition.unlock()
                        }
                    }.await()
                }
            }
        }
    }
}

internal suspend fun phongentVisibleTextFields(
    snapshot: ScreenSnapshot,
    focus: List<Int>? = null,
    read: suspend (List<Int>) -> List<VisibleTextLine>?,
): JsonObject {
    val tree = TreeAdequacy.ofTree(snapshot)
    if (snapshot.protected) return buildJsonObject {
        put("protected", true)
        put("readable", false)
    }
    if (!snapshot.hasWindow || snapshot.web != null || tree.quality == TreeQuality.Adequate
    ) return JsonObject(emptyMap())

    val crop = visibleTextCrop(snapshot, focus)
    var completed = false
    var status: String? = null
    val raw = try {
        withTimeoutOrNull(if (tree.poor) OCR_WAIT_POOR_MS else OCR_WAIT_MS) {
            read(crop).also { completed = true }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        status = "failed"
        null
    }
    val missing = when {
        raw == null -> {
            status = status ?: if (completed) "unavailable" else "timeout"
            null
        }
        raw.isEmpty() -> {
            status = "empty"
            raw
        }
        else -> {
            val legible = VisibleText.legible(raw)
            if (legible.isEmpty()) status = "illegible"
            VisibleText.missing(snapshot, legible)
        }
    }
    val reviewed = TreeAdequacy.withText(tree, missing)
    return buildJsonObject {
        if (reviewed.poor) {
            put("treeQuality", reviewed.quality!!.wire)
            reviewed.reason?.let { put("imageReason", it) }
            if (missing.isNullOrEmpty()) status?.let { put("ocr", it) }
            val hint = if (missing.isNullOrEmpty() && status != null) {
                Feedback.format("tree_poor.no_text", "ocr" to status)
            } else {
                Feedback["tree_poor"]
            }.replace(
                "its center is a tap point on phone_tap's 0–999 x/y grid. Use element refs where they exist; tap text that has none with phone_tap x/y at its center.",
                "its center is normalized to 0–999. Use element refs where they exist. To tap OCR-only text, convert its center to target screen pixels for screen_tap."
            ).replace("call hand_to_user", "report the blocker")
            if (hint.isNotEmpty()) put("hint", hint)
        } else if (raw == null && status != null) {
            put("ocr", status)
        }
        if (!missing.isNullOrEmpty()) put("visibleText", VisibleText.json(missing))
        put("ocrBounds", JsonArray(crop.map(::JsonPrimitive)))
    }
}

internal fun visibleTextCrop(snapshot: ScreenSnapshot, focus: List<Int>?): List<Int> {
    val named = snapshot.elements.filter { it.name.isNotBlank() && !it.secret }
    val appWindows = snapshot.windows.filter { it.type == "application" && it.packageName == snapshot.packageName }
    val roots = appWindows.maxWithOrNull(compareBy({ it.focused || it.active }, { it.layer }))?.nodes.orEmpty()
    val gaps = mutableListOf<List<Int>>()
    fun visit(node: UiTreeNode): Boolean {
        if (node.secret) return true
        var known = !node.text.isNullOrBlank() || !node.description.isNullOrBlank() || !node.hintText.isNullOrBlank()
        for (child in node.children) if (visit(child)) known = true
        if (!known && usableBox(node.bounds) && area(node.bounds) in 18_000..850_000 &&
            named.count {
                normalize(it.centerX, snapshot.width) in node.bounds[0]..node.bounds[2] &&
                    normalize(it.centerY, snapshot.height) in node.bounds[1]..node.bounds[3]
            } < 3
        ) gaps += node.bounds
        return known
    }
    roots.forEach(::visit)
    val viewport = roots.map { it.bounds }.filter { usableBox(it) && area(it) >= 18_000 }
        .maxByOrNull(::area) ?: listOf(0, 0, 999, 999)
    val hole = gaps.maxByOrNull(::area) ?: viewport
    val anchor = if (focus != null && focus.size == 4) focus else hole
    val x = (anchor[0] + anchor[2]) / 2
    val y = (anchor[1] + anchor[3]) / 2
    val width = (anchor[2] - anchor[0] + 140).coerceIn(minOf(460, viewport[2] - viewport[0]), minOf(960, viewport[2] - viewport[0]))
    val height = (anchor[3] - anchor[1] + 140).coerceIn(minOf(280, viewport[3] - viewport[1]), minOf(680, viewport[3] - viewport[1]))
    val left = (x - width / 2).coerceIn(viewport[0], viewport[2] - width)
    val top = (y - height / 2).coerceIn(viewport[1], viewport[3] - height)
    return listOf(left, top, left + width, top + height)
}

private fun usableBox(box: List<Int>) =
    box.size == 4 && box.all { it in 0..999 } && box[2] > box[0] && box[3] > box[1]

private fun area(box: List<Int>) = (box[2] - box[0]) * (box[3] - box[1])

private inline fun <T> Bitmap.sample(read: (Int, Int, (Int, Int) -> Int) -> T): T? =
    runCatching { read(width, height, ::getPixel) }.getOrNull()

@Suppress("DEPRECATION")
private suspend fun recognizeCrop(png: ByteArray, crop: List<Int>): List<VisibleTextLine>? {
    val decoder = BitmapRegionDecoder.newInstance(png, 0, png.size, false) ?: return null
    val width = decoder.width
    val height = decoder.height
    fun pixel(value: Int, size: Int) = (value * (size - 1) / 999.0).roundToInt()
    val region = Rect(
        pixel(crop[0], width), pixel(crop[1], height), pixel(crop[2], width) + 1, pixel(crop[3], height) + 1,
    )
    val bitmap = try {
        decoder.decodeRegion(region, null)
    } finally {
        decoder.recycle()
    } ?: return null
    return PhoneOcr.read(bitmap).map { line ->
        line.copy(bounds = line.bounds.mapIndexed { index, value ->
            if (index % 2 == 0) normalize(region.left + pixel(value, region.width()), width)
            else normalize(region.top + pixel(value, region.height()), height)
        })
    }
}
