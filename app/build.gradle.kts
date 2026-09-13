plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val previewKey = file(System.getenv("DJI_DEBUG_KEYSTORE")
    ?: "${System.getProperty("user.home")}/.android/debug.keystore").canonicalFile
require(!previewKey.toPath().startsWith(rootDir.canonicalFile.toPath())) {
    "Signing keys must be stored outside this repository"
}
android {
    namespace = "dev.djiremote"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig {
        applicationId = "dev.djiremote"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "dev.djiremote.UiSmokeInstrumentation"
        versionCode = 2
        versionName = "0.2.0-preview"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    // Android's debug signing key is generated in the external Android user home, never here.
    signingConfigs.getByName("debug") {
        storeFile = previewKey
    }
    lint { abortOnError = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
