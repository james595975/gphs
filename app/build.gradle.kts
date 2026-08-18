import java.util.Properties

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use(::load)
}
val cloudflareApiBaseUrl = localProperties.getProperty("CLOUDFLARE_API_BASE_URL", "").trimEnd('/')
val firebaseConfigured = file("google-services.json").exists()
val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE", "")
val releaseStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD", "")
val releaseKeyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "")
val releaseKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "kr.hs.gunpo.school"
    compileSdk = 37

    defaultConfig {
        applicationId = "kr.hs.gunpo.school"
        minSdk = 33
        targetSdk = 37
        versionCode = 16
        versionName = "0.1.15"
        buildConfigField("String", "CLOUDFLARE_API_BASE_URL", "\"${cloudflareApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField("String", "FIREBASE_AI_MODEL", "\"gemini-3.5-flash\"")
        buildConfigField("String", "FIREBASE_AI_FALLBACK_MODEL", "\"gemini-3.1-flash-lite\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (releaseStoreFile.isNotBlank()) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStoreFile.isNotBlank()) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
    implementation("org.jsoup:jsoup:1.22.2")

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
