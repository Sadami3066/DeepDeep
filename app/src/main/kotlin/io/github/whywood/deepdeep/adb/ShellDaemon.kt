package io.github.whywood.deepdeep.adb

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.lsposed.hiddenapibypass.HiddenApiBypass

@SuppressLint("PrivateApi", "BlockedPrivateApi")
object ShellDaemon {
    private const val HELPER_CLASS = "io.github.whywood.deepdeep.vscreen.VirtualScreenHelperMain"
    private const val LOG_FILE = "/data/local/tmp/deepdeep-vscreen.log"
    private var token = ""

    private val shellContext: Context by lazy {
        val activityThread = Class.forName("android.app.ActivityThread")
        val thread = activityThread.getMethod("systemMain").invoke(null)
        val system = activityThread.getMethod("getSystemContext").invoke(thread) as Context
        system.createPackageContext("com.android.shell", 0)
    }

    private val inputManager: Any by lazy {
        val className = if (android.os.Build.VERSION.SDK_INT >= 34) {
            "android.hardware.input.InputManagerGlobal"
        } else {
            "android.hardware.input.InputManager"
        }
        Class.forName(className).getMethod("getInstance").invoke(null)
            ?: error("InputManager unavailable")
    }

    private val injectMethod by lazy {
        inputManager.javaClass.getMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType,
        )
    }

    @JvmStatic
    fun main(args: Array<String>) {
        runCatching { Looper.prepareMainLooper() }
        runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
        val port = args.getOrNull(0)?.toIntOrNull() ?: 18242
        token = args.getOrNull(1) ?: ""
        require(token.length >= 8) { "token too short" }
        val server = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
        println("SHD_READY port=$port pid=${android.os.Process.myPid()} uid=${android.os.Process.myUid()}")
        System.out.flush()
        while (true) {
            val client = runCatching { server.accept() }.getOrNull() ?: continue
            thread(isDaemon = true) {
                try {
                    handleClient(client)
                } catch (failure: Throwable) {
                    failure.printStackTrace()
                } finally {
                    runCatching { client.close() }
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.soTimeout = 30_000
        val input = socket.getInputStream()
        val requestLine = readAsciiLine(input) ?: return
        val path = requestLine.split(" ").getOrNull(1) ?: "/"
        var contentLength = 0
        var requestToken = ""
        var timeoutSeconds = DEFAULT_TIMEOUT_SECONDS
        while (true) {
            val line = readAsciiLine(input) ?: break
            if (line.isEmpty()) break
            when {
                line.startsWith("content-length:", ignoreCase = true) ->
                    contentLength = line.substring(15).trim().toIntOrNull() ?: 0

                line.startsWith("x-token:", ignoreCase = true) ->
                    requestToken = line.substring(8).trim()

                line.startsWith("x-timeout:", ignoreCase = true) ->
                    timeoutSeconds = (line.substring(10).trim().toLongOrNull() ?: timeoutSeconds)
                        .coerceIn(1, MAX_TIMEOUT_SECONDS)
            }
        }
        if (requestToken != token) {
            sendHttp(socket, 403, "DENIED\n")
            return
        }
        val body = readBody(input, contentLength)
        val route = path.substringBefore('?')
        val (code, response) = try {
            when (route) {
                "/health" -> 200 to "uid=${android.os.Process.myUid()} ok\n"
                "/exec" -> 200 to execShell(body, timeoutSeconds)
                "/tap" -> 200 to handleTap(path)
                "/swipe" -> 200 to handleSwipe(path)
                "/settings" -> 200 to handleSettings(path)
                "/start-helper" -> 200 to handleStartHelper(path)
                "/kill-helper" -> 200 to handleKillHelper(path)
                else -> 404 to "not found\n"
            }
        } catch (failure: Throwable) {
            500 to "ERR: ${failure.message}\n"
        }
        sendHttp(socket, code, response)
    }

    private fun execShell(command: String, timeoutSeconds: Long): String {
        if (command.isBlank()) return "[EXIT=2]\n"
        val builder = ProcessBuilder("/system/bin/setsid", "/system/bin/sh", "-c", command)
        builder.environment().remove("CLASSPATH")
        builder.redirectErrorStream(true)
        val process = builder.start()
        val output = StringBuilder()
        val reader = thread(isDaemon = true) {
            runCatching {
                process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    synchronized(output) {
                        if (output.length < MAX_OUTPUT_CHARS) {
                            output.append(line).append('\n')
                        }
                    }
                }
            }
        }
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            val pid = process.processId()
            if (pid > 0) {
                runCatching { android.system.Os.kill(-pid, android.system.OsConstants.SIGKILL) }
            }
            runCatching { process.destroyForcibly() }
            reader.join(500)
            return "[TIMEOUT]\n[EXIT=-9]\n"
        }
        reader.join(1000)
        val text = synchronized(output) { output.toString() }
        return "$text[EXIT=${process.exitValue()}]\n"
    }

    private fun handleTap(path: String): String {
        val x = queryParam(path, "x")?.toFloatOrNull() ?: return "need x\n"
        val y = queryParam(path, "y")?.toFloatOrNull() ?: return "need y\n"
        val now = SystemClock.uptimeMillis()
        injectMotion(MotionEvent.ACTION_DOWN, now, now, x, y)
        injectMotion(MotionEvent.ACTION_UP, now, now + 15, x, y)
        return "ok\n"
    }

    private fun handleSwipe(path: String): String {
        val x1 = queryParam(path, "x1")?.toFloatOrNull() ?: return "need x1\n"
        val y1 = queryParam(path, "y1")?.toFloatOrNull() ?: return "need y1\n"
        val x2 = queryParam(path, "x2")?.toFloatOrNull() ?: return "need x2\n"
        val y2 = queryParam(path, "y2")?.toFloatOrNull() ?: return "need y2\n"
        val duration = (queryParam(path, "dur")?.toLongOrNull() ?: 300L).coerceIn(50L, 5000L)
        val downTime = SystemClock.uptimeMillis()
        injectMotion(MotionEvent.ACTION_DOWN, downTime, downTime, x1, y1)
        val steps = (duration / 16).toInt().coerceIn(2, 60)
        for (index in 1..steps) {
            SystemClock.sleep(16)
            val fraction = index.toFloat() / steps
            injectMotion(
                MotionEvent.ACTION_MOVE,
                downTime,
                SystemClock.uptimeMillis(),
                x1 + (x2 - x1) * fraction,
                y1 + (y2 - y1) * fraction,
            )
        }
        injectMotion(MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(), x2, y2)
        return "ok\n"
    }

    private fun injectMotion(action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            injectMethod.invoke(inputManager, event, 2)
        } finally {
            event.recycle()
        }
    }

    private fun handleSettings(path: String): String {
        val namespace = queryParam(path, "ns") ?: "system"
        val key = queryParam(path, "key") ?: return "need key\n"
        val value = queryParam(path, "value")
        val resolver = shellContext.contentResolver
        return when (namespace) {
            "global" -> if (value == null) {
                "${Settings.Global.getString(resolver, key)}\n"
            } else {
                Settings.Global.putString(resolver, key, value)
                "ok\n"
            }

            "secure" -> if (value == null) {
                "${Settings.Secure.getString(resolver, key)}\n"
            } else {
                Settings.Secure.putString(resolver, key, value)
                "ok\n"
            }

            else -> if (value == null) {
                "${Settings.System.getString(resolver, key)}\n"
            } else {
                Settings.System.putString(resolver, key, value)
                "ok\n"
            }
        }
    }

    private fun handleStartHelper(path: String): String {
        val apk = queryParam(path, "apk") ?: return "need apk\n"
        val port = queryParam(path, "port")?.toIntOrNull() ?: return "need port\n"
        val nonce = queryParam(path, "nonce") ?: return "need nonce\n"
        val builder = ProcessBuilder(
            "/system/bin/app_process",
            "/system/bin",
            "--nice-name=deepdeep-vscreen",
            HELPER_CLASS,
            "--port",
            port.toString(),
            "--nonce",
            nonce,
        )
        builder.environment()["CLASSPATH"] = apk
        builder.redirectErrorStream(true)
        builder.redirectOutput(File(LOG_FILE))
        val process = builder.start()
        return "pid=${process.processId()}\n"
    }

    private fun handleKillHelper(path: String): String {
        val pid = queryParam(path, "pid")?.toIntOrNull() ?: return "need pid\n"
        val process = ProcessBuilder("/system/bin/kill", "-9", pid.toString()).start()
        process.waitFor(2, TimeUnit.SECONDS)
        return "ok\n"
    }

    private fun readBody(input: InputStream, length: Int): String {
        if (length <= 0) return ""
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(bytes, read, length - read)
            if (count < 0) break
            read += count
        }
        return String(bytes, 0, read, Charsets.UTF_8)
    }

    private fun readAsciiLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream(128)
        while (true) {
            val value = input.read()
            if (value == -1) return if (buffer.size() == 0) null else buffer.toString("UTF-8")
            if (value == '\n'.code) break
            if (value != '\r'.code) buffer.write(value)
        }
        return buffer.toString("UTF-8")
    }

    private fun queryParam(path: String, key: String): String? {
        val queryIndex = path.indexOf('?')
        if (queryIndex < 0) return null
        return path.substring(queryIndex + 1).split("&").firstNotNullOfOrNull { item ->
            val separator = item.indexOf('=')
            if (separator > 0 && item.substring(0, separator) == key) {
                URLDecoder.decode(item.substring(separator + 1), "UTF-8")
            } else {
                null
            }
        }
    }

    private fun sendHttp(socket: Socket, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code OK\r\nContent-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(header.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }

    private fun Process.processId(): Int = runCatching {
        javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(this)
    }.getOrDefault(-1)

    private const val DEFAULT_TIMEOUT_SECONDS = 20L
    private const val MAX_TIMEOUT_SECONDS = 120L
    private const val MAX_OUTPUT_CHARS = 2 * 1024 * 1024
}
