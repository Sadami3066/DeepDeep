package io.github.whywood.deepdeep.plugin.feishu

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object FeishuCli {
    const val BINARY = "lark-cli"
    const val INSTALL_PATH = "/usr/local/bin/lark-cli"
    const val CONFIG_DIR = "/root/.lark-cli"
    const val DATA_DIR = "/root/.local/share/lark-cli"

    val AUTH_DOMAINS: List<String> = listOf("all")

    private const val ENV = "LARKSUITE_CLI_NO_UPDATE_NOTIFIER=1 LARKSUITE_CLI_NO_SKILLS_NOTIFIER=1"

    fun versionCommand(): String = "$ENV $BINARY --version 2>&1"

    fun statusCommand(verify: Boolean = false): String {
        val verifyFlag = if (verify) " --verify" else ""
        return "$ENV $BINARY auth status --json$verifyFlag 2>&1"
    }

    fun configInitCommand(): String = "$ENV $BINARY config init --new --brand feishu 2>&1"

    fun loginStartCommand(): String =
        "$ENV $BINARY auth login --domain ${AUTH_DOMAINS.joinToString(",")} --no-wait --json 2>&1"

    fun loginPollCommand(deviceCode: String): String =
        "$ENV $BINARY auth login --device-code ${shellQuote(deviceCode)} --json 2>&1"

    fun logoutCommand(): String = "$ENV $BINARY auth logout --json 2>&1"

    fun uninstallCommand(): String = buildString {
        append("rm -f ").append(INSTALL_PATH).append(" /usr/local/bin/.lark-cli.new /usr/local/bin/.lark-cli.old; ")
        append("rm -rf ").append(CONFIG_DIR).append(' ').append(DATA_DIR).append("; true")
    }

    fun parseVersion(output: String): String? =
        Regex("""lark-cli\s+version\s+([0-9][^\s]*)""").find(output)?.groupValues?.get(1)
            ?: Regex("""([0-9]+\.[0-9]+\.[0-9]+[^\s]*)""").find(output)?.groupValues?.get(1)

    fun parseConfigInitUrl(output: String): String? {
        val urls = Regex("""https://[^\s"'<>\\]+""").findAll(output).map { it.value }.toList()
        return urls.firstOrNull { it.contains("/page/cli") } ?: urls.firstOrNull()
    }

    data class LoginChallenge(
        val verificationUrl: String,
        val deviceCode: String,
    )

    fun parseLoginChallenge(output: String): LoginChallenge? {
        val root = findJsonObject(output) ?: return null
        val url = root.string("verification_url") ?: return null
        val code = root.string("device_code") ?: return null
        return LoginChallenge(url, code)
    }

    data class AuthStatus(
        val appConfigured: Boolean,
        val userReady: Boolean,
        val account: String?,
        val scopeCount: Int,
        val message: String?,
    )

    fun parseAuthStatus(output: String): AuthStatus? {
        val root = findJsonObject(output) ?: return null
        val appId = root.string("appId")
        val user = root.objectOrNull("identities")?.objectOrNull("user")
        val available = user?.get("available")?.jsonPrimitive?.booleanOrNull ?: false
        val account = user?.string("userName")
        val scope = user?.string("scope").orEmpty()
        val scopeCount = scope.split(Regex("\\s+")).count { it.isNotBlank() }
        val message = user?.string("message") ?: root.string("note")
        return AuthStatus(
            appConfigured = !appId.isNullOrBlank(),
            userReady = available,
            account = account,
            scopeCount = scopeCount,
            message = message,
        )
    }

    private fun findJsonObject(output: String): JsonObject? {
        val trimmed = output.trim()
        if (trimmed.startsWith("{")) {
            runCatching { Json.parseToJsonElement(trimmed).jsonObject }.getOrNull()?.let { return it }
        }
        val start = output.indexOf('{')
        if (start < 0) return null
        var end = output.lastIndexOf('}')
        while (end > start) {
            val candidate = output.substring(start, end + 1)
            runCatching { Json.parseToJsonElement(candidate).jsonObject }.getOrNull()?.let { return it }
            end = output.lastIndexOf('}', end - 1)
        }
        return null
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.objectOrNull(name: String): JsonObject? {
        val element = this[name] ?: return null
        return runCatching { element.jsonObject }.getOrNull()
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
