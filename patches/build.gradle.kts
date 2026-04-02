import org.gradle.api.GradleException
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.net.URL
import java.util.Properties

plugins {
    kotlin("jvm")
}

group = "dev.kareemlukitomo.revanced"
version = providers.gradleProperty("version").get()

val revancedCliVersion = "6.0.0"
val managerBuildToolsVersion = providers.gradleProperty("androidBuildToolsVersion").orElse("37.0.0")
val revancedCliJarName = "revanced-cli-$revancedCliVersion-all.jar"
val revancedCliJar = layout.buildDirectory.file("downloads/$revancedCliJarName")
val revancedCliDownloadUrl =
    "https://github.com/ReVanced/revanced-cli/releases/download/v$revancedCliVersion/$revancedCliJarName"

fun resolveAndroidBuildToolsDir(): File {
    providers.gradleProperty("androidBuildToolsDir").orNull?.let { overridePath ->
        val overrideDir = file(overridePath)
        if (overrideDir.resolve("lib/d8.jar").isFile) return overrideDir
        throw GradleException("Configured androidBuildToolsDir does not contain lib/d8.jar: ${overrideDir.absolutePath}")
    }
    System.getenv("ANDROID_BUILD_TOOLS_DIR")?.let { overridePath ->
        val overrideDir = File(overridePath)
        if (overrideDir.resolve("lib/d8.jar").isFile) return overrideDir
        throw GradleException("Configured ANDROID_BUILD_TOOLS_DIR does not contain lib/d8.jar: ${overrideDir.absolutePath}")
    }

    val sdkRoots = buildList {
        rootProject.file("local.properties").takeIf(File::isFile)?.let { localPropertiesFile ->
            val properties = Properties()
            localPropertiesFile.inputStream().use(properties::load)
            properties.getProperty("sdk.dir")?.let(::File)?.let(::add)
        }
        System.getenv("ANDROID_SDK_ROOT")?.let(::File)?.let(::add)
        System.getenv("ANDROID_HOME")?.let(::File)?.let(::add)
    }

    val requestedVersion = managerBuildToolsVersion.get()
    sdkRoots.forEach { sdkRoot ->
        val buildToolsDir = sdkRoot.resolve("build-tools/$requestedVersion")
        if (buildToolsDir.resolve("lib/d8.jar").isFile) return buildToolsDir
    }

    throw GradleException(
        "Could not find Android build-tools $requestedVersion. " +
            "Set ANDROID_SDK_ROOT/ANDROID_HOME or pass -PandroidBuildToolsDir=/path/to/build-tools/$requestedVersion.",
    )
}

val downloadRevancedCli by tasks.registering {
    outputs.file(revancedCliJar)

    doLast {
        val output = revancedCliJar.get().asFile
        if (output.exists() && output.length() > 0) return@doLast

        output.parentFile.mkdirs()
        URL(revancedCliDownloadUrl).openStream().use { input ->
            output.outputStream().use { fileOut ->
                input.copyTo(fileOut)
            }
        }
    }
}

dependencies {
    compileOnly(files(revancedCliJar))
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(downloadRevancedCli)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    compilerOptions.freeCompilerArgs.addAll(
        "-Xskip-metadata-version-check",
        "-Xskip-prerelease-check",
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.jar {
    dependsOn(downloadRevancedCli)
    archiveBaseName.set("patches")
    archiveExtension.set("rvp")
    manifest {
        attributes(
            "name" to "Kareem ReVanced Patches",
            "version" to version.toString(),
            "description" to "Custom Twitter/X patches by Kareem",
            "source" to "https://github.com/kareemlukitomo/revanced-patches-kareem",
            "author" to "Kareem Lukitomo",
            "contact" to "https://github.com/kareemlukitomo",
            "website" to "https://github.com/kareemlukitomo/revanced-patches-kareem",
            "license" to "GNU General Public License v3.0",
        )
    }
}

val buildAndroidBundle by tasks.registering {
    dependsOn(tasks.jar)
    val bundleArtifact = tasks.jar.flatMap { it.archiveFile }
    val androidBundleArtifact = layout.buildDirectory.file("libs/patches-${project.version}-android.rvp")
    inputs.file(bundleArtifact)
    outputs.file(androidBundleArtifact)

    doLast {
        val bundleFile = bundleArtifact.get().asFile
        val d8Jar = resolveAndroidBuildToolsDir().resolve("lib/d8.jar")
        val androidBundleDir = layout.buildDirectory.dir("androidBundle").get().asFile
        val inputJar = androidBundleDir.resolve("${bundleFile.nameWithoutExtension}.jar")
        val dexDir = androidBundleDir.resolve("dex")
        val stagingDir = androidBundleDir.resolve("staging")
        val rebuiltBundle = androidBundleArtifact.get().asFile
        val javaExecutable = File(System.getProperty("java.home"), "bin/java")
        if (!javaExecutable.isFile) {
            throw GradleException("Could not find Java executable at ${javaExecutable.absolutePath}")
        }

        dexDir.deleteRecursively()
        stagingDir.deleteRecursively()
        rebuiltBundle.delete()
        inputJar.delete()
        rebuiltBundle.parentFile.mkdirs()
        dexDir.mkdirs()
        stagingDir.mkdirs()
        bundleFile.copyTo(inputJar, overwrite = true)

        exec {
            executable = javaExecutable.absolutePath
            args(
                "-cp",
                d8Jar.absolutePath,
                "com.android.tools.r8.D8",
                "--release",
                "--min-api",
                "21",
                "--output",
                dexDir.absolutePath,
                inputJar.absolutePath,
            )
        }
        if (!dexDir.resolve("classes.dex").isFile) {
            throw GradleException("D8 did not produce classes.dex for ${bundleFile.name}")
        }

        copy {
            from(zipTree(bundleFile))
            into(stagingDir)
        }
        copy {
            from(dexDir.resolve("classes.dex"))
            into(stagingDir)
        }

        ant.withGroovyBuilder {
            "zip"(
                "destfile" to rebuiltBundle.absolutePath,
                "basedir" to stagingDir.absolutePath,
            )
        }
    }
}

tasks.build {
    dependsOn(buildAndroidBundle)
}
