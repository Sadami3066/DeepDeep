package io.github.whywood.deepdeep.shell

import java.io.File
import java.io.OutputStream
import java.util.UUID

internal class CommandOutput(private val capacity: Int, private val logDir: File) : AutoCloseable {
    private val tail = ByteArray(capacity)
    private var start = 0
    private var size = 0
    private var bytes = 0L
    private var newlines = 0L
    private var lastByte: Byte? = null
    private var log: OutputStream? = null
    var logFile: File? = null
        private set

    fun append(buffer: ByteArray, count: Int) {
        bytes += count
        newlines += (0 until count).count { buffer[it] == 10.toByte() }
        if (count > 0) lastByte = buffer[count - 1]
        val lines = newlines + if (lastByte == 10.toByte() || bytes == 0L) 0 else 1
        if (log == null && (bytes > minOf(capacity, 51200) || lines > 2000)) {
            check(logDir.isDirectory || logDir.mkdirs()) { "Cannot create command log directory: $logDir" }
            logFile = File(logDir, "command-${UUID.randomUUID()}.log")
            log = logFile!!.outputStream().buffered().also { it.write(tailBytes()) }
        }
        log?.write(buffer, 0, count)
        if (count >= capacity) {
            buffer.copyInto(tail, 0, count - capacity, count)
            start = 0
            size = capacity
        } else {
            for (i in 0 until count) {
                if (size < capacity) {
                    tail[(start + size) % capacity] = buffer[i]
                    size++
                } else {
                    tail[start] = buffer[i]
                    start = (start + 1) % capacity
                }
            }
        }
    }

    private fun tailBytes(): ByteArray = ByteArray(size) { tail[(start + it) % capacity] }

    fun text(): String {
        val data = tailBytes()
        var offset = 0
        if (bytes > size) while (offset < data.size && data[offset].toInt() and 0xC0 == 0x80) offset++
        return data.copyOfRange(offset, data.size).toString(Charsets.UTF_8)
    }

    val truncated: Boolean get() = bytes > size

    override fun close() { log?.close() }
}
