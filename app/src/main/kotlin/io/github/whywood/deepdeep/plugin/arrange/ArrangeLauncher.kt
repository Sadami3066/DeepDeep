package io.github.whywood.deepdeep.plugin.arrange

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.ArrayList

data class ArrangeIntentSpec(
    val action: String,
    val data: String? = null,
    val categories: List<String> = emptyList(),
    val extras: Map<String, Any> = emptyMap(),
)

data class ArrangeLaunchResult(
    val launched: Boolean,
    val errorMessage: String? = null,
)

interface ArrangeLauncher {
    fun launch(spec: ArrangeIntentSpec): ArrangeLaunchResult
}

class AndroidArrangeLauncher(private val context: Context) : ArrangeLauncher {
    override fun launch(spec: ArrangeIntentSpec): ArrangeLaunchResult {
        val intent = Intent(spec.action)
        spec.data?.let { intent.data = Uri.parse(it) }
        spec.categories.forEach { intent.addCategory(it) }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        spec.extras.forEach { (key, value) -> addExtra(intent, key, value) }
        return try {
            context.startActivity(intent)
            ArrangeLaunchResult(launched = true)
        } catch (error: ActivityNotFoundException) {
            ArrangeLaunchResult(launched = false, errorMessage = "没有找到可用的系统应用")
        } catch (error: SecurityException) {
            ArrangeLaunchResult(launched = false, errorMessage = "系统拒绝了启动请求")
        }
    }

    private fun addExtra(intent: Intent, key: String, value: Any) {
        when (value) {
            is String -> intent.putExtra(key, value)
            is Int -> intent.putExtra(key, value)
            is Long -> intent.putExtra(key, value)
            is Boolean -> intent.putExtra(key, value)
            is ArrayList<*> -> {
                val values = ArrayList<Int>()
                value.filterIsInstance<Int>().forEach { values.add(it) }
                intent.putIntegerArrayListExtra(key, values)
            }
        }
    }
}
