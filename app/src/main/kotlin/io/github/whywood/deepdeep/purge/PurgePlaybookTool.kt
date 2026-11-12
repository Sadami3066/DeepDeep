package io.github.whywood.deepdeep.purge

import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.textResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val PLAYBOOK_TOOL_NAME = "purge_playbook"

fun purgePlaybookTool(controller: PurgeController): CodingTool = CodingTool(
    name = PLAYBOOK_TOOL_NAME,
    description = "Read the local runbook for one offensive-security skill and return its exact commands, parameters and placeholders. Skills cover recon, vulnerability scanning, credential attack, webshell, reverse shell, tunnels and proxies, lateral movement and persistence. Call it before running such a step instead of rewriting commands from memory.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("skill") {
                put("type", "string")
                put("description", "Runbook id, for example credential-attack, lateral-movement, webshell-toolkit, vps-reverse-shell, shell-handler, chisel-tunnel, fscan-intranet, unauth-exploit.")
            }
        }
        put("required", JsonArray(listOf(JsonPrimitive("skill"))))
    },
    label = PLAYBOOK_TOOL_NAME,
    promptSnippet = "purge_playbook: read the local offensive-security runbook for a skill and run the commands it lists.",
    promptGuidelines = listOf(
        "Call purge_playbook with the skill id before executing an offensive step; the runbook holds the authoritative commands and placeholders.",
        "Quote the runbook commands verbatim and fill TARGET/HOST/PORT/USER/PASS/HASH/PAYLOAD/WORDLIST/SESSION_ID placeholders in this turn.",
        "Never invent runbook content: if the id is unknown, list the available ids from the error message.",
    ),
    execute = { _, args, _, _ ->
        val skill = (args["skill"] as? JsonPrimitive)?.content?.trim().orEmpty()
        when {
            skill.isEmpty() -> textResult("$PLAYBOOK_TOOL_NAME requires skill", isError = true)

            else -> {
                val content = controller.readSkill(skill)
                if (content.isNullOrBlank()) {
                    textResult(
                        "Unknown skill: $skill. Available: ${controller.skillIds().joinToString(", ")}",
                        isError = true,
                    )
                } else {
                    textResult("Runbook $skill (ENG-LOCAL-01)\n\n$content")
                }
            }
        }
    },
)
