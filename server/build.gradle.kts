import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinKsp)
}

// The sqlx4k Gradle plugin (io.github.smyrgeorge.sqlx4k) is NOT applied here:
// the code generator is wired manually instead (see the `ksp` block below and the
// commonMain source set) — it runs with the `jvm` compilation (kspKotlinJvm), and its
// output is shared through commonMain, so every target compilation (jvm, linuxX64)
// compiles it; the generated code uses only common sqlx4k APIs, and no native KSP
// run is needed.
ksp {
    arg("dialect", "postgresql")
    arg("output-package", "ru.herobrine1st.memeindex.server.generated.sqlx4k")
}

kotlin {
    jvmToolchain(21)

    jvm {
        // Replaces the `application` plugin (incompatible with KMP): creates a
        // `runJvm` task and a Gradle distribution (installDist).
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        binaries {
            executable {
                mainClass.set("ru.herobrine1st.memeindex.server.MainKt")
            }
        }
    }

    // Native target: `shared`, sqlx4k(-postgres) (libpq via Rust cinterop) and every
    // ktor piece we use all publish linuxX64. The remaining platform boundary is
    // toPng (stub), mtime, env and cwd (see Platform.kt / NativePlatform.kt).
//    linuxX64 {
//        binaries {
//            executable()
//        }
//    }

    sourceSets {
        commonMain {
            // Output of the sqlx4k codegen KSP task (the `jvm` compilation). It must live in
            // exactly one source set — a file cannot belong to two — so it is kept in
            // commonMain only (removed from jvmMain below, where KSP wires it by default).
            kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/jvm/jvmMain/kotlin"))
            dependencies {
                implementation(projects.shared)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.resources)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.server.cors)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlin.logging)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.io.core)
                implementation(libs.ksoup)
                implementation(libs.kfswatch)
                implementation(libs.sqlx4k.postgres)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.twelvemonkeys.imageio.webp)
                runtimeOnly(libs.slf4j.simple)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.testJunit)
                implementation(libs.junit)
            }
        }
    }
}

// sqlx4k code generator: registered on the `jvm` KSP configuration, so it runs in the
// kspKotlinJvm task; its output is wired into commonMain (see above).
dependencies {
    // `kspJvm` is the KSP processor configuration of the `jvm` target (KSP 2.3.x naming);
    // no typed accessor is generated for it, so add the configuration by name.
    add("kspJvm", libs.sqlx4k.codegen)
}

// KSP wires the codegen output into jvmMain automatically; commonMain lists it too (above),
// and a file may belong to only one source set, so drop it from jvmMain. The jvm compilation
// still sees it through the commonMain sources. Done after evaluation, because KSP registers
// the directory while configuring the jvm target.
val jvmMainSourceSet = kotlin.sourceSets.named("jvmMain").get()
val jvmMainKotlin = jvmMainSourceSet.kotlin
@OptIn(ExperimentalKotlinGradlePluginApi::class)
val jvmMainGeneratedKotlin = jvmMainSourceSet.generatedKotlin
val generatedKspDir = layout.buildDirectory.dir("generated/ksp/jvm/jvmMain/kotlin")
project.afterEvaluate {
    val dir = generatedKspDir.get().asFile.absolutePath
    jvmMainKotlin.setSrcDirs(jvmMainKotlin.srcDirs.filterNot { it.absolutePath == dir })
    jvmMainGeneratedKotlin.setSrcDirs(jvmMainGeneratedKotlin.srcDirs.filterNot { it.absolutePath == dir })
}

// Every compilation consuming commonMain (metadata + jvm + linuxX64) must wait for the
// KSP task that produces the codegen output (removing it from jvmMain also dropped
// KSP's own ordering).
tasks.named("compileKotlinMetadata").configure {
    dependsOn("kspKotlinJvm")
}

tasks.named("compileKotlinJvm").configure {
    dependsOn("kspKotlinJvm")
}

//tasks.named("compileKotlinLinuxX64").configure {
//    dependsOn("kspKotlinJvm")
//}

// KSP auto-creates a KSP task for every target, including linuxX64, but no
// codegen processor is registered there (kspLinuxX64 is empty): the native
// compilation consumes the JVM codegen output through commonMain. Order the
// task after the JVM codegen (its sources include that output) and disable it.
// Done after evaluation, because KSP registers the task while configuring the
// target, i.e. later than this script body.
//project.afterEvaluate {
//    tasks.named("kspKotlinLinuxX64").configure {
//        dependsOn("kspKotlinJvm")
//        enabled = false
//    }
//}
