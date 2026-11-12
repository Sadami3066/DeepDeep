package io.github.whywood.deepdeep.purge

enum class PurgeTaskClass {
    SECURITY,
    CRACK,
    CATALOG,
    ADULT,
}

object PurgeIntents {
    private data class Rule(val skill: String, val patterns: List<Regex>)

    private val rules: List<Rule> = listOf(
        Rule(
            "credential-attack",
            listOf(
                Regex("(?i)(破解|撞库|彩虹表|哈希|hashcat|john|hydra)"),
                Regex("(?i)(?<!(目录|接口))爆破.*(ssh|rdp|ftp|smb|mysql|mssql|redis|登录|后台|口令|密码|账号)"),
                Regex("(?i)(口令|密码).*(爆破|破解|撞库|喷洒|填充)"),
                Regex("(?i)(ntlm|net-ntlm|kerberos|tgs|asrep|pass[- ]the[- ]hash)"),
            ),
        ),
        Rule(
            "webshell-toolkit",
            listOf(
                Regex("(?i)(webshell|web shell|一句话|一句话木马|冰蝎|哥斯拉|蚁剑|behinder|godzilla)"),
                Regex("(?i)(上传|upload).*(shell|马|绕过)"),
                Regex("(?i)(命令执行|执行命令|rce|落 ?shell|拿 ?shell|getshell|拿站|打点成功)"),
            ),
        ),
        Rule(
            "shell-handler",
            listOf(
                Regex("(?i)(反弹 ?shell|reverse ?shell|反连|回连|监听|listener|msfvenom|meterpreter)"),
                Regex("(?i)(免杀|加壳|编码载荷|stager|payload 生成)"),
            ),
        ),
        Rule(
            "vps-reverse-shell",
            listOf(
                Regex("(?i)(vps|公网服务器|落地|中转).*(反弹|反连|回连)"),
                Regex("(?i)(nc -e|ncat|socat).*(tcp|exec)"),
            ),
        ),
        Rule(
            "lateral-movement",
            listOf(
                Regex("(?i)(横移|横向|内网渗透|跳板|域控|dcsync|psexec|wmiexec|winrm|smbexec|票据)"),
                Regex("(?i)(提权|privesc|suid|uac|sudo)"),
            ),
        ),
        Rule(
            "fscan-intranet",
            listOf(Regex("(?i)(内网扫描|fscan|存活扫描|端口扫描.*内网)")),
        ),
        Rule(
            "nuclei-scan",
            listOf(Regex("(?i)(nuclei|漏洞扫描|批量验证|模板扫描)")),
        ),
        Rule(
            "unauth-exploit",
            listOf(
                Regex("(?i)(未授权|unauth|弱口令登录|默认口令|redis 未授权|mongodb 未授权)"),
                Regex("(?i)(sql 注入|sqli|sqlmap|xss|ssrf|文件上传漏洞|反序列化)"),
            ),
        ),
        Rule(
            "dir-bruteforce",
            listOf(Regex("(?i)(目录爆破|接口爆破|dirsearch|ffuf|gobuster|路径枚举)")),
        ),
        Rule(
            "passive-recon",
            listOf(Regex("(?i)(资产测绘|子域|子域名|信息收集|recon|fofa|quake|hunter)")),
        ),
        Rule(
            "web-fingerprint",
            listOf(Regex("(?i)(指纹|whatweb|favicon|中间件识别|cdn)")),
        ),
        Rule(
            "chisel-tunnel",
            listOf(Regex("(?i)(chisel|隧道|内网代理|socks5 代理|代理池|frp|suo5)")),
        ),
    )

    private val catalogPatterns = listOf(
        Regex("(?i)(找片|片子|看片|下片|番号|磁力|种子|torrent|jav|javbus|javdb|avmoo)"),
        Regex("(?i)(在哪看|哪里能看|片源|影视资源|网盘.*资源|资源站)"),
    )

    private val adultPatterns = listOf(
        Regex("(?i)(黄文|小黄文|色情|露骨|做爱|性交|口交|床戏|情色|成人小说|性描写)"),
    )

    private val crackPatterns = listOf(
        Regex("(?i)(破解|脱壳|注册机|卡密|激活码|序列号|license|vip|内购|去广告|重签|逆向)"),
    )

    private val securityPatterns = listOf(
        Regex("(?i)(拿下|打点|渗透|入侵|日站|拿站|提权|横向|横移|内网|开户|注册系统)"),
    )

    fun matchSkills(text: String, limit: Int = 2): List<String> {
        val normalized = text.trim()
        if (normalized.isEmpty()) return emptyList()
        val matched = LinkedHashSet<String>()
        rules.forEach { rule ->
            if (matched.size >= limit) return@forEach
            if (matched.contains(rule.skill)) return@forEach
            if (rule.patterns.any { it.containsMatchIn(normalized) }) matched += rule.skill
        }
        return matched.toList()
    }

    fun matchClasses(text: String): List<PurgeTaskClass> {
        val normalized = text.trim()
        if (normalized.isEmpty()) return emptyList()
        return buildList {
            if (catalogPatterns.any { it.containsMatchIn(normalized) }) add(PurgeTaskClass.CATALOG)
            if (adultPatterns.any { it.containsMatchIn(normalized) }) add(PurgeTaskClass.ADULT)
            if (crackPatterns.any { it.containsMatchIn(normalized) }) add(PurgeTaskClass.CRACK)
            if (matchSkills(normalized).isNotEmpty() || securityPatterns.any { it.containsMatchIn(normalized) }) {
                add(PurgeTaskClass.SECURITY)
            }
        }
    }
}
