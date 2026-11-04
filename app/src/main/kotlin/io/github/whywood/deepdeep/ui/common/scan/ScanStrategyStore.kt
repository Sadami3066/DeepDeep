package io.github.whywood.deepdeep.ui.common.scan

import android.content.Context
import java.io.File

internal data class ScanStrategies(
    val strategies: List<ScanStrategy>,
    val fromFile: Boolean,
    val error: String?,
)

internal class ScanStrategyStore(context: Context) {
    private val appContext = context.applicationContext

    val file: File = File(File(appContext.filesDir, "scan"), "strategies.json")

    fun load(): ScanStrategies {
        val builtin = builtinText()?.let { ScanStrategyJson.parse(it) }
        if (file.isFile) {
            val text = runCatching { file.readText() }.getOrNull()
            if (text != null) {
                val parsed = ScanStrategyJson.parse(text)
                if (parsed != null) {
                    return ScanStrategies(parsed.strategies, fromFile = true, error = parsed.error)
                }
                return ScanStrategies(
                    strategies = builtin?.strategies.orEmpty(),
                    fromFile = false,
                    error = "策略文件无法解析，已使用内置策略",
                )
            }
        }
        return ScanStrategies(
            strategies = builtin?.strategies.orEmpty(),
            fromFile = false,
            error = builtin?.error,
        )
    }

    fun save(strategies: List<ScanStrategy>) {
        file.parentFile?.mkdirs()
        file.writeText(ScanStrategyJson.render(strategies))
    }

    fun reset() {
        file.delete()
    }

    private fun builtinText(): String? = runCatching {
        appContext.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
    }.getOrNull()

    private companion object {
        const val ASSET_PATH = "scan/strategies.json"
    }
}
