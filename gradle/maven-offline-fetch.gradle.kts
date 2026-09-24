import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Offline Maven helpers:
 * - fetchMavens: copy app/lib resolved classpaths into D:/Tools/Android/maven_repo
 * - fetchPlugins: resolve AGP/Kotlin/KSP/Hilt plugin artifacts into the same repo
 * - syncGradleCacheToMavenRepo: copy selected groups from ~/.gradle/caches (incl. .module)
 */
val offlineMavenRepo = file("D:/Tools/Android/maven_repo")

val resolvableNameHints = listOf(
    "CompileClasspath",
    "RuntimeClasspath",
    "AnnotationProcessorClasspath",
    "annotationProcessor",
    "ksp",
    "kapt",
)

val agpVersion = "8.5.2"
val kotlinVersion = "2.0.21"
val kspVersion = "2.0.21-1.0.27"
val hiltVersion = "2.51.1"

val pluginCoordinates = listOf(
    "com.android.tools.build:gradle:$agpVersion",
    "com.android.application:com.android.application.gradle.plugin:$agpVersion",
    "com.android.library:com.android.library.gradle.plugin:$agpVersion",
    "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion",
    "org.jetbrains.kotlin.android:org.jetbrains.kotlin.android.gradle.plugin:$kotlinVersion",
    "org.jetbrains.kotlin.plugin.parcelize:org.jetbrains.kotlin.plugin.parcelize.gradle.plugin:$kotlinVersion",
    "org.jetbrains.kotlin.kapt:org.jetbrains.kotlin.kapt.gradle.plugin:$kotlinVersion",
    "com.google.devtools.ksp:symbol-processing-gradle-plugin:$kspVersion",
    "com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:$kspVersion",
    "com.google.dagger:hilt-android-gradle-plugin:$hiltVersion",
    "com.google.dagger.hilt.android:com.google.dagger.hilt.android.gradle.plugin:$hiltVersion",
)

val cacheGroupsToSync = listOf(
    "com.android.tools",
    "com.android.tools.build",
    "com.android.tools.build.jetifier",
    "com.android.application",
    "com.android.library",
    "com.android.databinding",
    "androidx.databinding",
    "org.jetbrains.kotlin",
    "org.jetbrains.kotlin.android",
    "org.jetbrains.kotlin.plugin.parcelize",
    "org.jetbrains.kotlin.kapt",
    "org.jetbrains.intellij.deps",
    "com.google.devtools.ksp",
    "com.google.dagger",
    "com.google.dagger.hilt.android",
    "com.google.guava",
    "com.google.code.gson",
    "com.google.protobuf",
    "com.google.errorprone",
    "com.squareup",
    "net.sf.jopt-simple",
    "org.ow2.asm",
    "org.jetbrains",
    "org.jetbrains.kotlinx",
    "commons-io",
    "commons-codec",
    "org.apache.commons",
    "org.apache.httpcomponents",
    "org.checkerframework",
    "javax.inject",
    "net.java.dev.jna",
)

fun org.gradle.api.artifacts.Configuration.shouldFetch(): Boolean {
    if (!isCanBeResolved) return false
    val n = name
    return resolvableNameHints.any { n.contains(it, ignoreCase = false) } ||
        n.contains("ksp", ignoreCase = true) ||
        n.contains("kapt", ignoreCase = true)
}

fun mavenArtifactFileName(
    artifactId: String,
    version: String,
    extension: String,
    classifier: String?
): String {
    val ext = extension.ifBlank { "jar" }
    return if (classifier.isNullOrBlank()) {
        "$artifactId-$version.$ext"
    } else {
        "$artifactId-$version-$classifier.$ext"
    }
}

fun copyIntoMavenRepo(
    repo: java.io.File,
    group: String,
    artifactId: String,
    version: String,
    source: java.io.File,
    extension: String,
    classifier: String? = null
) {
    if (!source.isFile) return
    val destDir = repo.resolve(group.replace('.', '/')).resolve(artifactId).resolve(version)
    destDir.mkdirs()
    val dest = destDir.resolve(mavenArtifactFileName(artifactId, version, extension, classifier))
    if (!dest.exists() || dest.length() != source.length()) {
        Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

fun copyResolvedArtifact(repo: java.io.File, artifact: ResolvedArtifact) {
    val mvi = artifact.moduleVersion.id
    val ext = artifact.extension?.ifBlank { null } ?: artifact.type ?: "jar"
    copyIntoMavenRepo(
        repo = repo,
        group = mvi.group,
        artifactId = mvi.name,
        version = mvi.version,
        source = artifact.file,
        extension = ext,
        classifier = artifact.classifier
    )
}

fun Project.copyPomsFor(artifact: ResolvedArtifact, repo: java.io.File) {
    val componentId = artifact.id.componentIdentifier
    if (componentId !is ModuleComponentIdentifier) return
    runCatching {
        val result = dependencies.createArtifactResolutionQuery()
            .forComponents(componentId)
            .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
            .execute()
        result.resolvedComponents.forEach { component ->
            component.getArtifacts(MavenPomArtifact::class.java)
                .filterIsInstance<ResolvedArtifactResult>()
                .forEach { pom ->
                    copyIntoMavenRepo(
                        repo = repo,
                        group = componentId.group,
                        artifactId = componentId.module,
                        version = componentId.version,
                        source = pom.file,
                        extension = "pom"
                    )
                }
        }
    }
}

fun syncGradleCacheGroups(repo: java.io.File, groups: Collection<String>?, logger: org.gradle.api.logging.Logger): Int {
    val cacheRoot = File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1")
    if (!cacheRoot.isDirectory) {
        logger.warn("Gradle module cache not found: {}", cacheRoot)
        return 0
    }

    val groupDirs = if (groups == null) {
        cacheRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
    } else {
        groups.map { cacheRoot.resolve(it) }.filter { it.isDirectory }
    }

    var copied = 0
    groupDirs.forEach { groupDir ->
        val group = groupDir.name
        groupDir.listFiles()?.forEach { artifactDir ->
            if (!artifactDir.isDirectory) return@forEach
            artifactDir.listFiles()?.forEach { versionDir ->
                if (!versionDir.isDirectory) return@forEach
                versionDir.walkTopDown().maxDepth(2).filter { it.isFile }.forEach { cached ->
                    val destDir = repo.resolve(group.replace('.', '/'))
                        .resolve(artifactDir.name)
                        .resolve(versionDir.name)
                    destDir.mkdirs()
                    val dest = destDir.resolve(cached.name)
                    if (!dest.exists() || dest.length() != cached.length()) {
                        Files.copy(cached.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        copied++
                    }
                }
            }
        }
    }
    return copied
}

val offlinePluginClasspath by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    isVisible = false
    description = "Plugin artifacts to mirror for offline builds"
}

dependencies {
    pluginCoordinates.forEach { coord ->
        offlinePluginClasspath(coord)
    }
}

tasks.register("fetchPlugins") {
    group = "maven-offline"
    description = "Resolve Gradle plugins and copy them into D:/Tools/Android/maven_repo"

    notCompatibleWithConfigurationCache("Resolves plugin classpath into offline maven repo")

    doLast {
        offlineMavenRepo.mkdirs()
        var copied = 0
        val seen = mutableSetOf<String>()

        val artifacts = offlinePluginClasspath.resolvedConfiguration.lenientConfiguration.artifacts
        logger.lifecycle("fetchPlugins resolved {} artifacts", artifacts.size)

        artifacts.forEach { artifact ->
            val mvi = artifact.moduleVersion.id
            val key = "${mvi.group}:${mvi.name}:${mvi.version}:${artifact.classifier}:${artifact.extension}"
            if (!seen.add(key)) return@forEach
            copyResolvedArtifact(offlineMavenRepo, artifact)
            project.copyPomsFor(artifact, offlineMavenRepo)
            copied++
        }

        val cacheCopied = syncGradleCacheGroups(offlineMavenRepo, null, logger)
        logger.lifecycle(
            "fetchPlugins finished -> {}\n  resolved copied: {}\n  cache files synced: {}",
            offlineMavenRepo.absolutePath,
            copied,
            cacheCopied
        )
    }
}

tasks.register("syncGradleCacheToMavenRepo") {
    group = "maven-offline"
    description = "Copy ALL groups from ~/.gradle/caches into the offline maven repo"

    doLast {
        offlineMavenRepo.mkdirs()
        val copied = syncGradleCacheGroups(offlineMavenRepo, null, logger)
        logger.lifecycle("syncGradleCacheToMavenRepo copied {} files -> {}", copied, offlineMavenRepo)
    }
}

tasks.register("fetchMavens") {
    group = "maven-offline"
    description = "Fetch resolved app dependencies into D:/Tools/Android/maven_repo (Maven layout)"

    notCompatibleWithConfigurationCache("Walks all project configurations and copies artifacts")

    doLast {
        offlineMavenRepo.mkdirs()

        var copied = 0
        var skippedNonModule = 0
        val seen = mutableSetOf<String>()

        allprojects.forEach { proj ->
            val configs = proj.configurations
                .filter { it.shouldFetch() }
                .sortedBy { it.name }

            if (configs.isEmpty()) {
                logger.lifecycle("[{}] no resolvable classpath configs", proj.path)
                return@forEach
            }

            configs.forEach { config ->
                val artifacts = runCatching {
                    config.resolvedConfiguration.lenientConfiguration.artifacts
                }.onFailure { err ->
                    logger.warn("[{}:{}] resolve failed: {}", proj.path, config.name, err.message)
                }.getOrNull().orEmpty()

                if (artifacts.isEmpty()) {
                    logger.lifecycle("[{}:{}] 0 artifacts", proj.path, config.name)
                    return@forEach
                }

                logger.lifecycle("[{}:{}] {} artifacts", proj.path, config.name, artifacts.size)

                artifacts.forEach { artifact ->
                    val mvi = artifact.moduleVersion.id
                    val key = "${mvi.group}:${mvi.name}:${mvi.version}:${artifact.classifier}:${artifact.extension}:${artifact.file.length()}"
                    if (!seen.add(key)) return@forEach

                    copyResolvedArtifact(offlineMavenRepo, artifact)
                    copied++
                    proj.copyPomsFor(artifact, offlineMavenRepo)

                    if (artifact.id.componentIdentifier !is ModuleComponentIdentifier) {
                        skippedNonModule++
                    }
                }
            }
        }

        val cacheCopied = syncGradleCacheGroups(
            offlineMavenRepo,
            cacheGroupsToSync + listOf(
                "androidx.datastore",
                "androidx.room",
                "androidx.media3",
                "androidx.media",
                "androidx.core",
                "androidx.appcompat",
                "androidx.navigation",
                "androidx.recyclerview",
                "androidx.fragment",
                "androidx.activity",
                "androidx.lifecycle",
                "androidx.savedstate",
                "androidx.sqlite",
                "com.google.android.material",
                "com.github.bumptech.glide",
                "com.github.yalantis",
                "com.pranavpandey.android",
                "jp.wasabeef",
                "net.jthink",
                "junit",
                "org.robolectric",
            ),
            logger
        )

        logger.lifecycle(
            """
            |fetchMavens finished -> ${offlineMavenRepo.absolutePath}
            |  unique module files copied: $copied
            |  non-module artifacts skipped: $skippedNonModule
            |  cache files synced: $cacheCopied
            """.trimMargin()
        )
    }
}

tasks.register("prepareOfflineMavenRepo") {
    group = "maven-offline"
    description = "Fetch plugins + app deps into the offline maven repo (run while online)"
    dependsOn("fetchPlugins", "fetchMavens")
}
