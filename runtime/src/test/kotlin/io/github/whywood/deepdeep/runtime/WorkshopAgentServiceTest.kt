package io.github.whywood.deepdeep.runtime

import dev.pi.ai.auth.Credential
import dev.pi.ai.auth.CredentialInfo
import dev.pi.ai.auth.CredentialStore
import dev.pi.ai.http.HttpRequest
import dev.pi.ai.http.HttpResponse
import dev.pi.ai.http.HttpTransport
import dev.pi.coding.prompt.Skill
import dev.pi.coding.tools.CodingTool
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopAgentServiceTest {
    private val transport = object : HttpTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse =
            error("network is not used by the catalog")
    }

    private val credentials = object : CredentialStore {
        override fun read(providerId: String): Credential? = null
        override fun readAll(): Map<String, Credential> = emptyMap()
        override fun readApiKey(providerId: String): String? = null
        override fun readEnv(providerId: String): Map<String, String>? = null
        override fun list(): List<CredentialInfo> = emptyList()
        override fun keys(): List<String> = emptyList()
        override fun modify(providerId: String, transform: (Credential?) -> Credential?): Credential? = null
        override fun writeApiKey(providerId: String, key: String, env: Map<String, String>?) = Unit
        override fun write(providerId: String, credential: Credential) = Unit
        override fun writeRawCredential(providerId: String, credential: JsonElement) = Unit
        override fun delete(providerId: String) = Unit
        override fun readRaw(): Map<String, JsonElement> = emptyMap()
    }

    @Test
    fun modelsUseOnlyConfiguredProviders() {
        val models = buildWorkshopAgentModels(ProviderCatalog(transport, credentials), setOf("deepseek"))

        assertTrue(models.isNotEmpty())
        assertTrue(models.all { it.providerId == "deepseek" && it.modelId.isNotBlank() && it.label.isNotBlank() })
        assertTrue(models.all { it.thinkingLevels.isNotEmpty() && it.thinkingLevels.first() == "off" })
        assertEquals(models.sortedWith(compareBy({ it.providerId }, { it.modelId })), models)
    }

    @Test
    fun modelsAreEmptyWithoutConfiguredProviders() {
        assertTrue(buildWorkshopAgentModels(ProviderCatalog(transport, credentials), emptySet()).isEmpty())
    }

    @Test
    fun rejectsBlankPrompt() {
        assertRejected { validateWorkshopAgentRequest(request(prompt = "   ")) }
    }

    @Test
    fun rejectsOversizedPrompt() {
        assertRejected { validateWorkshopAgentRequest(request(prompt = "x".repeat(64 * 1024 + 1))) }
        validateWorkshopAgentRequest(request(prompt = "x".repeat(64 * 1024)))
    }

    @Test
    fun rejectsOversizedSystemPrompt() {
        assertRejected { validateWorkshopAgentRequest(request(systemPrompt = "x".repeat(16 * 1024 + 1))) }
        validateWorkshopAgentRequest(request(systemPrompt = "x".repeat(16 * 1024)))
    }

    @Test
    fun rejectsMaxTurnsOutOfRange() {
        assertRejected { validateWorkshopAgentRequest(request(maxTurns = 0)) }
        assertRejected { validateWorkshopAgentRequest(request(maxTurns = 65)) }
        validateWorkshopAgentRequest(request(maxTurns = 1))
        validateWorkshopAgentRequest(request(maxTurns = 64))
    }

    @Test
    fun maxTurnsDefaultsToSixteen() {
        assertEquals(16, workshopAgentMaxTurns(null))
        assertEquals(64, workshopAgentMaxTurns(64))
    }

    @Test
    fun providerAndModelMustComeTogether() {
        assertRejected {
            resolveWorkshopAgentConfig(
                request = request(providerId = "openai"),
                defaultConfig = ModelConfig(),
                models = sampleModels,
                configuredProviders = setOf("openai"),
            )
        }
        assertRejected {
            resolveWorkshopAgentConfig(
                request = request(modelId = "gpt"),
                defaultConfig = ModelConfig(),
                models = sampleModels,
                configuredProviders = setOf("openai"),
            )
        }
    }

    @Test
    fun unconfiguredProviderIsRejected() {
        assertRejected {
            resolveWorkshopAgentConfig(
                request = request(providerId = "openai", modelId = "gpt"),
                defaultConfig = ModelConfig(),
                models = sampleModels,
                configuredProviders = setOf("deepseek"),
            )
        }
    }

    @Test
    fun unknownModelIsRejected() {
        assertRejected {
            resolveWorkshopAgentConfig(
                request = request(providerId = "deepseek", modelId = "missing"),
                defaultConfig = ModelConfig(),
                models = sampleModels,
                configuredProviders = setOf("deepseek"),
            )
        }
    }

    @Test
    fun unsupportedThinkingLevelIsRejected() {
        assertRejected {
            resolveWorkshopAgentConfig(
                request = request(providerId = "deepseek", modelId = "chat", thinkingLevel = "high"),
                defaultConfig = ModelConfig(),
                models = sampleModels,
                configuredProviders = setOf("deepseek"),
            )
        }
    }

    @Test
    fun explicitSelectionResolvesConfig() {
        val config = resolveWorkshopAgentConfig(
            request = request(providerId = "openai", modelId = "gpt", thinkingLevel = "high"),
            defaultConfig = ModelConfig(),
            models = sampleModels,
            configuredProviders = setOf("deepseek", "openai"),
        )

        assertEquals(ModelConfig("openai", "gpt", ThinkingLevel.HIGH), config)
    }

    @Test
    fun missingThinkingLevelKeepsDefaultLevel() {
        val config = resolveWorkshopAgentConfig(
            request = request(providerId = "openai", modelId = "gpt"),
            defaultConfig = ModelConfig("deepseek", "chat", ThinkingLevel.LOW),
            models = sampleModels,
            configuredProviders = setOf("deepseek", "openai"),
        )

        assertEquals(ModelConfig("openai", "gpt", ThinkingLevel.LOW), config)
    }

    @Test
    fun defaultConfigIsUsedWithoutSelection() {
        val defaultConfig = ModelConfig("deepseek", "chat", ThinkingLevel.LOW)

        assertEquals(
            defaultConfig,
            resolveWorkshopAgentConfig(request(), defaultConfig, sampleModels, setOf("deepseek")),
        )
    }

    @Test
    fun thinkingLevelOverrideAppliesToDefaultConfig() {
        val config = resolveWorkshopAgentConfig(
            request = request(thinkingLevel = "low"),
            defaultConfig = ModelConfig("deepseek", "chat", ThinkingLevel.OFF),
            models = sampleModels,
            configuredProviders = setOf("deepseek"),
        )

        assertEquals(ModelConfig("deepseek", "chat", ThinkingLevel.LOW), config)
        assertRejected {
            resolveWorkshopAgentConfig(
                request = request(thinkingLevel = "max"),
                defaultConfig = ModelConfig("deepseek", "chat", ThinkingLevel.OFF),
                models = sampleModels,
                configuredProviders = setOf("deepseek"),
            )
        }
    }

    @Test
    fun thinkingLevelStringsMapToEnumValues() {
        assertEquals(ThinkingLevel.OFF, parseWorkshopThinkingLevel("off"))
        assertEquals(ThinkingLevel.LOW, parseWorkshopThinkingLevel(" low "))
        assertEquals(ThinkingLevel.MEDIUM, parseWorkshopThinkingLevel("medium"))
        assertEquals(ThinkingLevel.HIGH, parseWorkshopThinkingLevel("high"))
        assertEquals(ThinkingLevel.MAX, parseWorkshopThinkingLevel("max"))
        assertRejected { parseWorkshopThinkingLevel("extreme") }
    }

    @Test
    fun nullSelectionMeansAllNames() {
        assertNull(selectWorkshopAgentNames("tool", null, listOf("read", "write")))
        assertNull(selectWorkshopAgentNames("skill", null, emptyList()))
    }

    @Test
    fun emptySelectionMeansNoNames() {
        assertEquals(emptySet<String>(), selectWorkshopAgentNames("tool", emptyList(), listOf("read")))
    }

    @Test
    fun unknownNameIsRejected() {
        assertRejected { selectWorkshopAgentNames("tool", listOf("missing"), listOf("read")) }
    }

    @Test
    fun oversizedSelectionIsRejected() {
        assertRejected {
            selectWorkshopAgentNames("tool", List(101) { "read" }, listOf("read"))
        }
    }

    @Test
    fun selectionKeepsOnlyKnownNames() {
        assertEquals(
            setOf("read", "write"),
            selectWorkshopAgentNames("tool", listOf("read", "write", "read"), listOf("read", "write", "bash")),
        )
    }

    @Test
    fun toolCatalogIsSortedWithDescriptions() {
        val tools = buildWorkshopAgentTools(
            listOf(
                tool("zeta", "Last tool"),
                tool("alpha", "First tool"),
            ),
        )

        assertEquals(listOf("alpha", "zeta"), tools.map { it.name })
        assertEquals(listOf("First tool", "Last tool"), tools.map { it.description })
    }

    @Test
    fun skillDirectoryNameIsSanitized() {
        assertEquals("workshop", workshopSkillDirectoryName("workshop"))
        assertEquals("a_b", workshopSkillDirectoryName("a/b"))
        assertEquals("skill", workshopSkillDirectoryName(".."))
        assertEquals("skill", workshopSkillDirectoryName("   "))
        assertFalse(workshopSkillDirectoryName("../../evil").contains("/"))
        assertFalse(workshopSkillDirectoryName("../../evil").contains("\\"))
    }

    @Test
    fun copiesDirectorySkillWithAssets() {
        val sourceRoot = tempDir("workshop-source-skills")
        val directory = File(sourceRoot, "toolbox").apply { mkdirs() }
        File(directory, "SKILL.md").writeText(skillFile("toolbox", "Toolbox helper"))
        File(directory, "assets").mkdirs()
        File(directory, "assets/helper.js").writeText("console.log(1)")
        val skill = Skill(
            name = "toolbox",
            description = "Toolbox helper",
            filePath = File(directory, "SKILL.md").absolutePath,
            content = File(directory, "SKILL.md").readText(),
            baseDir = directory.absolutePath,
        )
        val targetRoot = File(tempDir("workshop-target"), "skills")

        copySelectedSkills(listOf(skill), sourceRoot, targetRoot)

        val copied = File(targetRoot, "toolbox")
        assertTrue(File(copied, "SKILL.md").isFile)
        assertEquals("console.log(1)", File(copied, "assets/helper.js").readText())
    }

    @Test
    fun copiesRootLevelSkillAsSkillFile() {
        val sourceRoot = tempDir("workshop-source-skills")
        val loose = File(sourceRoot, "loose.md").apply { writeText("# Loose skill") }
        val skill = Skill(
            name = "loose",
            description = "Loose helper",
            filePath = loose.absolutePath,
            content = loose.readText(),
            baseDir = sourceRoot.absolutePath,
        )
        val targetRoot = File(tempDir("workshop-target"), "skills")

        copySelectedSkills(listOf(skill), sourceRoot, targetRoot)

        assertEquals("# Loose skill", File(targetRoot, "loose/SKILL.md").readText())
    }

    @Test
    fun emptySkillSelectionCopiesNothing() {
        val targetRoot = File(tempDir("workshop-target"), "skills")

        copySelectedSkills(emptyList(), tempDir("workshop-source-skills"), targetRoot)

        assertFalse(targetRoot.exists())
    }

    @Test
    fun writesAppendSystemPromptOnlyWhenNonBlank() {
        val agentDir = tempDir("workshop-agent")

        writeAppendSystemPrompt(agentDir, "  ")
        writeAppendSystemPrompt(agentDir, null)
        assertFalse(File(agentDir, "APPEND_SYSTEM.md").exists())

        writeAppendSystemPrompt(agentDir, "  Be brief.  ")
        assertEquals("Be brief.", File(agentDir, "APPEND_SYSTEM.md").readText())
    }

    private val sampleModels = listOf(
        WorkshopAgentModel("deepseek", "chat", "Chat", listOf("off", "low")),
        WorkshopAgentModel("openai", "gpt", "GPT", listOf("off", "medium", "high")),
    )

    private fun request(
        prompt: String = "hello",
        providerId: String? = null,
        modelId: String? = null,
        thinkingLevel: String? = null,
        systemPrompt: String? = null,
        maxTurns: Int? = null,
        tools: List<String>? = null,
        skills: List<String>? = null,
    ): WorkshopAgentRequest = WorkshopAgentRequest(
        prompt = prompt,
        providerId = providerId,
        modelId = modelId,
        thinkingLevel = thinkingLevel,
        systemPrompt = systemPrompt,
        maxTurns = maxTurns,
        tools = tools,
        skills = skills,
    )

    private fun tool(name: String, description: String): CodingTool = CodingTool(
        name = name,
        description = description,
        parameters = JsonObject(emptyMap()),
        label = name,
        execute = { _, _, _, _ -> error("not executed in catalog tests") },
    )

    private fun skillFile(name: String, description: String): String =
        "---\nname: $name\ndescription: $description\n---\n\nBody of $name.\n"

    private fun tempDir(prefix: String): File {
        val directory = Files.createTempDirectory(prefix).toFile()
        directory.deleteOnExit()
        return directory
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }
}
