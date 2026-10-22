package io.github.whywood.deepdeep.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

class AppNavigator(initialStack: List<AppDestination> = listOf(AppDestination.Chat)) {
    private val entries = mutableStateListOf<AppDestination>().apply {
        addAll(initialStack.ifEmpty { listOf(AppDestination.Chat) })
    }

    val stack: List<AppDestination> get() = entries

    val current: AppDestination get() = entries.last()

    val canPop: Boolean get() = entries.size > 1

    fun push(destination: AppDestination) {
        if (entries.last() == destination) return
        entries.add(destination)
    }

    fun pop(): Boolean {
        if (!canPop) return false
        entries.removeAt(entries.lastIndex)
        return true
    }

    fun reset(destination: AppDestination = AppDestination.Chat) {
        entries.clear()
        entries.add(destination)
    }

    companion object {
        val Saver: Saver<AppNavigator, Any> = listSaver<AppNavigator, String>(
            save = { navigator -> navigator.stack.map { it.routeKey } },
            restore = { routeKeys -> AppNavigator(routeKeys.mapNotNull { AppDestination.fromRouteKey(it) }) },
        )
    }
}

@Composable
fun rememberAppNavigator(): AppNavigator =
    rememberSaveable(saver = AppNavigator.Saver) { AppNavigator() }
