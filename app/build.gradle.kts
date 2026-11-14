import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val baseVersionMajor = 0
val baseVersionMinor = 0

fun computeGitCommitCount(): Int {
    return runCatching {
        val stdout = ByteArrayOutputStream()
        project.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            standardOutput = stdout
        }
        stdout.toString().trim().toInt()
    }.getOrElse {
        System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 2
    }
}

val gitCommitCount: Int = computeGitCommitCount()

// Ensure versionCode strictly increments (> 1, since release v0.0.1 had versionCode 1)
val appVersionCode: Int = (project.findProperty("versionCode") as? String)?.toIntOrNull()
    ?: System.getenv("VERSION_CODE")?.toIntOrNull()
    ?: gitCommitCount.coerceAtLeast(2)

// Auto-bump versionName: 0.0.2, 0.0.3, ... or use tag/env if provided
val appVersionName: String = (project.findProperty("versionName") as? String)
    ?: System.getenv("VERSION_NAME")
    ?: runCatching {
        val tagOutput = ByteArrayOutputStream()
        project.exec {
            commandLine("git", "describe", "--tags", "--exact-match")
            standardOutput = tagOutput
            isIgnoreExitValue = true
        }
        val tag = tagOutput.toString().trim().removePrefix("v")
        if (tag.isNotEmpty()) tag else null
    }.getOrNull()
    ?: run {
        val patch = (gitCommitCount - 13).coerceAtLeast(1) + 1
        "$baseVersionMajor.$baseVersionMinor.$patch"
    }

android {
    namespace = "com.flow.todo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.flow.randall"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Stable release signing (required for Google Sign-In: Firebase checks the APK's SHA-1, and a
    // stable key also lets new APKs install over old ones). Provided by CI from GitHub Secrets, or
    // locally via environment variables. Falls back to the debug key when not configured.
    val releaseKeystore = System.getenv("FLOW_KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("FLOW_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FLOW_KEY_ALIAS")
                keyPassword = System.getenv("FLOW_KEY_PASSWORD") ?: System.getenv("FLOW_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// App-wide Firebase project: applied only when app/google-services.json is present, so the
// project still builds (with cloud sync disabled) before that file is added.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

tasks.register("printVersion") {
    doLast {
        println("versionCode=$appVersionCode")
        println("versionName=$appVersionName")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Haze
    implementation(libs.haze)

    // Health Connect & Shizuku
    implementation(libs.androidx.health.connect)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Firebase Auth, Cloud Firestore & Google Sign-In
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.play.services.auth)

    // AI Coach: background scheduling (interval / random triggers)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
}
