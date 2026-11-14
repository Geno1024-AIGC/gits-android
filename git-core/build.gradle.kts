import com.geno1024.ai.gits.gradle.Versioning
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Count this packaging build before reading the stamp, so the jar carries its
// own number and count.pack stays equal to the number of packaging builds that
// have happened. Failures count too: the number is decided at configuration time.
Versioning.bumpOnPackaging(project, projectDir)

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
