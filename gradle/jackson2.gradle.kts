import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import java.util.zip.ZipFile

fun jackson2Group(group: String): Boolean =
    group == "com.fasterxml.jackson" || group.startsWith("com.fasterxml.jackson.")

fun jackson2Implementation(group: String, name: String): Boolean =
    jackson2Group(group) && !(group == "com.fasterxml.jackson.core" && name == "jackson-annotations")

val jackson2ApplicationModules = setOf(
    "com.fasterxml.jackson:jackson-bom:2.22.4",
    "com.fasterxml.jackson.core:jackson-core:2.22.4",
    "com.fasterxml.jackson.core:jackson-databind:2.22.4",
    "com.fasterxml.jackson.module:jackson-module-kotlin:2.22.4",
)
val jackson2ProcessorModules = setOf(
    "com.fasterxml.jackson:jackson-bom:2.21.0",
    "com.fasterxml.jackson.core:jackson-core:2.21.0",
    "com.fasterxml.jackson.core:jackson-databind:2.21.0",
    "com.fasterxml.jackson.module:jackson-module-kotlin:2.21.0",
    "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.0",
    "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.0",
)
val jackson2GeneratorEdges = setOf(
    "io.javalin.community.openapi:openapi-generator -> com.fasterxml.jackson.core:jackson-databind",
    "io.javalin.community.openapi:openapi-generator -> com.fasterxml.jackson.module:jackson-module-kotlin",
)
val jackson2RuntimeEdges = jackson2GeneratorEdges + setOf(
    "com.auth0:java-jwt -> com.fasterxml.jackson.core:jackson-core",
    "com.auth0:java-jwt -> com.fasterxml.jackson.core:jackson-databind",
    "com.auth0:jwks-rsa -> com.fasterxml.jackson.core:jackson-databind",
    "org.opensearch.client:opensearch-java -> com.fasterxml.jackson.core:jackson-core",
    "org.opensearch.client:opensearch-java -> com.fasterxml.jackson.core:jackson-databind",
)
val jackson2ProcessorEdges = jackson2GeneratorEdges + setOf(
    "io.javalin.community.openapi:openapi-annotation-processor -> com.fasterxml.jackson.dataformat:jackson-dataformat-yaml",
    "io.javalin.community.openapi:openapi-annotation-processor -> com.fasterxml.jackson.datatype:jackson-datatype-jsr310",
)

val jackson2Baseline = buildMap<String, Set<String>> {
    listOf("compileClasspath", "testCompileClasspath").forEach {
        put(it, jackson2ApplicationModules + jackson2GeneratorEdges + "project -> com.fasterxml.jackson:jackson-bom")
    }
    listOf("runtimeClasspath", "testRuntimeClasspath").forEach {
        put(it, jackson2ApplicationModules + jackson2RuntimeEdges + "project -> com.fasterxml.jackson:jackson-bom")
    }
    listOf("kapt", "kaptTest", "kaptClasspath_kaptKotlin", "kaptClasspath_kaptTestKotlin").forEach {
        put(it, jackson2ProcessorModules + jackson2ProcessorEdges)
    }
}

fun jackson2Findings(): Map<String, Set<String>> {
    val scopes = configurations.filter { it.isCanBeResolved }.associateBy { it.name } +
        project.buildscript.configurations.filter { it.isCanBeResolved }.associateBy { "buildscript:${it.name}" }
    return scopes.toSortedMap().mapValues { (label, configuration) ->
        val result = configuration.incoming.resolutionResult
        val failures = result.allDependencies.filterIsInstance<UnresolvedDependencyResult>()
        check(failures.isEmpty()) {
            failures.joinToString("\n") { "$label: ${it.requested}: ${it.failure.message}" }
        }
        buildSet {
            result.allComponents.mapNotNull { it.moduleVersion }
                .filter { jackson2Implementation(it.group, it.name) }
                .forEach { add(it.toString()) }
            result.allDependencies.filterIsInstance<ResolvedDependencyResult>().filterNot { it.isConstraint }
                .forEach { edge ->
                    val target = edge.selected.moduleVersion
                    val source = edge.from.moduleVersion
                    if (target != null && jackson2Implementation(target.group, target.name) &&
                        (source == null || !jackson2Group(source.group))) {
                        val parent = if (edge.from == result.rootComponent.get()) "project"
                            else source?.let { "${it.group}:${it.name}" } ?: edge.from.id.displayName
                        add("$parent -> ${target.group}:${target.name}")
                    }
                }
        }
    }.filterValues { it.isNotEmpty() }
}

fun writeJackson2Report(findings: Map<String, Set<String>>) {
    val report = layout.buildDirectory.file("reports/jackson2/dependencies.txt").get().asFile
    report.parentFile.mkdirs()
    report.writeText(findings.flatMap { (scope, entries) ->
        entries.sorted().map { "$scope | $it" }
    }.joinToString("\n", postfix = "\n"))
    logger.lifecycle("Jackson 2 dependency report: $report")
}

fun bundledJackson2Findings(): Set<String> {
    val scopes = configurations.filter { it.isCanBeResolved }.associateBy { it.name } +
        project.buildscript.configurations.filter { it.isCanBeResolved }.associateBy { "buildscript:${it.name}" }
    val inspected = mutableMapOf<File, List<String>>()
    return scopes.flatMap { (label, configuration) ->
        configuration.incoming.artifacts.artifacts.filter { it.file.extension == "jar" }.flatMap { artifact ->
            val component = artifact.id.componentIdentifier
            val coordinate = if (component is ModuleComponentIdentifier)
                "${component.group}:${component.module}:${component.version}" else component.displayName
            if (component is ModuleComponentIdentifier && jackson2Implementation(component.group, component.module)) {
                emptyList()
            } else {
                val prefixes = inspected.getOrPut(artifact.file) {
                    ZipFile(artifact.file).use { jar ->
                        jar.entries().asSequence().map { it.name }
                            .filter { it.endsWith(".class") && it.contains("com/fasterxml/jackson/") }
                            .filterNot { it.contains("com/fasterxml/jackson/annotation/") }
                            .map { it.substringBefore("com/fasterxml/jackson/") + "com/fasterxml/jackson/" }
                            .distinct().toList()
                    }
                }
                prefixes.map { "$label | $coordinate | $it" }
            }
        }
    }.toSortedSet()
}

val bundledJackson2Baseline = listOf("testCompileClasspath", "testRuntimeClasspath").flatMap { scope ->
    listOf(
        "$scope | org.wiremock:wiremock-standalone:3.13.2 | wiremock/com/fasterxml/jackson/",
        "$scope | org.wiremock:wiremock-standalone:3.13.2 | META-INF/versions/11/wiremock/com/fasterxml/jackson/",
    )
}.toSet()

fun writeBundledJackson2Report(findings: Set<String>) {
    val report = layout.buildDirectory.file("reports/jackson2/bundled.txt").get().asFile
    report.parentFile.mkdirs()
    report.writeText(findings.sorted().joinToString("\n", postfix = "\n"))
    logger.lifecycle("Bundled Jackson 2 class inventory: $report")
}

val reportJackson2Dependencies = tasks.register("reportJackson2Dependencies") {
    group = "verification"
    description = "Reports Jackson 2 modules and their consumers across project and buildscript configurations."
    notCompatibleWithConfigurationCache("Inspects all dependency configurations at execution time.")
    doLast { writeJackson2Report(jackson2Findings()) }
}

val verifyJackson2Baseline = tasks.register("verifyJackson2Baseline") {
    group = "verification"
    description = "Rejects new Jackson 2 dependencies and requires removal of obsolete baseline entries."
    notCompatibleWithConfigurationCache("Inspects all dependency configurations at execution time.")
    doLast {
        val actual = jackson2Findings()
        writeJackson2Report(actual)
        val bundled = bundledJackson2Findings()
        writeBundledJackson2Report(bundled)
        val changes = (actual.keys + jackson2Baseline.keys).sorted().flatMap { scope ->
            val found = actual[scope].orEmpty()
            val expected = jackson2Baseline[scope].orEmpty()
            (found - expected).sorted().map { "$scope: unexpected $it" } +
                (expected - found).sorted().map { "$scope: remove obsolete baseline entry $it" }
        }
        val bundledChanges = (bundled - bundledJackson2Baseline).map { "unexpected bundled implementation $it" } +
            (bundledJackson2Baseline - bundled).map { "remove obsolete bundled baseline entry $it" }
        check(changes.isEmpty() && bundledChanges.isEmpty()) {
            "Jackson 2 baseline changed. Review dependency paths; do not add blanket exclusions.\n" +
                (changes + bundledChanges).joinToString("\n")
        }
    }
}

tasks.register("verifyNoJackson2") {
    group = "verification"
    description = "Requires zero Jackson 2 dependencies except the shared annotations artifact."
    notCompatibleWithConfigurationCache("Inspects all dependency configurations at execution time.")
    doLast {
        val findings = jackson2Findings()
        writeJackson2Report(findings)
        val bundled = bundledJackson2Findings()
        writeBundledJackson2Report(bundled)
        check(findings.isEmpty() && bundled.isEmpty()) {
            "Jackson 2 consumers remain; see build/reports/jackson2/dependencies.txt and bundled.txt"
        }
    }
}

tasks.register("reportBundledJackson2") {
    group = "verification"
    description = "Inspects resolved jars for original or prefix-relocated Jackson 2 implementation classes."
    notCompatibleWithConfigurationCache("Inspects all resolved dependency artifacts at execution time.")
    doLast {
        writeBundledJackson2Report(bundledJackson2Findings())
    }
}

tasks.named("check") {
    dependsOn(verifyJackson2Baseline)
}
