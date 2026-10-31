package io.github.whywood.deepdeep.adb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

class LocalAdbManager(context: Context) : AbsAdbConnectionManager() {
    private val credentials: AdbCredentials =
        AdbKeyStore(File(context.filesDir, AdbKeyStore.DIRECTORY_NAME)).loadOrCreate()

    init {
        setApi(Build.VERSION.SDK_INT)
        setHostAddress(LOOPBACK)
        setTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        setThrowOnUnauthorised(true)
    }

    override fun getPrivateKey(): PrivateKey = credentials.privateKey

    override fun getCertificate(): Certificate = credentials.certificate

    override fun getDeviceName(): String = DEVICE_NAME

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val CONNECT_TIMEOUT_SECONDS = 8L
        const val DEVICE_NAME = "DeepDeep"
    }
}
