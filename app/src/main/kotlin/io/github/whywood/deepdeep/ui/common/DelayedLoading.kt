package io.github.whywood.deepdeep.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.whywood.deepdeep.design.DdLoadingBlock
import kotlinx.coroutines.delay

private const val LOADING_REVEAL_MILLIS = 1_000L

@Composable
fun DelayedLoadingBlock(
    loading: Boolean,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        visible = if (loading) {
            delay(LOADING_REVEAL_MILLIS)
            true
        } else {
            false
        }
    }
    if (visible && loading) {
        DdLoadingBlock(modifier = modifier, text = text)
    }
}
