package io.github.whywood.deepdeep.purge

enum class PurgeInjectPosition(val id: String) {
    HEAD("head"),
    TAIL("tail"),
    BOTH("both"),
    ;

    companion object {
        fun fromId(value: String?): PurgeInjectPosition {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.id == normalized } ?: BOTH
        }
    }
}

enum class PurgeLayerState {
    OK,
    PENDING,
    BROKEN,
    NOT_APPLICABLE,
}

enum class PurgeLayerId {
    PROMPT_PREAMBLE,
    PROMPT_RULES,
    BOOST,
    INJECT_POSITION,
    HOST_RULES,
    QUESTION,
    INPUT_ROUTING,
    SKILL_ASSETS,
    SKILLS,
    OVERRIDE_DOC,
    AUDIT,
    PROBE,
}

data class PurgeLayer(
    val id: PurgeLayerId,
    val state: PurgeLayerState,
    val evidence: String,
)

data class PurgeTuning(
    val enabled: Boolean = false,
    val boost: Boolean = true,
    val injectPosition: PurgeInjectPosition = PurgeInjectPosition.BOTH,
    val askNoBlock: Boolean = true,
    val writeOverride: Boolean = false,
    val injection: String = "",
)

data class PurgeSkillInfo(
    val id: String,
    val name: String,
    val description: String,
    val role: String,
    val installed: Boolean,
)

data class PurgeAuditEntry(
    val at: Long,
    val action: String,
    val detail: String,
)

data class PurgeSnapshot(
    val tuning: PurgeTuning = PurgeTuning(),
    val skills: List<PurgeSkillInfo> = emptyList(),
    val layers: List<PurgeLayer> = emptyList(),
    val audit: List<PurgeAuditEntry> = emptyList(),
    val probe: String? = null,
    val promptBytes: Int = 0,
    val overridePath: String? = null,
    val busy: Boolean = false,
) {
    val okCount: Int get() = layers.count { it.state == PurgeLayerState.OK }

    val brokenCount: Int get() = layers.count { it.state == PurgeLayerState.BROKEN }
}
