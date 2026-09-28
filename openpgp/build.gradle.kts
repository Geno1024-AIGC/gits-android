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
    api(libs.bcprov)
    api(libs.bcpg)
    implementation(libs.bcutil)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

Versioning.bumpOnPackaging(project, projectDir, android = false)
