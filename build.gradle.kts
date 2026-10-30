plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val verifyModuleBoundaries = tasks.register("verifyModuleBoundaries") {
    group = "verification"
    description = "Checks app -> runtime/shell/design and core-only dependencies."
}

gradle.projectsEvaluated {
    val allowed = mapOf(
        ":app" to setOf(":runtime", ":shell", ":design"),
        ":runtime" to emptySet(),
        ":shell" to emptySet(),
        ":design" to emptySet(),
    )
    val undeclared = subprojects.map { it.path }.filterNot { it in allowed }
    check(undeclared.isEmpty()) { "Modules missing from the boundary table: ${undeclared.joinToString()}" }
    val violations = subprojects.flatMap { module ->
        val targets = allowed.getValue(module.path)
        module.configurations.flatMap { configuration ->
            configuration.dependencies.withType<org.gradle.api.artifacts.ProjectDependency>().mapNotNull { dependency ->
                val target = dependency.path
                when {
                    target == module.path -> null
                    target in targets -> null
                    else -> "${module.path} -> $target"
                }
            }
        }
    }.distinct()
    verifyModuleBoundaries.configure {
        inputs.property("violations", violations)
        doLast {
            check(violations.isEmpty()) { "Invalid module dependencies: ${violations.joinToString()}" }
            logger.lifecycle("Module boundaries verified: app -> runtime/shell/design")
        }
    }

    val unitTestTasks = subprojects.map { it.tasks.named("testDebugUnitTest") }
    val lintTasks = subprojects.mapNotNull { it.tasks.findByName("lintDebug") }
    tasks.named("check") {
        dependsOn(verifyModuleBoundaries)
        dependsOn(unitTestTasks)
        dependsOn(lintTasks)
    }
    subprojects {
        tasks.matching { it.name == "check" }.configureEach {
            dependsOn(verifyModuleBoundaries)
        }
    }
}
