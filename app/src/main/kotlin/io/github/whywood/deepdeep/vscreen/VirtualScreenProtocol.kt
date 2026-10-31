package io.github.whywood.deepdeep.vscreen

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class VirtualScreenFrameKind(val code: Byte) {
    TEXT(1),
    BINARY(2),
    SCREENSHOT(3),
    ;

    companion object {
        fun fromCode(code: Byte): VirtualScreenFrameKind? =
            entries.firstOrNull { it.code == code }
    }
}

class VirtualScreenFrame(
    val kind: VirtualScreenFrameKind,
    val payload: ByteArray,
)

object VirtualScreenProtocol {
    const val MAX_FRAME_BYTES = 8 * 1024 * 1024

    private const val HEADER_BYTES = 5

    fun text(json: JsonObject): VirtualScreenFrame =
        VirtualScreenFrame(VirtualScreenFrameKind.TEXT, json.toString().toByteArray(Charsets.UTF_8))

    fun binary(bytes: ByteArray): VirtualScreenFrame = VirtualScreenFrame(VirtualScreenFrameKind.BINARY, bytes)

    fun screenshot(bytes: ByteArray): VirtualScreenFrame =
        VirtualScreenFrame(VirtualScreenFrameKind.SCREENSHOT, bytes)

    fun json(frame: VirtualScreenFrame): JsonObject {
        if (frame.kind != VirtualScreenFrameKind.TEXT) {
            throw IllegalArgumentException("not a text frame")
        }
        val element = Json.parseToJsonElement(frame.payload.decodeToString())
        return element as? JsonObject ?: throw IllegalArgumentException("frame is not a json object")
    }

    fun write(output: OutputStream, frame: VirtualScreenFrame) {
        val header = ByteArray(HEADER_BYTES)
        val length = frame.payload.size
        header[0] = (length ushr 24).toByte()
        header[1] = (length ushr 16).toByte()
        header[2] = (length ushr 8).toByte()
        header[3] = length.toByte()
        header[4] = frame.kind.code
        output.write(header)
        output.write(frame.payload)
        output.flush()
    }

    fun read(input: InputStream): VirtualScreenFrame? {
        val header = ByteArray(HEADER_BYTES)
        var filled = 0
        while (filled < HEADER_BYTES) {
            val count = input.read(header, filled, HEADER_BYTES - filled)
            if (count < 0) {
                if (filled == 0) return null
                throw IOException("连接在帧头中间结束")
            }
            filled += count
        }
        val length = ((header[0].toInt() and 0xFF) shl 24) or
            ((header[1].toInt() and 0xFF) shl 16) or
            ((header[2].toInt() and 0xFF) shl 8) or
            (header[3].toInt() and 0xFF)
        if (length <= 0 || length > MAX_FRAME_BYTES) {
            throw IOException("帧长度非法：$length")
        }
        val kind = VirtualScreenFrameKind.fromCode(header[4])
            ?: throw IOException("未知帧类型：${header[4]}")
        val payload = ByteArray(length)
        var payloadFilled = 0
        while (payloadFilled < length) {
            val count = input.read(payload, payloadFilled, length - payloadFilled)
            if (count < 0) throw IOException("连接在帧内容中间结束")
            payloadFilled += count
        }
        return VirtualScreenFrame(kind, payload)
    }
}

object VirtualScreenCommands {
    const val HELLO = "hello"
    const val PING = "ping"
    const val START = "start"
    const val STOP = "stop"
    const val LAUNCH = "launch"
    const val TAP = "tap"
    const val SWIPE = "swipe"
    const val KEY = "key"
    const val TEXT = "text"
    const val SCREENSHOT = "screenshot"

    const val SHELL_UID = 2000
    const val DEFAULT_WIDTH = 720
    const val DEFAULT_HEIGHT = 1280
    const val DEFAULT_DPI = 320
    const val STREAM_WIDTH = 360
}

internal fun JsonObject.stringField(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.intField(name: String): Int? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

internal fun JsonObject.longField(name: String): Long? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

internal fun JsonObject.booleanField(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

internal fun Throwable.readableMessage(): String {
    val cause = cause ?: this
    val text = cause.message
    return if (text.isNullOrBlank()) cause.javaClass.simpleName else text
}
