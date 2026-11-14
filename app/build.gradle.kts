import com.geno1024.ai.gits.gradle.Versioning
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Count this packaging build before reading the stamp, so the APK carries its
// own number and count.pack stays equal to the number of packaging builds that
// have happened. Failures count too: the number is decided at configuration time.
Versioning.bumpOnPackaging(project, projectDir)

val stamp = Versioning.stamp(rootDir, projectDir)

android {
    namespace = "com.geno1024.ai.gits"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.geno1024.ai.gits"
        minSdk = 26
        targetSdk = 37
        versionCode = stamp.versionCode
        versionName = stamp.versionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // The about screen reads the stamped version name, which only BuildConfig carries.
        buildConfig = true
    }

    // The org's shared signing key, read the way RULES/KEYSTORE.md orders it:
    // environment first, then a git-ignored local.properties, and nothing written
    // down in the repository itself. A missing key is not an error — the build
    // still works, it just cannot be installed over another one.
    val signingProps = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    fun signingSecret(property: String, env: String): String? =
        System.getenv(env)?.takeIf { it.isNotBlank() }
            ?: signingProps.getProperty(property)?.takeIf { it.isNotBlank() }

    val signingStoreFile = signingSecret("keystore.storeFile", "KEYSTORE_STORE_FILE")
    val signingStorePassword = signingSecret("keystore.storePassword", "KEYSTORE_STORE_PASSWORD")

    signingConfigs {
        if (signingStoreFile != null && signingStorePassword != null) {
            create("release") {
                storeFile = file(signingStoreFile)
                storePassword = signingStorePassword
                keyPassword = signingSecret("keystore.keyPassword", "KEYSTORE_KEY_PASSWORD")
                    ?: signingStorePassword
                keyAlias = signingSecret("keystore.keyAlias", "KEYSTORE_KEY_ALIAS") ?: "geno"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // The published build is the debug one, so the debug variant is the one that
            // has to be signed with the shared key; without it this falls back to the
            // machine-local debug key and no two builds can replace each other.
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Nothing publishes this variant today, but it is assembled with the same
            // shared key whenever it is.
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/**",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "OSGI-INF/l10n/plugin.properties",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":git-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
