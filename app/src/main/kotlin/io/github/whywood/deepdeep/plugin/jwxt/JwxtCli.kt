package io.github.whywood.deepdeep.plugin.jwxt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal object JwxtCli {
    const val VERSION = "1.0.0"
    const val MIN_NODE_MAJOR = 22
    const val SKILL_ID = "deepdeep-jwxt"

    const val ASSET_ROOT = "jwxt"
    const val ENTRY_RELATIVE = "bin/jwxt"
    const val INSTALLED_ENTRY_RELATIVE = "root/projects/jwxt/bin/jwxt"
    const val PACKAGE_RELATIVE = "package.json"

    const val INSTALL_DIR = "/root/projects/jwxt"
    const val INSTALL_DIR_RELATIVE = "root/projects/jwxt"
    const val BIN_PATH = "/usr/local/bin/jwxt"
    const val BIN_RELATIVE = "usr/local/bin/jwxt"
    const val CONFIG_DIR = "/root/.config/jwxt"
    const val CONFIG_DIR_RELATIVE = "root/.config/jwxt"
    const val CREDENTIALS_RELATIVE = "$CONFIG_DIR_RELATIVE/credentials"
    const val SESSION_RELATIVE = "$CONFIG_DIR_RELATIVE/session.json"
    const val CREDENTIALS_PATH = "$CONFIG_DIR/credentials"
    const val SESSION_PATH = "$CONFIG_DIR/session.json"

    val ASSET_FILES = listOf(
        PACKAGE_RELATIVE,
        ENTRY_RELATIVE,
        "src/ca.js",
        "src/client.js",
        "src/cmds.js",
        "src/http.js",
        "src/login.js",
        "src/session.js",
    )

    fun nodeVersionCommand(): String = "node --version"

    fun versionCommand(): String = "$BIN_PATH --version"

    fun installCommand(): String = buildString {
        appendLine("set -e")
        appendLine("mkdir -p /usr/local/bin")
        appendLine("chmod 755 $INSTALL_DIR/$ENTRY_RELATIVE")
        appendLine("ln -sf $INSTALL_DIR/$ENTRY_RELATIVE $BIN_PATH")
        appendLine("$BIN_PATH --version")
    }

    fun loginSendCodeCommand(): String = "jwxt login --send-code"

    fun loginCodeCommand(code: String): String = "jwxt login --code $code"

    fun credentialsContent(netid: String, password: String): String =
        "netid=${netid.trim()}\npassword=${password.trim()}\n"

    fun netId(credentials: String?): String? = credentials
        ?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("netid=") }
        ?.substringAfter('=')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    fun isLoggedIn(sessionJson: String?): Boolean {
        if (sessionJson.isNullOrBlank()) return false
        return runCatching {
            val root = Json.parseToJsonElement(sessionJson).jsonObject
            root["cookies"]?.jsonArray?.isNotEmpty() == true
        }.getOrDefault(false)
    }

    fun parseNodeVersion(output: String): String? =
        Regex("""v?\d+\.\d+\.\d+""").find(output)?.value

    fun parseNodeMajor(version: String): Int? =
        Regex("""\d+""").find(version)?.value?.toIntOrNull()

    fun parseVersion(output: String): String? =
        Regex("""\b\d+\.\d+\.\d+\b""").find(output)?.value
}
