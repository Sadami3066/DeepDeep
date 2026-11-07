package io.github.whywood.deepdeep.ui.navigation

import androidx.compose.ui.graphics.ImageBitmap
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.design.DdFontStyle
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.ui.chat.ChatViewModel
import kotlinx.coroutines.CoroutineScope

internal data class ChatRouteData(
    val conversationId: String?,
    val workspaces: List<RuntimeWorkspace>,
    val archived: List<ConversationSummary>,
    val archivedCount: Int,
)

internal data class ShellRouteUi(
    val theme: ThemeMode,
    val onThemeChange: (ThemeMode) -> Unit,
    val fontStyle: DdFontStyle,
    val onFontStyleChange: (DdFontStyle) -> Unit,
    val nickname: String,
    val avatar: ImageBitmap?,
    val providerName: String?,
    val modelLabel: String?,
    val modelConfig: ModelConfig,
    val configuredProviderIds: Set<String>,
)

internal class AppRouteHost(
    val navigator: AppNavigator,
    val dependencies: AppDependencies,
    val scope: CoroutineScope,
    val chatViewModel: ChatViewModel,
    val chat: ChatRouteData,
    val ui: ShellRouteUi,
    val onOpenChat: () -> Unit,
    val onOpenMiniappCreate: () -> Unit,
    val onOpenFile: (String) -> Unit,
    val onOpenAssistantSettings: () -> Unit,
) {
    fun back() {
        navigator.pop()
    }
}
