package io.github.whywood.superpi.plugin.host.services

import io.github.whywood.superpi.plugin.api.ImageService
import io.github.whywood.superpi.plugin.api.RemoteImage
import io.github.whywood.superpi.plugin.host.images.RemoteImageCache

internal class ImagePluginService(
    private val cache: RemoteImageCache,
) : ImageService {
    override suspend fun load(url: String, maxEdgePx: Int): RemoteImage? =
        cache.load(url, maxEdgePx)
}
