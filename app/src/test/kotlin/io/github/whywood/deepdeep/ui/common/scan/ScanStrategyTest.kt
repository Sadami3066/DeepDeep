package io.github.whywood.deepdeep.ui.common.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScanStrategyTest {
    private val allInstalled = { _: String -> true }

    private fun strategy(
        packageName: String,
        hosts: List<String> = emptyList(),
        schemes: List<String> = emptyList(),
        contains: List<String> = emptyList(),
        regex: List<String> = emptyList(),
        priority: Int = 0,
    ): ScanStrategy = ScanStrategy(
        packageName = packageName,
        hosts = hosts,
        schemes = schemes,
        contains = contains,
        regexes = regex.map { Regex(it, RegexOption.IGNORE_CASE) },
        priority = priority,
        fallbackMain = true,
        launch = ScanLaunch.Uri("$packageName://scan"),
        note = null,
    )

    private fun packageOf(raw: String, strategies: List<ScanStrategy>, installed: (String) -> Boolean = allInstalled): String? =
        (ScanMatcher.resolve(raw, strategies, installed).primary as? ScanPrimary.AppScan)?.strategy?.packageName

    @Test
    fun parsesUriStrategy() {
        val text =
            """{"version":1,"apps":[{"package":"com.example.a","hosts":["Example.com"],"schemes":["App"],"contains":["Demo"],"scan":{"uri":"app://scan"}}]}"""
        val parsed = ScanStrategyJson.parse(text)!!
        assertEquals(1, parsed.strategies.size)
        val strategy = parsed.strategies[0]
        assertEquals(listOf("example.com"), strategy.hosts)
        assertEquals(listOf("app"), strategy.schemes)
        assertEquals(listOf("demo"), strategy.contains)
        assertEquals(ScanLaunch.Uri("app://scan"), strategy.launch)
        assertNull(parsed.error)
    }

    @Test
    fun parsesComponentStrategyWithTypedExtras() {
        val text = """
            {"version":1,"apps":[{
              "package":"com.example.b",
              "scan":{
                "activity":"com.example.b.ScanActivity",
                "action":"com.example.b.SCAN",
                "extras":{"flag":true,"count":2,"mode":"fast"},
                "flags":["CLEAR_TOP"]
              }
            }]}
        """.trimIndent()
        val strategy = ScanStrategyJson.parse(text)!!.strategies.single()
        val launch = strategy.launch as ScanLaunch.Component
        assertEquals("com.example.b.ScanActivity", launch.activity)
        assertEquals("com.example.b.SCAN", launch.action)
        assertEquals(ScanExtra.Flag(true), launch.extras["flag"])
        assertEquals(ScanExtra.Number(2), launch.extras["count"])
        assertEquals(ScanExtra.Text("fast"), launch.extras["mode"])
        assertEquals(listOf("CLEAR_TOP"), launch.flags)
    }

    @Test
    fun skipsInvalidEntries() {
        val text = """
            {"version":1,"apps":[
              {"package":"","scan":{"uri":"a://b"}},
              {"package":"com.example.c"},
              {"package":"com.example.d","regex":["("],"scan":{"uri":"d://scan"}},
              {"package":"com.example.e","scan":{"uri":"e://scan"}}
            ]}
        """.trimIndent()
        val parsed = ScanStrategyJson.parse(text)!!
        assertEquals(listOf("com.example.e"), parsed.strategies.map { it.packageName })
        assertNotNull(parsed.error)
    }

    @Test
    fun rendersAndReparsesStrategy() {
        val original = ScanStrategy(
            packageName = "com.example.f",
            hosts = listOf("example.com"),
            schemes = listOf("f"),
            contains = listOf("brand"),
            regexes = listOf(Regex("token\\s*=\\s*(\\w+)", RegexOption.IGNORE_CASE)),
            priority = 5,
            fallbackMain = false,
            launch = ScanLaunch.Component(
                activity = "com.example.f.Scan",
                action = null,
                extras = mapOf("flag" to ScanExtra.Flag(true), "n" to ScanExtra.Number(1)),
                flags = listOf("SINGLE_TOP"),
            ),
            note = "note",
        )
        val parsed = ScanStrategyJson.parse(ScanStrategyJson.render(listOf(original)))!!.strategies.single()
        assertEquals(original.packageName, parsed.packageName)
        assertEquals(original.hosts, parsed.hosts)
        assertEquals(original.schemes, parsed.schemes)
        assertEquals(original.contains, parsed.contains)
        assertEquals(original.regexes.map { it.pattern }, parsed.regexes.map { it.pattern })
        assertEquals(original.priority, parsed.priority)
        assertEquals(original.fallbackMain, parsed.fallbackMain)
        assertEquals(original.launch, parsed.launch)
        assertEquals(original.note, parsed.note)
    }

    @Test
    fun extrasTextRoundTrip() {
        val extras = mapOf(
            "s" to ScanExtra.Text("value"),
            "f" to ScanExtra.Flag(false),
            "n" to ScanExtra.Number(7),
        )
        assertEquals(extras, ScanStrategyJson.parseExtrasText(ScanStrategyJson.extrasText(extras)))
        assertEquals(emptyMap<String, ScanExtra>(), ScanStrategyJson.parseExtrasText("  "))
        assertNull(ScanStrategyJson.parseExtrasText("not json"))
    }

    @Test
    fun schemeMatchWins() {
        val strategies = listOf(
            strategy("com.example.scheme", schemes = listOf("demo")),
            strategy("com.example.host", hosts = listOf("demo.com")),
        )
        assertEquals("com.example.scheme", packageOf("demo://scan", strategies))
    }

    @Test
    fun longestHostSuffixWins() {
        val strategies = listOf(
            strategy("com.example.qq", hosts = listOf("qq.com")),
            strategy("com.example.wechat", hosts = listOf("weixin.qq.com")),
        )
        assertEquals("com.example.wechat", packageOf("https://weixin.qq.com/x", strategies))
        assertEquals("com.example.qq", packageOf("https://qm.qq.com/x", strategies))
    }

    @Test
    fun hostSuffixMatchesOnBoundaryOnly() {
        val strategies = listOf(strategy("com.example.alipay", hosts = listOf("alipay.com")))
        assertNull(packageOf("https://notalipay.com/x", strategies))
        assertEquals("com.example.alipay", packageOf("https://qr.alipay.com/x", strategies))
    }

    @Test
    fun containsAndRegexMatchOpaqueContent() {
        val contains = listOf(strategy("com.example.bytedance", contains = listOf("feishu", "lark")))
        assertEquals("com.example.bytedance", packageOf("""{"type":"login","app":"lark"}""", contains))
        val regex = listOf(strategy("com.example.regex", regex = listOf(""""app"\s*:\s*"dingtalk"""")))
        assertEquals("com.example.regex", packageOf("""{"app":"dingtalk","token":"x"}""", regex))
    }

    @Test
    fun higherPriorityWinsOnEqualScore() {
        val strategies = listOf(
            strategy("com.example.low", contains = listOf("demo"), priority = 1),
            strategy("com.example.high", contains = listOf("demo"), priority = 5),
        )
        assertEquals("com.example.high", packageOf("demo-token", strategies))
    }

    @Test
    fun missingAppFallsBack() {
        val strategies = listOf(strategy("com.example.alipay", hosts = listOf("alipay.com")))
        val installed = { _: String -> false }
        assertEquals(
            ScanPrimary.Browse("https://qr.alipay.com/x"),
            ScanMatcher.resolve("https://qr.alipay.com/x", strategies, installed).primary,
        )
        assertEquals(
            ScanPrimary.Copy,
            ScanMatcher.resolve("demo://scan", strategies, installed).primary,
        )
    }

    @Test
    fun unknownHttpBrowsesAndPlainTextCopies() {
        assertEquals(ScanPrimary.Browse("https://example.com/a"), ScanMatcher.resolve("  https://example.com/a  ", emptyList(), allInstalled).primary)
        assertEquals(ScanPrimary.Copy, ScanMatcher.resolve("普通文本", emptyList(), allInstalled).primary)
        assertEquals(ScanPrimary.Copy, ScanMatcher.resolve("   ", emptyList(), allInstalled).primary)
    }

    @Test
    fun caseInsensitiveContent() {
        val strategies = listOf(strategy("com.example.alipay", hosts = listOf("alipay.com")))
        assertEquals("com.example.alipay", packageOf("HTTPS://QR.ALIPAY.COM/ABC", strategies))
    }

    @Test
    fun builtinStrategiesAreValid() {
        val file = File("src/main/assets/scan/strategies.json")
        assertTrue("missing ${file.absolutePath}", file.isFile)
        val parsed = ScanStrategyJson.parse(file.readText())!!
        assertNull(parsed.error)
        assertEquals(
            setOf(
                "com.tencent.mm",
                "com.eg.android.AlipayGphone",
                "com.tencent.mobileqq",
                "com.sankuai.meituan",
                "com.jingdong.app.mall",
                "com.ss.android.ugc.aweme",
                "com.ss.android.lark",
            ),
            parsed.strategies.map { it.packageName }.toSet(),
        )
    }
}
