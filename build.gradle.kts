plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.baselineprofile) apply false
}

// ---------------------------------------------------------------------------------------------
// Repo-wide gates (section 9 of the plan). `./gradlew driftChecks` runs all of them.
// ---------------------------------------------------------------------------------------------

/** Allowed project dependencies per module pattern. Anything not matched is rejected. */
val dependencyRules: Map<Regex, (String) -> Boolean> = mapOf(
    Regex(":core:model") to { _ -> false },
    Regex(":core:engine") to { d -> d == ":core:model" },
    Regex(":source:.*") to { d -> d == ":core:model" },
    Regex(":core:designsystem") to { d -> d == ":core:model" },
    Regex(":core:charts") to { d -> d in setOf(":core:model", ":core:designsystem") },
    Regex(":core:data") to { d -> d == ":core:model" },
    Regex(":core:background") to { d -> d in setOf(":core:model", ":core:designsystem", ":core:charts") },
    Regex(":feature:.*") to { d -> d in setOf(":core:model", ":core:designsystem", ":core:charts", ":testing") },
    Regex(":testing") to { d -> d == ":core:model" },
    Regex(":app") to { _ -> true },
)

tasks.register("checkDependencyRules") {
    group = "verification"
    description = "Fails if a module depends on a project it is not allowed to (plan section 2)."
    doLast {
        val violations = mutableListOf<String>()
        subprojects.forEach { p ->
            val rule = dependencyRules.entries.firstOrNull { it.key.matches(p.path) }?.value ?: return@forEach
            p.configurations
                // Test configurations may use :testing fixtures; production configurations are checked.
                .filter { !it.name.contains("test", ignoreCase = true) }
                .forEach { conf ->
                    conf.dependencies.withType(ProjectDependency::class.java).forEach { dep ->
                        val target = dep.path
                        if (target != p.path && !rule(target)) violations += "${p.path} (${conf.name}) -> $target"
                    }
                }
        }
        if (violations.isNotEmpty()) throw GradleException("Dependency rule violations:\n" + violations.distinct().joinToString("\n"))
        logger.lifecycle("Dependency rules OK")
    }
}

tasks.register("checkNoInternetPermission") {
    group = "verification"
    description = "Fails if any source manifest or merged app manifest grants INTERNET."
    doLast {
        val bad = fileTree(rootDir) {
            include("**/src/**/AndroidManifest.xml", "app/build/intermediates/merged_manifest*/**/AndroidManifest.xml")
            exclude("**/node_modules/**", "build-logic/**")
        }.filter { f ->
            f.readLines().any { line ->
                line.contains("android.permission.INTERNET") && !line.contains("tools:node=\"remove\"")
            }
        }
        if (!bad.isEmpty) throw GradleException("INTERNET permission found in:\n" + bad.files.joinToString("\n"))
        logger.lifecycle("No INTERNET permission OK")
    }
}

tasks.register("checkNoPlaceholders") {
    group = "verification"
    description = "Fails on TODO/FIXME/placeholder/lorem text in production source sets."
    doLast {
        val pattern = Regex("""\b(TODO|FIXME|XXX)\b|placeholder|lorem ipsum|mock data""", RegexOption.IGNORE_CASE)
        val hits = mutableListOf<String>()
        fileTree(rootDir) {
            include("**/src/main/**/*.kt", "**/src/main/**/*.xml", "**/src/main/**/*.json")
            exclude("build-logic/**", "**/build/**", "testing/**")
        }.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                // `placeholder` is a legitimate Compose parameter name (TextField); allow that exact API use.
                val cleaned = line.replace(Regex("""placeholder\s*=\s*\{"""), "")
                if (pattern.containsMatchIn(cleaned)) hits += "${f.relativeTo(rootDir)}:${i + 1}: ${line.trim()}"
            }
        }
        if (hits.isNotEmpty()) throw GradleException("Placeholder text in production code:\n" + hits.joinToString("\n"))
        logger.lifecycle("No placeholders OK")
    }
}

tasks.register("driftChecks") {
    group = "verification"
    description = "All repo-wide gates: dependency rules, no INTERNET, no placeholders."
    dependsOn("checkDependencyRules", "checkNoInternetPermission", "checkNoPlaceholders")
}
