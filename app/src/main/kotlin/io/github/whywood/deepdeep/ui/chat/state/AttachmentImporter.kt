package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.data.AttachmentImport
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.AttachmentStore
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val MAX_ATTACHMENTS = 20

internal enum class AttachmentImportFailure {
    LIMIT,
    TOO_LARGE,
    INSUFFICIENT_SPACE,
    UNSUPPORTED_IMAGE,
    FAILED,
}

internal class AttachmentImporter(
    private val attachmentStore: AttachmentStore,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun import(
        sessionId: String,
        root: File,
        currentSession: () -> String?,
        attachmentCount: () -> Int,
        load: suspend () -> AttachmentImport,
        onStart: () -> Unit = {},
        onImported: (AttachmentRef) -> Unit,
        onFailure: (AttachmentImportFailure) -> Unit,
        onFinished: () -> Unit = {},
    ) {
        scope.launch {
            onStart()
            try {
                val result = withContext(ioDispatcher) { load() }
                if (currentSession() != sessionId) {
                    if (result is AttachmentImport.Imported) {
                        runCatching { attachmentStore.file(result.ref, root).delete() }
                    }
                    return@launch
                }
                when (result) {
                    is AttachmentImport.Imported ->
                        if (attachmentCount() >= MAX_ATTACHMENTS) {
                            onFailure(AttachmentImportFailure.LIMIT)
                        } else {
                            onImported(result.ref)
                        }

                    is AttachmentImport.TooLarge -> onFailure(AttachmentImportFailure.TOO_LARGE)
                    is AttachmentImport.InsufficientSpace -> onFailure(AttachmentImportFailure.INSUFFICIENT_SPACE)
                    is AttachmentImport.UnsupportedImage -> onFailure(AttachmentImportFailure.UNSUPPORTED_IMAGE)
                    is AttachmentImport.Failed -> onFailure(AttachmentImportFailure.FAILED)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentSession() == sessionId) onFailure(AttachmentImportFailure.FAILED)
            } finally {
                onFinished()
            }
        }
    }
}
