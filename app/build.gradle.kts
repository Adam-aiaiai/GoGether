plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val backendMode = providers.gradleProperty("backend").orElse("demo").get()
require(backendMode in setOf("demo", "emulator", "firebase")) { "Use -Pbackend=demo, emulator or firebase." }
val googleConfigFile = file("google-services.json")
@Suppress("UNCHECKED_CAST")
val googleConfig = if (googleConfigFile.exists()) groovy.json.JsonSlurper().parse(googleConfigFile) as Map<String, Any?> else emptyMap()
val googleProject = googleConfig["project_info"] as? Map<*, *> ?: emptyMap<Any, Any>()
val googleClient = (googleConfig["client"] as? List<*>)?.mapNotNull { it as? Map<*, *> }?.firstOrNull {
    ((it["client_info"] as? Map<*, *>)?.get("android_client_info") as? Map<*, *>)?.get("package_name") == "com.gogether.app"
} ?: emptyMap<Any, Any>()
fun config(name: String, fallback: String) = providers.gradleProperty(name).orElse(fallback).get()
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val firebaseProject = config("firebaseProjectId", if (backendMode == "firebase") googleProject["project_id"] as? String ?: "" else "demo-gogether")
val firebaseAppId = config("firebaseAppId", if (backendMode == "firebase") (googleClient["client_info"] as? Map<*, *>)?.get("mobilesdk_app_id") as? String ?: "" else "1:1234567890:android:1234567890abcdef")
val firebaseApiKey = config("firebaseApiKey", if (backendMode == "firebase") ((googleClient["api_key"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("current_key") as? String ?: "" else "AIza00000000000000000000000000000000000")
val firebaseBucket = config("firebaseStorageBucket", if (backendMode == "firebase") googleProject["storage_bucket"] as? String ?: "" else "demo-gogether.appspot.com")
if (backendMode == "firebase") require(listOf(firebaseProject, firebaseAppId, firebaseApiKey, firebaseBucket).all { it.isNotBlank() }) {
    "Firebase mode requires app/google-services.json for com.gogether.app or explicit firebaseProjectId/firebaseAppId/firebaseApiKey/firebaseStorageBucket properties."
}

android {
    namespace = "com.gogether.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.gogether.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.2.0-$backendMode"
        buildConfigField("String", "BACKEND_MODE", quoted(backendMode))
        buildConfigField("String", "FIREBASE_PROJECT_ID", quoted(firebaseProject))
        buildConfigField("String", "FIREBASE_APP_ID", quoted(firebaseAppId))
        buildConfigField("String", "FIREBASE_API_KEY", quoted(firebaseApiKey))
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", quoted(firebaseBucket))
        buildConfigField("String", "FIREBASE_REGION", quoted(config("firebaseRegion", "us-central1")))
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", quoted(config("firebaseEmulatorHost", "10.0.2.2")))
        manifestPlaceholders["allowBackendCleartext"] = (backendMode == "emulator").toString()
        manifestPlaceholders["backendNetworkPermission"] = if (backendMode == "emulator") "android.permission.ACCESS_LOCAL_NETWORK" else "android.permission.INTERNET"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-storage")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.zxing.embedded)
    implementation(libs.zxing.core)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext)
    // 3.7 fixes InputManager reflection removed on the installed Android 17 emulator.
    androidTestImplementation(libs.espresso.core)
}
