plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
}

tasks.register("printVersion") {
    val stamp = com.geno1024.ai.gits.gradle.Versioning.stamp(rootDir, projectDir)
    doLast { println(stamp.versionName) }
}
