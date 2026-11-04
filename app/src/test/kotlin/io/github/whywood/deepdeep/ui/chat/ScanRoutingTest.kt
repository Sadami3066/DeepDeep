package io.github.whywood.deepdeep.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanRoutingTest {
    private val allInstalled = { _: String -> true }
    private val noneInstalled = { _: String -> false }

    private fun packageOf(raw: String, installed: (String) -> Boolean = allInstalled): String? =
        (ScanRouting.resolve(raw, installed).primary as? ScanPrimary.AppScan)?.target?.packageName

    private fun isCopy(raw: String, installed: (String) -> Boolean = allInstalled): Boolean =
        ScanRouting.resolve(raw, installed).primary == ScanPrimary.Copy

    private fun browseUri(raw: String, installed: (String) -> Boolean = allInstalled): String? =
        (ScanRouting.resolve(raw, installed).primary as? ScanPrimary.Browse)?.uri

    @Test
    fun alipayPaymentCodeRoutesToAlipay() {
        assertEquals(
            "com.eg.android.AlipayGphone",
            packageOf("https://qr.alipay.com/fkx12345"),
        )
    }

    @Test
    fun wechatLinkRoutesToWechat() {
        assertEquals("com.tencent.mm", packageOf("https://u.wechat.com/abc"))
    }

    @Test
    fun longestHostSuffixWins() {
        assertEquals("com.tencent.mm", packageOf("https://weixin.qq.com/x"))
        assertEquals("com.tencent.mobileqq", packageOf("https://qm.qq.com/x"))
    }

    @Test
    fun meituanJdAndDouyinHostsRoute() {
        assertEquals("com.sankuai.meituan", packageOf("https://i.meituan.com/x"))
        assertEquals("com.jingdong.app.mall", packageOf("https://3.cn/x"))
        assertEquals("com.ss.android.ugc.aweme", packageOf("https://v.douyin.com/x"))
    }

    @Test
    fun customSchemesRouteToApps() {
        assertEquals("com.tencent.mm", packageOf("wxp://f2f0abcdef"))
        assertEquals("com.eg.android.AlipayGphone", packageOf("alipays://platformapi/startapp?appId=1"))
        assertEquals("com.tencent.mobileqq", packageOf("mqqapi://qrscan/open?source=1"))
        assertEquals("com.sankuai.meituan", packageOf("imeituan://www.meituan.com/page/x"))
        assertEquals("com.jingdong.app.mall", packageOf("openapp.jdmobile://virtual?params=x"))
        assertEquals("com.ss.android.ugc.aweme", packageOf("snssdk1128://scan"))
    }

    @Test
    fun unknownLinkOpensInBrowser() {
        assertEquals("https://example.com/a", browseUri("https://example.com/a"))
    }

    @Test
    fun plainTextIsCopied() {
        assertTrue(isCopy("这是一段普通文本"))
    }

    @Test
    fun emptyContentIsCopied() {
        assertTrue(isCopy("   "))
    }

    @Test
    fun missingAppFallsBack() {
        assertEquals("https://qr.alipay.com/fkx12345", browseUri("https://qr.alipay.com/fkx12345", noneInstalled))
        assertTrue(isCopy("wxp://f2f0abcdef", noneInstalled))
    }

    @Test
    fun hostSuffixMustMatchOnBoundary() {
        assertEquals("https://notalipay.com/x", browseUri("https://notalipay.com/x"))
    }

    @Test
    fun contentIsTrimmedAndCaseInsensitive() {
        assertEquals("com.eg.android.AlipayGphone", packageOf("  HTTPS://QR.ALIPAY.COM/fkx  "))
    }
}
