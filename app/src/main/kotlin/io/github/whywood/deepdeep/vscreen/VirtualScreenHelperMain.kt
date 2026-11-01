package io.github.whywood.deepdeep.vscreen

import android.app.ActivityOptions
import android.app.Application
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "VScreenHelper"

object VirtualScreenHelperMain {
    private const val ARG_PORT = "--port"
    private const val ARG_NONCE = "--nonce"

    @JvmStatic
    fun main(args: Array<String>) {
        runCatching { Looper.prepareMainLooper() }
        val port = optionValue(args, ARG_PORT)?.toIntOrNull()
        val nonce = optionValue(args, ARG_NONCE)
        if (port == null || port !in 1..65535 || nonce.isNullOrBlank()) {
            Log.e(TAG, "缺少 --port 或 --nonce 参数")
            return
        }
        val code = try {
            HelperServer(port, nonce).run()
        } catch (t: Throwable) {
            Log.e(TAG, "helper 运行失败", t)
            1
        }
        exitProcess(code)
    }

    private fun optionValue(args: Array<String>, name: String): String? {
        val index = args.indexOf(name)
        return if (index >= 0 && index + 1 < args.size) args[index + 1] else null
    }
}

private class HelperServer(private val port: Int, private val nonce: String) {
    private val writeLock = Any()
    private val frameQueue = LinkedBlockingQueue<VirtualScreenFrame>(FRAME_QUEUE_CAPACITY)
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var session: DisplaySession? = null
    private var frameThread: Thread? = null
    private var writerThread: Thread? = null
    private var lastFrameDropLogAt = 0L
    private var systemContext: Context? = null
    @Volatile
    private var running = true

    fun run(): Int {
        val client = connectToApp() ?: run {
            Log.e(TAG, "连接 App 超时：127.0.0.1:$port")
            println("VSH_EXIT pid=${Process.myPid()} reason=connect_timeout")
            shutdown()
            return 2
        }
        socket = client
        input = BufferedInputStream(client.getInputStream())
        output = BufferedOutputStream(client.getOutputStream())
        println("VSH_START pid=${Process.myPid()} port=$port sdk=${Build.VERSION.SDK_INT}")
        sendJson(
            buildJsonObject {
                put("event", VirtualScreenCommands.HELLO)
                put("uid", Process.myUid())
                put("pid", Process.myPid())
                put("sdk", Build.VERSION.SDK_INT)
                put("nonce", nonce)
            },
        )
        var exitReason = "正常结束"
        try {
            if (Process.myUid() != VirtualScreenCommands.SHELL_UID) {
                exitReason = "需要以 shell(2000) 身份运行，当前 uid=${Process.myUid()}"
                Log.e(TAG, exitReason)
                return 3
            }
            while (running) {
                val stream = input ?: run {
                    exitReason = "连接已关闭"
                    break
                }
                val frame = try {
                    VirtualScreenProtocol.read(stream)
                } catch (t: IOException) {
                    exitReason = "读取客户端帧失败：${t.message}"
                    Log.w(TAG, exitReason)
                    break
                } ?: run {
                    exitReason = "客户端已断开"
                    break
                }
                if (frame.kind != VirtualScreenFrameKind.TEXT) continue
                val json = runCatching { VirtualScreenProtocol.json(frame) }.getOrNull() ?: continue
                if (!handle(json)) {
                    exitReason = "收到停止命令"
                    break
                }
            }
        } finally {
            shutdown()
            Log.i(TAG, "helper 退出：$exitReason")
            println("VSH_EXIT pid=${Process.myPid()} reason=$exitReason")
        }
        return 0
    }

    private fun connectToApp(): Socket? {
        val deadline = SystemClock.uptimeMillis() + CONNECT_TIMEOUT_MS
        val address = InetSocketAddress(LOOPBACK, port)
        while (SystemClock.uptimeMillis() < deadline) {
            val candidate = Socket()
            try {
                candidate.connect(address, CONNECT_ATTEMPT_TIMEOUT_MS)
                return candidate
            } catch (t: IOException) {
                runCatching { candidate.close() }
                try {
                    Thread.sleep(CONNECT_RETRY_MS)
                } catch (e: InterruptedException) {
                    return null
                }
            }
        }
        return null
    }

    private fun handle(json: JsonObject): Boolean {
        val id = json.intField("id") ?: 0
        val command = json.stringField("cmd")
        if (command == null) {
            respondError(id, "缺少 cmd")
            return true
        }
        try {
            when (command) {
                VirtualScreenCommands.PING -> respond(id) { put("pong", true) }
                VirtualScreenCommands.START -> handleStart(id)
                VirtualScreenCommands.STOP -> {
                    respond(id) {}
                    running = false
                    return false
                }
                VirtualScreenCommands.LAUNCH -> handleLaunch(json, id)
                VirtualScreenCommands.TAP -> handleTap(json, id)
                VirtualScreenCommands.SWIPE -> handleSwipe(json, id)
                VirtualScreenCommands.KEY -> handleKey(json, id)
                VirtualScreenCommands.TEXT -> handleText(json, id)
                VirtualScreenCommands.SCREENSHOT -> handleScreenshot(id)
                else -> respondError(id, "未知命令：$command")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "$command 执行失败", t)
            respondError(id, messageOf(t))
        }
        return true
    }

    private fun handleStart(id: Int) {
        if (session != null) throw IllegalStateException("虚拟屏已启动")
        val screen = realScreenMetrics()
        val size = portraitScreenSize(screen.widthPixels, screen.heightPixels)
        val dpi = screen.densityDpi
        val display = DisplaySession(context(), size.width, size.height, dpi)
        val displayId = display.start()
        session = display
        startFrameThread(display)
        respond(id) {
            put("displayId", displayId)
            put("width", size.width)
            put("height", size.height)
            put("dpi", dpi)
        }
    }

    @Suppress("DEPRECATION")
    private fun realScreenMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val manager = DisplayManager::class.java
            .getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }
            .newInstance(context())
        manager.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics)
        return metrics
    }

    private fun handleLaunch(json: JsonObject, id: Int) {
        val display = session ?: throw IllegalStateException("虚拟屏未启动")
        val packageName = json.stringField("packageName")?.trim().orEmpty()
        if (packageName.isEmpty()) throw IllegalArgumentException("缺少 packageName")
        val conflict = occupancyConflict(packageName, display.displayId)
        if (conflict != null) throw IllegalStateException(conflict)
        launchOnDisplay(packageName, display.displayId)
        val taskDisplayId = taskDisplayOf(packageName)
        val landedOnMain = taskDisplayId == Display.DEFAULT_DISPLAY
        if (taskDisplayId != display.displayId) {
            Log.w(TAG, "$packageName 未落在虚拟屏：taskDisplayId=$taskDisplayId，displayId=${display.displayId}")
        }
        respond(id) {
            put("packageName", packageName)
            put("displayId", display.displayId)
            put("taskDisplayId", taskDisplayId)
            put("landedOnMain", landedOnMain)
            put("message", virtualLaunchMessage(packageName, display.displayId, taskDisplayId))
        }
    }

    private fun handleTap(json: JsonObject, id: Int) {
        val display = session ?: throw IllegalStateException("虚拟屏未启动")
        val displayId = display.displayId
        val x = json.intField("x") ?: throw IllegalArgumentException("缺少 x")
        val y = json.intField("y") ?: throw IllegalArgumentException("缺少 y")
        outOfBoundsReason("点击", x, y, display.width, display.height)?.let {
            throw IllegalArgumentException(it)
        }
        val duration = (json.longField("durationMs") ?: DEFAULT_TAP_MILLIS).coerceIn(0L, MAX_TAP_MILLIS)
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0)
        if (!inject(down, displayId)) {
            down.recycle()
            throw IllegalStateException("按下事件注入失败：($x, $y) display=$displayId")
        }
        down.recycle()
        if (duration > 0) Thread.sleep(duration)
        val upTime = SystemClock.uptimeMillis()
        val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0)
        if (!inject(up, displayId)) {
            up.recycle()
            throw IllegalStateException("抬起事件注入失败：($x, $y) display=$displayId")
        }
        up.recycle()
        respond(id) { put("message", "已点击 ($x, $y)") }
    }

    private fun handleSwipe(json: JsonObject, id: Int) {
        val display = session ?: throw IllegalStateException("虚拟屏未启动")
        val displayId = display.displayId
        val x1 = json.intField("x1") ?: throw IllegalArgumentException("缺少 x1")
        val y1 = json.intField("y1") ?: throw IllegalArgumentException("缺少 y1")
        val x2 = json.intField("x2") ?: throw IllegalArgumentException("缺少 x2")
        val y2 = json.intField("y2") ?: throw IllegalArgumentException("缺少 y2")
        outOfBoundsReason("滑动起点", x1, y1, display.width, display.height)?.let {
            throw IllegalArgumentException(it)
        }
        outOfBoundsReason("滑动终点", x2, y2, display.width, display.height)?.let {
            throw IllegalArgumentException(it)
        }
        val duration = (json.longField("durationMs") ?: DEFAULT_SWIPE_MILLIS).coerceIn(50L, MAX_SWIPE_MILLIS)
        val steps = (duration / 16).toInt().coerceIn(2, 60)
        val stepDelay = (duration / steps).coerceAtLeast(8L)
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x1.toFloat(), y1.toFloat(), 0)
        if (!inject(down, displayId)) {
            down.recycle()
            throw IllegalStateException("按下事件注入失败：($x1, $y1) display=$displayId")
        }
        down.recycle()
        for (index in 1..steps) {
            val fraction = index.toFloat() / steps
            val x = x1 + (x2 - x1) * fraction
            val y = y1 + (y2 - y1) * fraction
            val eventTime = downTime + duration * index / steps
            val move = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, x, y, 0)
            val injected = inject(move, displayId)
            move.recycle()
            if (!injected) throw IllegalStateException("滑动事件注入失败：(${x.toInt()}, ${y.toInt()}) display=$displayId")
            Thread.sleep(stepDelay)
        }
        val upTime = SystemClock.uptimeMillis()
        val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x2.toFloat(), y2.toFloat(), 0)
        if (!inject(up, displayId)) {
            up.recycle()
            throw IllegalStateException("抬起事件注入失败：($x2, $y2) display=$displayId")
        }
        up.recycle()
        respond(id) { put("message", "已滑动 ($x1, $y1) → ($x2, $y2)") }
    }

    private fun handleKey(json: JsonObject, id: Int) {
        val displayId = requireDisplayId()
        val name = json.stringField("key")?.trim()?.lowercase()
            ?: throw IllegalArgumentException("缺少 key")
        val keyCode = KEY_CODES[name]
            ?: throw IllegalArgumentException("不支持的按键：$name；可用：${KEY_CODES.keys.joinToString(", ")}")
        injectKey(displayId, keyCode)
        respond(id) { put("message", "已按键 $name") }
    }

    private fun handleText(json: JsonObject, id: Int) {
        val displayId = requireDisplayId()
        val value = json.stringField("text") ?: throw IllegalArgumentException("缺少 text")
        if (value.isEmpty()) throw IllegalArgumentException("text 不能为空")
        val map = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        val events = map.getEvents(value.toCharArray())
        if (events.isNullOrEmpty()) throw IllegalStateException("无法输入该文本，只支持键盘可映射的 ASCII 字符")
        var injected = 0
        for (event in events) {
            if (event.keyCode == 0) continue
            if (!inject(event, displayId)) throw IllegalStateException("文本事件注入失败")
            injected++
        }
        if (injected == 0) throw IllegalStateException("无法输入该文本，只支持键盘可映射的 ASCII 字符")
        respond(id) { put("message", "已输入 ${injected / 2} 个字符") }
    }

    private fun handleScreenshot(id: Int) {
        val display = session ?: throw IllegalStateException("虚拟屏未启动")
        val png = display.screenshotPng() ?: throw IllegalStateException("暂无画面，请稍后再试")
        if (screenshotFrameExceedsLimit(png.size)) {
            throw IllegalStateException("截图过大：${png.size} 字节，超过帧上限 ${VirtualScreenProtocol.MAX_FRAME_BYTES}")
        }
        respond(id) {
            put("kind", "screenshot")
            put("width", display.width)
            put("height", display.height)
            put("sizeBytes", png.size)
        }
        writeFrame(VirtualScreenProtocol.screenshot(png))
    }

    private fun requireDisplayId(): Int = session?.displayId ?: throw IllegalStateException("虚拟屏未启动")

    private fun taskDisplayOf(packageName: String): Int {
        repeat(TASK_DISPLAY_RETRIES) { attempt ->
            val tasks = runCatching { tasksOf(packageName) }.getOrElse {
                Log.w(TAG, "读取任务列表失败", it)
                return INVALID_TASK_DISPLAY
            }
            val visible = tasks.firstOrNull { it.visible }
            val displayId = visible?.displayId ?: tasks.firstOrNull()?.displayId
            if (displayId != null) return displayId
            if (attempt < TASK_DISPLAY_RETRIES - 1) {
                try {
                    Thread.sleep(TASK_DISPLAY_RETRY_MS)
                } catch (t: InterruptedException) {
                    return INVALID_TASK_DISPLAY
                }
            }
        }
        return INVALID_TASK_DISPLAY
    }

    private fun occupancyConflict(packageName: String, displayId: Int): String? {
        val tasks = runCatching { tasksOf(packageName) }.getOrElse {
            Log.w(TAG, "读取任务列表失败", it)
            return null
        }
        for (task in tasks) {
            if (task.displayId == displayId) continue
            if (task.displayId == Display.DEFAULT_DISPLAY) {
                if (task.visible) {
                    return "$packageName 正在主屏使用，未在虚拟屏启动；请先离开该应用"
                }
                continue
            }
            return "$packageName 已在另一块屏幕上运行，未在虚拟屏启动"
        }
        return null
    }

    private fun tasksOf(packageName: String): List<TaskEntry> {
        val service = taskManager()
        val methods = service.javaClass.methods.filter { it.name == "getTasks" }
        val tasks: List<*> = when {
            methods.any { it.parameterTypes.size == 4 } -> methods.first { it.parameterTypes.size == 4 }
                .invoke(service, MAX_TASKS, false, false, Display.INVALID_DISPLAY) as List<*>
            methods.any { it.parameterTypes.size == 3 } -> methods.first { it.parameterTypes.size == 3 }
                .invoke(service, MAX_TASKS, false, false) as List<*>
            else -> return emptyList()
        }
        val taskClass = Class.forName("android.app.TaskInfo")
        val displayField = taskClass.getField("displayId")
        val visibleField = runCatching { taskClass.getField("isVisible") }.getOrNull()
        val baseField = runCatching { taskClass.getField("baseActivity") }.getOrNull()
        val topField = runCatching { taskClass.getField("topActivity") }.getOrNull()
        return tasks.filterNotNull().mapNotNull { task ->
            val packages = listOfNotNull(baseField?.get(task), topField?.get(task))
                .filterIsInstance<ComponentName>()
                .map { it.packageName }
            if (packageName !in packages) return@mapNotNull null
            TaskEntry(
                displayId = displayField.getInt(task),
                visible = visibleField?.getBoolean(task) ?: false,
            )
        }
    }

    private fun taskManager(): Any =
        Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)

    private fun launchOnDisplay(packageName: String, displayId: Int) {
        val intent = context().packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalStateException("找不到应用：$packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
        val started = runCatching { startActivityAsUser(intent, options) }.getOrElse {
            Log.w(TAG, "startActivityAsUser 失败，改用 am", it)
            false
        }
        if (started) return
        val component = intent.component
            ?: throw IllegalStateException("无法解析启动入口：$packageName")
        val code = exec(
            "am",
            "start",
            "--display",
            displayId.toString(),
            "-n",
            component.flattenToShortString(),
        )
        if (code != 0) throw IllegalStateException("启动失败（am 退出码 $code）：$packageName")
    }

    private fun startActivityAsUser(intent: Intent, options: Bundle): Boolean {
        val service = taskManager()
        val method = service.javaClass.methods.firstOrNull {
            it.name == "startActivityAsUser" && it.parameterTypes.size == 12
        } ?: return false
        val result = method.invoke(
            service,
            null,
            SHELL_PACKAGE,
            null,
            intent,
            null,
            null,
            null,
            0,
            0,
            null,
            options,
            USER_CURRENT,
        ) as Int
        return result >= 0
    }

    private fun injectKey(displayId: Int, keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()
        val down = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        if (!inject(down, displayId)) throw IllegalStateException("按键按下事件注入失败")
        val up = KeyEvent(downTime, downTime + 40, KeyEvent.ACTION_UP, keyCode, 0)
        if (!inject(up, displayId)) throw IllegalStateException("按键抬起事件注入失败")
    }

    private fun inject(event: InputEvent, displayId: Int): Boolean {
        event.javaClass.getMethod("setDisplayId", Int::class.javaPrimitiveType).invoke(event, displayId)
        when (event) {
            is MotionEvent -> event.source = InputDevice.SOURCE_TOUCHSCREEN
            is KeyEvent -> event.javaClass
                .getMethod("setSource", Int::class.javaPrimitiveType)
                .invoke(event, InputDevice.SOURCE_KEYBOARD)
        }
        val manager = inputManager()
        val method = manager.javaClass.getMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType,
        )
        return method.invoke(manager, event, INJECT_MODE_WAIT_FOR_FINISH) as Boolean
    }

    private fun inputManager(): Any {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                Class.forName("android.hardware.input.InputManagerGlobal")
                    .getMethod("getInstance")
                    .invoke(null)
            }.getOrNull()?.let { return it }
        }
        return Class.forName("android.hardware.input.InputManager")
            .getMethod("getInstance")
            .invoke(null)
    }

    private fun exec(vararg command: String): Int {
        val process = try {
            ProcessBuilder(*command).redirectErrorStream(true).start()
        } catch (t: Throwable) {
            Log.w(TAG, "${command.first()} 启动失败", t)
            return -1
        }
        return try {
            if (!process.waitFor(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                -1
            } else {
                process.exitValue()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "${command.first()} 等待失败", t)
            -1
        }
    }

    private fun startFrameThread(display: DisplaySession) {
        val writer = Thread({
            while (running && session === display) {
                val frame = try {
                    frameQueue.poll(FRAME_WRITE_POLL_MS, TimeUnit.MILLISECONDS)
                } catch (t: InterruptedException) {
                    null
                } ?: continue
                try {
                    if (!writeFrame(frame)) break
                } catch (t: Throwable) {
                    Log.w(TAG, "写入预览帧失败", t)
                    break
                }
            }
        }, "vscreen-writer")
        writer.isDaemon = true
        writer.start()
        writerThread = writer
        val encoder = Thread({
            while (running && session === display) {
                val started = SystemClock.uptimeMillis()
                try {
                    val jpeg = display.streamJpeg(VirtualScreenCommands.STREAM_WIDTH)
                    if (jpeg != null && !enqueueFrame(VirtualScreenProtocol.binary(jpeg))) break
                } catch (t: Throwable) {
                    Log.w(TAG, "编码预览帧失败", t)
                }
                val elapsed = SystemClock.uptimeMillis() - started
                if (elapsed < FRAME_INTERVAL_MS) {
                    try {
                        Thread.sleep(FRAME_INTERVAL_MS - elapsed)
                    } catch (t: InterruptedException) {
                        break
                    }
                }
            }
        }, "vscreen-frames")
        encoder.isDaemon = true
        encoder.start()
        frameThread = encoder
    }

    private fun context(): Context = systemContext ?: createSystemContext().also { systemContext = it }

    private fun createSystemContext(): Context {
        val threadClass = Class.forName("android.app.ActivityThread")
        val thread = threadClass.getMethod("currentActivityThread").invoke(null)
            ?: newActivityThread(threadClass)
        val system = threadClass.getDeclaredMethod("getSystemContext").invoke(thread) as Context
        val wrapper = ShellContext(system)
        val field = threadClass.getDeclaredField("mInitialApplication").apply { isAccessible = true }
        if (field.get(thread) == null) {
            runCatching {
                field.set(thread, Instrumentation.newApplication(Application::class.java, wrapper))
            }
        }
        return wrapper
    }

    private fun newActivityThread(threadClass: Class<*>): Any {
        val thread = threadClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        threadClass.getDeclaredField("sCurrentActivityThread").apply { isAccessible = true }.set(null, thread)
        threadClass.getDeclaredField("mSystemThread").apply { isAccessible = true }.setBoolean(thread, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val configurationClass = Class.forName("android.app.ConfigurationController")
                val internalClass = Class.forName("android.app.ActivityThreadInternal")
                val controller = configurationClass
                    .getDeclaredConstructor(internalClass)
                    .apply { isAccessible = true }
                    .newInstance(thread)
                threadClass.getDeclaredField("mConfigurationController")
                    .apply { isAccessible = true }
                    .set(thread, controller)
            }
        }
        runCatching {
            val bindClass = Class.forName("android.app.ActivityThread\$AppBindData")
            val bind = bindClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            bindClass.getDeclaredField("appInfo").apply { isAccessible = true }
                .set(bind, ApplicationInfo().apply { packageName = ShellContext.SHELL_PACKAGE })
            threadClass.getDeclaredField("mBoundApplication").apply { isAccessible = true }.set(thread, bind)
        }
        return thread
    }

    private fun respond(id: Int, block: JsonObjectBuilder.() -> Unit) {
        sendJson(
            buildJsonObject {
                put("id", id)
                put("ok", true)
                block()
            },
        )
    }

    private fun respondError(id: Int, message: String) {
        sendJson(
            buildJsonObject {
                put("id", id)
                put("ok", false)
                put("error", message)
            },
        )
    }

    private fun sendJson(json: JsonObject): Boolean = writeFrame(VirtualScreenProtocol.text(json))

    private fun enqueueFrame(frame: VirtualScreenFrame): Boolean {
        if (!running) return false
        if (frameQueue.offer(frame)) return true
        val now = SystemClock.uptimeMillis()
        if (now - lastFrameDropLogAt >= FRAME_DROP_LOG_INTERVAL_MS) {
            lastFrameDropLogAt = now
            Log.w(TAG, "预览帧积压，丢弃新帧")
        }
        return true
    }

    private fun writeFrame(frame: VirtualScreenFrame): Boolean {
        synchronized(writeLock) {
            val stream = output ?: return false
            return try {
                VirtualScreenProtocol.write(stream, frame)
                true
            } catch (t: IOException) {
                running = false
                false
            }
        }
    }

    private fun shutdown() {
        running = false
        frameThread?.interrupt()
        writerThread?.interrupt()
        runCatching { frameThread?.join(FRAME_THREAD_JOIN_MS) }
        runCatching { writerThread?.join(FRAME_THREAD_JOIN_MS) }
        frameQueue.clear()
        session?.release()
        session = null
        runCatching { socket?.close() }
    }

    private class TaskEntry(val displayId: Int, val visible: Boolean)

    private class FrameSnapshot(
        val width: Int,
        val height: Int,
        val rowStride: Int,
        val pixelStride: Int,
        val pixels: ByteArray,
    )

    private class ShellContext(base: Context) : ContextWrapper(base) {
        override fun getPackageName(): String = SHELL_PACKAGE

        override fun getOpPackageName(): String = SHELL_PACKAGE

        @android.annotation.TargetApi(Build.VERSION_CODES.S)
        override fun getAttributionSource(): android.content.AttributionSource =
            android.content.AttributionSource.Builder(VirtualScreenCommands.SHELL_UID)
                .setPackageName(SHELL_PACKAGE)
                .build()

        override fun getApplicationContext(): Context = this

        companion object {
            const val SHELL_PACKAGE = "com.android.shell"
        }
    }

    private class DisplaySession(
        private val context: Context,
        val width: Int,
        val height: Int,
        val dpi: Int,
    ) {
        private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        private val imageLock = Any()
        private var display: VirtualDisplay? = null
        private var snapshot: FrameSnapshot? = null
        private var snapshotAtUptime = 0L
        private var frameBuffer: Bitmap? = null
        private var previewBuffer: Bitmap? = null

        val displayId: Int
            get() = display?.display?.displayId ?: -1

        fun start(): Int {
            val manager = DisplayManager::class.java
                .getDeclaredConstructor(Context::class.java)
                .apply { isAccessible = true }
                .newInstance(context)
            val virtual = manager.createVirtualDisplay(
                DISPLAY_NAME,
                width,
                height,
                dpi,
                reader.surface,
                displayFlags(),
            )
            display = virtual
            val id = virtual.display.displayId
            runCatching { applyImePolicy(id) }
            return id
        }

        fun streamJpeg(targetWidth: Int): ByteArray? {
            val image = tryAcquire() ?: return null
            return try {
                cacheSnapshotIfDue(image)
                encodeJpeg(image, targetWidth)
            } finally {
                image.close()
            }
        }

        private fun encodeJpeg(image: Image, maxWidth: Int): ByteArray {
            val plane = image.planes[0]
            val paddedWidth = plane.rowStride / plane.pixelStride
            val frame = frameBuffer?.takeIf { it.width == paddedWidth && it.height == image.height }
                ?: Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888).also {
                    frameBuffer?.recycle()
                    frameBuffer = it
                }
            frame.copyPixelsFromBuffer(plane.buffer.rewind())
            val targetWidth = maxWidth.coerceAtMost(image.width).coerceAtLeast(1)
            val targetHeight = (image.height * targetWidth / image.width).coerceAtLeast(1)
            val preview = previewBuffer?.takeIf { it.width == targetWidth && it.height == targetHeight }
                ?: Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also {
                    previewBuffer?.recycle()
                    previewBuffer = it
                }
            preview.eraseColor(Color.TRANSPARENT)
            Canvas(preview).drawBitmap(
                frame,
                Rect(0, 0, image.width, image.height),
                Rect(0, 0, targetWidth, targetHeight),
                SCALE_PAINT,
            )
            val out = ByteArrayOutputStream()
            preview.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            return out.toByteArray()
        }

        fun screenshotPng(): ByteArray? {
            val deadline = SystemClock.uptimeMillis() + SCREENSHOT_WAIT_MS
            while (true) {
                val image = tryAcquire()
                if (image != null) {
                    return try {
                        val bitmap = toBitmap(image)
                        try {
                            encodePng(bitmap)
                        } finally {
                            bitmap.recycle()
                        }
                    } finally {
                        image.close()
                    }
                }
                val cached = synchronized(imageLock) { snapshot }
                if (cached != null) {
                    val bitmap = bitmapFromSnapshot(cached)
                    return try {
                        encodePng(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
                if (SystemClock.uptimeMillis() >= deadline) return null
                Thread.sleep(50)
            }
        }

        private fun cacheSnapshotIfDue(image: Image) {
            val now = SystemClock.uptimeMillis()
            if (now - snapshotAtUptime < SNAPSHOT_CACHE_INTERVAL_MS) return
            val plane = image.planes[0]
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            buffer.rewind()
            synchronized(imageLock) {
                snapshot = FrameSnapshot(
                    width = image.width,
                    height = image.height,
                    rowStride = plane.rowStride,
                    pixelStride = plane.pixelStride,
                    pixels = bytes,
                )
            }
            snapshotAtUptime = now
        }

        private fun bitmapFromSnapshot(value: FrameSnapshot): Bitmap {
            val paddedWidth = value.rowStride / value.pixelStride
            val padded = Bitmap.createBitmap(paddedWidth, value.height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(ByteBuffer.wrap(value.pixels))
            if (paddedWidth == value.width) return padded
            val cropped = Bitmap.createBitmap(padded, 0, 0, value.width, value.height)
            padded.recycle()
            return cropped
        }

        private fun encodePng(bitmap: Bitmap): ByteArray {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            return out.toByteArray()
        }

        fun release() {
            runCatching { display?.release() }
            display = null
            runCatching { reader.close() }
            frameBuffer?.recycle()
            frameBuffer = null
            previewBuffer?.recycle()
            previewBuffer = null
            synchronized(imageLock) { snapshot = null }
        }

        private fun tryAcquire(): Image? = synchronized(imageLock) {
            runCatching { reader.acquireLatestImage() }.getOrNull()
        }

        private fun toBitmap(image: Image): Bitmap {
            val plane = image.planes[0]
            val paddedWidth = plane.rowStride / plane.pixelStride
            val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer.rewind())
            if (paddedWidth == image.width) return padded
            val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
            padded.recycle()
            return cropped
        }

        private fun displayFlags(): Int {
            var flags = FLAG_PUBLIC or FLAG_OWN_CONTENT_ONLY or FLAG_SUPPORTS_TOUCH or
                FLAG_ROTATES_WITH_CONTENT or FLAG_DESTROY_CONTENT_ON_REMOVAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                flags = flags or FLAG_TRUSTED or FLAG_OWN_DISPLAY_GROUP or FLAG_ALWAYS_UNLOCKED or
                    FLAG_TOUCH_FEEDBACK_DISABLED
            }
            return flags
        }

        private fun applyImePolicy(displayId: Int) {
            val windowManager = Class.forName("android.view.WindowManagerGlobal")
                .getMethod("getWindowManagerService")
                .invoke(null)
            windowManager.javaClass
                .getMethod("setDisplayImePolicy", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(windowManager, displayId, DISPLAY_IME_POLICY_LOCAL)
        }

        private companion object {
            const val DISPLAY_NAME = "deepdeep-vscreen"
            const val JPEG_QUALITY = 70
            const val SCREENSHOT_WAIT_MS = 3000L
            const val SNAPSHOT_CACHE_INTERVAL_MS = 1000L
            const val DISPLAY_IME_POLICY_LOCAL = 0
            const val FLAG_PUBLIC = 1 shl 0
            const val FLAG_OWN_CONTENT_ONLY = 1 shl 3
            const val FLAG_SUPPORTS_TOUCH = 1 shl 6
            const val FLAG_ROTATES_WITH_CONTENT = 1 shl 7
            const val FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
            const val FLAG_TRUSTED = 1 shl 10
            const val FLAG_OWN_DISPLAY_GROUP = 1 shl 11
            const val FLAG_ALWAYS_UNLOCKED = 1 shl 12
            const val FLAG_TOUCH_FEEDBACK_DISABLED = 1 shl 13
            val SCALE_PAINT = Paint(Paint.FILTER_BITMAP_FLAG)
        }
    }

    private companion object {
        const val SHELL_PACKAGE = "com.android.shell"
        const val USER_CURRENT = -2
        const val MAX_TASKS = 100
        const val INVALID_TASK_DISPLAY = -1
        const val TASK_DISPLAY_RETRIES = 3
        const val TASK_DISPLAY_RETRY_MS = 120L
        const val FRAME_INTERVAL_MS = 200L
        const val FRAME_QUEUE_CAPACITY = 2
        const val FRAME_WRITE_POLL_MS = 200L
        const val FRAME_DROP_LOG_INTERVAL_MS = 5000L
        const val FRAME_THREAD_JOIN_MS = 1000L
        const val EXEC_TIMEOUT_SECONDS = 10L
        const val LOOPBACK = "127.0.0.1"
        const val CONNECT_TIMEOUT_MS = 5000L
        const val CONNECT_ATTEMPT_TIMEOUT_MS = 500
        const val CONNECT_RETRY_MS = 100L
        const val INJECT_MODE_WAIT_FOR_FINISH = 2
        const val DEFAULT_TAP_MILLIS = 60L
        const val MAX_TAP_MILLIS = 3000L
        const val DEFAULT_SWIPE_MILLIS = 300L
        const val MAX_SWIPE_MILLIS = 5000L

        val KEY_CODES: Map<String, Int> = mapOf(
            "back" to KeyEvent.KEYCODE_BACK,
            "enter" to KeyEvent.KEYCODE_ENTER,
            "delete" to KeyEvent.KEYCODE_DEL,
            "tab" to KeyEvent.KEYCODE_TAB,
            "escape" to KeyEvent.KEYCODE_ESCAPE,
            "space" to KeyEvent.KEYCODE_SPACE,
            "arrow_up" to KeyEvent.KEYCODE_DPAD_UP,
            "arrow_down" to KeyEvent.KEYCODE_DPAD_DOWN,
            "arrow_left" to KeyEvent.KEYCODE_DPAD_LEFT,
            "arrow_right" to KeyEvent.KEYCODE_DPAD_RIGHT,
        )

        fun messageOf(t: Throwable): String {
            val cause = t.cause ?: t
            val message = cause.message
            return if (message.isNullOrBlank()) cause.javaClass.simpleName else message
        }
    }
}

internal data class PortraitScreenSize(val width: Int, val height: Int)

internal fun screenshotFrameExceedsLimit(sizeBytes: Int): Boolean =
    sizeBytes > VirtualScreenProtocol.MAX_FRAME_BYTES - SCREENSHOT_FRAME_HEADER_BYTES

private const val SCREENSHOT_FRAME_HEADER_BYTES = 5

internal fun portraitScreenSize(width: Int, height: Int): PortraitScreenSize =
    if (width <= height) PortraitScreenSize(width, height) else PortraitScreenSize(height, width)

internal fun virtualLaunchMessage(packageName: String, displayId: Int, taskDisplayId: Int): String =
    if (taskDisplayId == displayId) {
        "已在虚拟屏启动 $packageName"
    } else {
        "已启动 $packageName，但任务没有落在虚拟屏，请重新 screen_observe 确认真实页面"
    }

internal fun outOfBoundsReason(label: String, x: Int, y: Int, width: Int, height: Int): String? =
    if (x >= 0 && y >= 0 && x < width && y < height) {
        null
    } else {
        "${label}坐标超出虚拟屏范围：($x, $y)，虚拟屏 ${width}×${height}"
    }
