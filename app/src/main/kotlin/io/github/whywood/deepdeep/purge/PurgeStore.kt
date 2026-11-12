package io.github.whywood.deepdeep.purge

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

private const val AUDIT_LIMIT_BYTES = 512L * 1024L

internal data class PurgeAppliedRecord(
    val skills: Int,
    val promptBytes: Int,
    val at: Long,
)

internal class PurgeStore(private val dir: File) {
    private val tuningFile = File(dir, "tuning.json")
    private val appliedFile = File(dir, "applied.json")
    private val auditFile = File(dir, "audit.jsonl")
    private val probeFile = File(dir, "probe.json")
    private val backupRoot = File(dir, "backups")

    fun readTuning(): PurgeTuning {
        val json = runCatching { JSONObject(tuningFile.readText(Charsets.UTF_8)) }.getOrNull()
            ?: return PurgeTuning()
        return PurgeTuning(
            enabled = json.optBoolean("enabled", false),
            boost = json.optBoolean("boost", true),
            injectPosition = PurgeInjectPosition.fromId(json.optString("injectPosition")),
            askNoBlock = json.optBoolean("askNoBlock", true),
            writeOverride = json.optBoolean("writeOverride", false),
            injection = json.optString("injection").orEmpty(),
        )
    }

    fun writeTuning(tuning: PurgeTuning) {
        dir.mkdirs()
        val json = JSONObject()
            .put("enabled", tuning.enabled)
            .put("boost", tuning.boost)
            .put("injectPosition", tuning.injectPosition.id)
            .put("askNoBlock", tuning.askNoBlock)
            .put("writeOverride", tuning.writeOverride)
            .put("injection", tuning.injection)
        tuningFile.writeText(json.toString(2), Charsets.UTF_8)
    }

    fun readApplied(): PurgeAppliedRecord? {
        val json = runCatching { JSONObject(appliedFile.readText(Charsets.UTF_8)) }.getOrNull() ?: return null
        return PurgeAppliedRecord(
            skills = json.optInt("skills", 0),
            promptBytes = json.optInt("promptBytes", 0),
            at = json.optLong("at", 0L),
        )
    }

    fun writeApplied(skills: Int, promptBytes: Int) {
        dir.mkdirs()
        val json = JSONObject()
            .put("skills", skills)
            .put("promptBytes", promptBytes)
            .put("at", System.currentTimeMillis())
        appliedFile.writeText(json.toString(2), Charsets.UTF_8)
    }

    fun audit(action: String, detail: String) {
        dir.mkdirs()
        if (auditFile.isFile && auditFile.length() > AUDIT_LIMIT_BYTES) {
            val rotated = File(dir, "audit.jsonl.1")
            rotated.delete()
            runCatching { auditFile.renameTo(rotated) }
        }
        val line = JSONObject()
            .put("at", System.currentTimeMillis())
            .put("action", action)
            .put("detail", detail)
            .toString()
        runCatching { auditFile.appendText(line + "\n", Charsets.UTF_8) }
    }

    fun readAudit(limit: Int = 24): List<PurgeAuditEntry> {
        if (!auditFile.isFile) return emptyList()
        return runCatching {
            auditFile.readLines(Charsets.UTF_8)
                .asSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val json = runCatching { JSONObject(line) }.getOrNull() ?: return@mapNotNull null
                    PurgeAuditEntry(
                        at = json.optLong("at", 0L),
                        action = json.optString("action"),
                        detail = json.optString("detail"),
                    )
                }
                .toList()
                .takeLast(limit)
                .reversed()
        }.getOrDefault(emptyList())
    }

    fun readProbe(): String? {
        val json = runCatching { JSONObject(probeFile.readText(Charsets.UTF_8)) }.getOrNull() ?: return null
        val nonce = json.optString("nonce").takeIf { it.isNotBlank() } ?: return null
        val createdAt = json.optLong("createdAt", 0L)
        if (System.currentTimeMillis() - createdAt > 30L * 60L * 1000L) return null
        return nonce
    }

    fun writeProbe(nonce: String) {
        dir.mkdirs()
        val json = JSONObject()
            .put("nonce", nonce)
            .put("createdAt", System.currentTimeMillis())
        probeFile.writeText(json.toString(2), Charsets.UTF_8)
    }

    fun backup(targets: List<File>): File? {
        val existing = targets.filter { it.isFile }
        if (existing.isEmpty()) return null
        val stamp = System.currentTimeMillis().toString()
        val snapshot = File(backupRoot, stamp)
        snapshot.mkdirs()
        val entries = JSONArray()
        existing.forEachIndexed { index, file ->
            val copy = File(snapshot, "file-$index.md")
            runCatching { file.copyTo(copy, overwrite = true) }.getOrNull() ?: return@forEachIndexed
            entries.put(
                JSONObject()
                    .put("target", file.absolutePath)
                    .put("backup", copy.name),
            )
        }
        if (entries.length() == 0) return null
        File(snapshot, "manifest.json").writeText(JSONObject().put("files", entries).toString(2), Charsets.UTF_8)
        return snapshot
    }

    fun pruneBackups(keep: Int = 5) {
        val snapshots = backupRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }.orEmpty()
        snapshots.dropLast(keep).forEach { runCatching { it.deleteRecursively() } }
    }

    fun restoreLatest(): List<String> {
        val snapshot = backupRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.name }
            ?: return emptyList()
        val manifest = runCatching {
            JSONObject(File(snapshot, "manifest.json").readText(Charsets.UTF_8))
        }.getOrNull() ?: return emptyList()
        val files = manifest.optJSONArray("files") ?: return emptyList()
        val restored = mutableListOf<String>()
        for (index in 0 until files.length()) {
            val entry = files.optJSONObject(index) ?: continue
            val target = entry.optString("target").takeIf { it.isNotBlank() } ?: continue
            val backup = entry.optString("backup").takeIf { it.isNotBlank() } ?: continue
            val source = File(snapshot, backup)
            if (!source.isFile) continue
            val destination = File(target)
            destination.parentFile?.mkdirs()
            if (runCatching { source.copyTo(destination, overwrite = true) }.isSuccess) {
                restored += target
            }
        }
        return restored
    }

    fun backupCount(): Int = backupRoot.listFiles()?.count { it.isDirectory } ?: 0
}
