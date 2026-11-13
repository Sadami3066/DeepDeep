package io.github.whywood.deepdeep.plugin.host.extensions.device

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.DisplayMetrics
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.plugin.api.DeviceInfo
import io.github.whywood.deepdeep.plugin.api.DeviceService
import io.github.whywood.deepdeep.plugin.api.DeviceSettings
import io.github.whywood.deepdeep.plugin.api.DeviceState
import io.github.whywood.deepdeep.runtime.host.HostAccessState
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

internal class DevicePluginService(private val deps: DeviceServiceDeps) : DeviceService {
    private val context: Context get() = deps.context
    private val appContext: Context get() = deps.appContext
    private val live = MutableStateFlow(readInitialSnapshot())
    private val installationId = MutableStateFlow("")
    private val receiverRegistered = AtomicBoolean(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> update(
                    DeviceLiveEvent.Battery(
                        level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                        scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
                        charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
                    ),
                )

                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> update(
                    DeviceLiveEvent.PowerSave(powerManager()?.isPowerSaveMode == true),
                )

                Intent.ACTION_SCREEN_ON -> update(DeviceLiveEvent.ScreenOn(true))

                Intent.ACTION_SCREEN_OFF -> update(DeviceLiveEvent.ScreenOn(false))

                Intent.ACTION_USER_PRESENT -> update(DeviceLiveEvent.Locked(false))

                ConnectivityManager.CONNECTIVITY_ACTION -> update(readNetworkEvent())
            }
        }
    }

    init {
        registerReceiver()
        deps.scope.launch {
            installationId.value = runCatching { deps.installationId() }.getOrDefault("")
        }
        deps.scope.coroutineContext[Job]?.invokeOnCompletion { unregisterReceiver() }
    }

    override val info: DeviceInfo
        get() = DeviceInfo(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            emulator = isEmulator(),
            language = languageTag(),
            timezone = timeZoneId(),
            screenWidthPx = displayMetrics().widthPixels,
            screenHeightPx = displayMetrics().heightPixels,
            density = displayMetrics().density,
            refreshRate = refreshRate(),
            totalMemoryBytes = memoryInfo()?.totalMem ?: 0L,
            totalStorageBytes = storageStats().first,
            installationId = installationId.value,
        )

    override val state: DeviceState
        get() = buildState(live.value)

    override fun observe(): Flow<DeviceState> = combine(
        live,
        deps.hostAccess,
        ticker(),
    ) { snapshot, access, _ ->
        buildState(snapshot, access)
    }.distinctUntilChanged()

    override fun vibrate(durationMs: Long) {
        val duration = durationMs.coerceIn(MIN_VIBRATION_MS, MAX_VIBRATION_MS)
        runCatching {
            val vibrator = vibrator() ?: return
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun openSettings(section: DeviceSettings): Boolean =
        openSettingsScreen(context, settingsScreenOf(section))

    private fun update(event: DeviceLiveEvent) {
        live.value = reduceDeviceLive(live.value, event)
    }

    private fun buildState(
        snapshot: DeviceLiveSnapshot,
        access: HostAccessState = deps.hostAccess.value,
    ): DeviceState = DeviceState(
        batteryPercent = snapshot.batteryPercent,
        charging = snapshot.charging,
        powerSave = snapshot.powerSave,
        networkType = snapshot.networkType,
        connected = snapshot.connected,
        screenOn = snapshot.screenOn,
        locked = snapshot.locked,
        language = languageTag(),
        timezone = timeZoneId(),
        uptimeMs = SystemClock.elapsedRealtime(),
        freeMemoryBytes = memoryInfo()?.availMem ?: 0L,
        freeStorageBytes = storageStats().second,
        hostAccess = hostAccessLevelOf(access.effective),
    )

    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(STATE_TICK_MILLIS)
        }
    }

    private fun readInitialSnapshot(): DeviceLiveSnapshot {
        var snapshot = DeviceLiveSnapshot(
            powerSave = powerManager()?.isPowerSaveMode == true,
            screenOn = powerManager()?.isInteractive != false,
            locked = runCatching { keyguardLocked() }.getOrDefault(false),
        )
        runCatching {
            @Suppress("DEPRECATION")
            val sticky = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (sticky != null) {
                snapshot = reduceDeviceLive(
                    snapshot,
                    DeviceLiveEvent.Battery(
                        level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                        scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
                        charging = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
                    ),
                )
            }
        }
        return reduceDeviceLive(snapshot, readNetworkEvent())
    }

    private fun readNetworkEvent(): DeviceLiveEvent.Network = runCatching {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return DeviceLiveEvent.Network(DEVICE_NETWORK_NONE, false)
        val network = manager.activeNetwork ?: return DeviceLiveEvent.Network(DEVICE_NETWORK_NONE, false)
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return DeviceLiveEvent.Network(DEVICE_NETWORK_NONE, false)
        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            else -> "other"
        }
        DeviceLiveEvent.Network(type, true)
    }.getOrDefault(DeviceLiveEvent.Network(DEVICE_NETWORK_NONE, false))

    private fun registerReceiver() {
        if (receiverRegistered.get()) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        val registered = runCatching {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.isSuccess
        if (registered) receiverRegistered.set(true)
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered.compareAndSet(true, false)) return
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    private fun displayMetrics(): DisplayMetrics = context.resources.displayMetrics

    private fun refreshRate(): Float = runCatching {
        val manager = appContext.getSystemService(DisplayManager::class.java) ?: return DEFAULT_REFRESH_RATE
        @Suppress("DEPRECATION")
        val display = manager.getDisplay(Display.DEFAULT_DISPLAY) ?: return DEFAULT_REFRESH_RATE
        display.refreshRate.takeIf { it > 0f } ?: DEFAULT_REFRESH_RATE
    }.getOrDefault(DEFAULT_REFRESH_RATE)

    private fun memoryInfo(): ActivityManager.MemoryInfo? = runCatching {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
    }.getOrNull()

    private fun storageStats(): Pair<Long, Long> = runCatching {
        @Suppress("DEPRECATION")
        val stat = StatFs(appContext.filesDir.absolutePath)
        stat.totalBytes to stat.availableBytes
    }.getOrDefault(0L to 0L)

    private fun languageTag(): String = runCatching {
        val locales = context.resources.configuration.locales
        if (locales.isEmpty) "und" else locales[0].toLanguageTag()
    }.getOrDefault("und")

    private fun timeZoneId(): String = runCatching { TimeZone.getDefault().id }.getOrDefault("UTC")

    private fun powerManager(): PowerManager? =
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private fun keyguardLocked(): Boolean = runCatching {
        val manager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        manager?.isKeyguardLocked ?: false
    }.getOrDefault(false)

    private fun vibrator(): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(VibratorManager::class.java) ?: return null
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.orEmpty()
        val model = Build.MODEL.orEmpty()
        val hardware = Build.HARDWARE.orEmpty()
        val product = Build.PRODUCT.orEmpty()
        return fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("Emulator") ||
            model.contains("Android SDK built for") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            product.contains("sdk")
    }

    private companion object {
        const val STATE_TICK_MILLIS = 15_000L
        const val MIN_VIBRATION_MS = 1L
        const val MAX_VIBRATION_MS = 10_000L
        const val DEFAULT_REFRESH_RATE = 60f
    }
}
