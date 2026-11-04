package io.github.whywood.deepdeep.ui.chat

import java.net.URI

internal sealed interface ScanLaunch {
    data class UriLaunch(val uri: String) : ScanLaunch

    data class Shortcut(
        val activityName: String,
        val action: String,
        val extraKey: String,
        val extraValue: String,
    ) : ScanLaunch
}

internal data class ScanTarget(
    val packageName: String,
    val hosts: List<String>,
    val schemes: List<String>,
    val launch: ScanLaunch,
)

internal sealed interface ScanPrimary {
    data class AppScan(val target: ScanTarget) : ScanPrimary
    data class Browse(val uri: String) : ScanPrimary
    data object Copy : ScanPrimary
}

internal data class ScanDecision(
    val raw: String,
    val primary: ScanPrimary,
)

internal object ScanRouting {
    private val httpSchemes = setOf("http", "https")

    private val targets = listOf(
        ScanTarget(
            packageName = "com.tencent.mm",
            hosts = listOf("weixin.qq.com", "u.wechat.com", "mp.weixin.qq.com"),
            schemes = listOf("weixin", "wxp"),
            launch = ScanLaunch.Shortcut(
                activityName = "com.tencent.mm.ui.LauncherUI",
                action = "com.tencent.mm.ui.ShortCutDispatchAction",
                extraKey = "LauncherUI.Shortcut.LaunchType",
                extraValue = "launch_type_scan_qrcode",
            ),
        ),
        ScanTarget(
            packageName = "com.eg.android.AlipayGphone",
            hosts = listOf("alipay.com", "alipay.cn"),
            schemes = listOf("alipay", "alipays", "alipayqr"),
            launch = ScanLaunch.UriLaunch(
                "alipays://platformapi/startapp?appId=10000007",
            ),
        ),
        ScanTarget(
            packageName = "com.tencent.mobileqq",
            hosts = listOf("qq.com"),
            schemes = listOf("mqq", "mqqapi"),
            launch = ScanLaunch.UriLaunch("mqqapi://qrscan/open?source=1"),
        ),
        ScanTarget(
            packageName = "com.sankuai.meituan",
            hosts = listOf("meituan.com", "dianping.com", "dpurl.cn"),
            schemes = listOf("imeituan", "dianping"),
            launch = ScanLaunch.UriLaunch(
                "imeituan://www.meituan.com/page/scanQRCodeForMBarResult?openAR=0&shortcuts=true",
            ),
        ),
        ScanTarget(
            packageName = "com.jingdong.app.mall",
            hosts = listOf("jd.com", "jd.cn", "3.cn"),
            schemes = listOf("openapp.jdmobile", "jingdong"),
            launch = ScanLaunch.UriLaunch(
                "openapp.jdmobile://virtual?params=%7B%22category%22%3A%22jump%22%2C%22des%22%3A%22scan%22%7D",
            ),
        ),
        ScanTarget(
            packageName = "com.ss.android.ugc.aweme",
            hosts = listOf("douyin.com", "iesdouyin.com"),
            schemes = listOf("snssdk1128", "snssdk1349"),
            launch = ScanLaunch.UriLaunch("snssdk1128://scan"),
        ),
    )

    fun resolve(raw: String, installed: (String) -> Boolean): ScanDecision {
        val text = raw.trim()
        val decision = resolveTarget(text, installed)
        return ScanDecision(text, decision ?: fallback(text))
    }

    private fun resolveTarget(text: String, installed: (String) -> Boolean): ScanPrimary? {
        if (text.isEmpty()) return null
        val uri = parse(text) ?: return null
        val available = targets.filter { installed(it.packageName) }
        val byScheme = available.firstOrNull { uri.scheme in it.schemes }
        if (byScheme != null) return ScanPrimary.AppScan(byScheme)
        val host = uri.host ?: return if (uri.scheme in httpSchemes) ScanPrimary.Browse(text) else null
        val hostSuffix = host.lowercase()
        val byHost = available
            .flatMap { target -> target.hosts.map { target to it } }
            .filter { (_, suffix) -> hostSuffix == suffix || hostSuffix.endsWith(".$suffix") }
            .maxByOrNull { (_, suffix) -> suffix.length }
            ?.first
        if (byHost != null) return ScanPrimary.AppScan(byHost)
        if (uri.scheme in httpSchemes) return ScanPrimary.Browse(text)
        return null
    }

    private fun fallback(text: String): ScanPrimary {
        if (text.isEmpty()) return ScanPrimary.Copy
        val lowered = text.lowercase()
        if (lowered.startsWith("http://") || lowered.startsWith("https://")) return ScanPrimary.Browse(text)
        return ScanPrimary.Copy
    }

    private fun parse(text: String): ScannedUri? {
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        return ScannedUri(scheme, uri.host?.lowercase())
    }

    private data class ScannedUri(val scheme: String, val host: String?)
}
