plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}


android {
    namespace = "com.aistudio.audiorouter.rkxw"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aistudio.audiorouter.rkxw"
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "1.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
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
    implementation(libs.kotlinx.coroutines.android)
}

val copyApkToRelease = tasks.register("copyApkToRelease") {
    doLast {
        val apkDir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        val releaseDir = rootProject.layout.projectDirectory.dir("release").asFile
        releaseDir.mkdirs()
        val debugApk = File(apkDir, "app-debug.apk")
        if (debugApk.exists()) {
            debugApk.copyTo(File(releaseDir, "app-debug.apk"), overwrite = true)
            debugApk.copyTo(File(releaseDir, "SonoRoute-v1.9.0-debug.apk"), overwrite = true)
            println("Successfully copied APK to release folder: SonoRoute-v1.9.0-debug.apk and app-debug.apk")
        }
    }
}

afterEvaluate {
    tasks.findByName("assembleDebug")?.finalizedBy(copyApkToRelease)
}

