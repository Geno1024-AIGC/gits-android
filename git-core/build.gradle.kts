import com.geno1024.ai.gits.gradle.Versioning
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

val stamp = Versioning.stamp(rootDir, projectDir)

group = "com.geno1024.ai.gits"
version = stamp.versionName

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

dependencies {
    api(project(":openpgp"))
    api(libs.jgit)
    api(libs.jgit.ssh)
    implementation(libs.slf4j.api)
    implementation(libs.jgit.gpg.bc)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.slf4j.simple)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
}

Versioning.bumpOnPackaging(project, projectDir, android = false)
