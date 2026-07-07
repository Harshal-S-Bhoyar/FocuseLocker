import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStoreFile = providers.environmentVariable("FOCUSLOCK_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("FOCUSLOCK_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("FOCUSLOCK_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("FOCUSLOCK_RELEASE_KEY_PASSWORD").orNull

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

fun String.wrapQuotes(): String {
    val unquoted = this.trim('"')
    return "\"$unquoted\""
}

fun getSecret(key: String, default: String): String {
    return providers.environmentVariable(key).orNull?.wrapQuotes()
        ?: localProperties.getProperty(key)?.wrapQuotes()
        ?: project.findProperty(key)?.toString()?.wrapQuotes()
        ?: default
}

val defaultBackendUrl = "\"\""
val defaultNextDnsBaseUrl = "\"\""
val defaultFallbackDns = "\"\""

android {
    namespace = "com.focuslock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.focuslock"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        vectorDrawables {
            useSupportLibrary = true
        }

        // Backend URL and NextDNS Profile ID from environment/local.properties
        buildConfigField("String", "NEXTDNS_PROFILE_ID", getSecret("NEXTDNS_PROFILE_ID", "\"\""))
        buildConfigField("String", "NEXTDNS_BASE_URL", getSecret("NEXTDNS_BASE_URL", defaultNextDnsBaseUrl))
        buildConfigField("String", "FALLBACK_DNS_ENDPOINT", getSecret("FALLBACK_DNS_ENDPOINT", defaultFallbackDns))
        buildConfigField("String", "BACKEND_URL", getSecret("BACKEND_URL", defaultBackendUrl))
        
        // Fast-path IP configuration for DNS bootstrap
        buildConfigField("String", "BOOTSTRAP_FALLBACK_SERVERS", getSecret("BOOTSTRAP_FALLBACK_SERVERS", "\"\""))
        buildConfigField("String", "NEXTDNS_FASTPATH_IPS", getSecret("NEXTDNS_FASTPATH_IPS", "\"\""))
        buildConfigField("String", "ADGUARD_FASTPATH_IPS", getSecret("ADGUARD_FASTPATH_IPS", "\"\""))
        
        // VPN Virtual Network Configuration
        buildConfigField("String", "VIRTUAL_IPV4", getSecret("VIRTUAL_IPV4", "\"10.0.0.2\""))
        buildConfigField("String", "VIRTUAL_IPV6", getSecret("VIRTUAL_IPV6", "\"fd00:1:fd00:1:fd00:1:fd00:1\""))
    }

    signingConfigs {
        if (
            releaseStoreFile != null &&
            releaseStorePassword != null &&
            releaseKeyAlias != null &&
            releaseKeyPassword != null
        ) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (signingConfigs.names.contains("release")) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Can override release-specific backend URL via env var or property if needed
            buildConfigField("String", "BACKEND_URL", getSecret("RELEASE_BACKEND_URL", getSecret("BACKEND_URL", "\"https://focuslock-backend.railway.app\"")))
        }
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "BACKEND_URL", getSecret("DEBUG_BACKEND_URL", getSecret("BACKEND_URL", defaultBackendUrl)))
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



    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // material-icons-extended removed to save ~10MB APK size
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.53")
    ksp("com.google.dagger:hilt-android-compiler:2.53")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")

    // Retrofit + OkHttp + Kotlin Serialization
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // HTML Parsing
    implementation("org.jsoup:jsoup:1.17.2")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("androidx.test:runner:1.6.2")
    testImplementation("androidx.room:room-testing:2.7.0")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    androidTestImplementation("io.mockk:mockk-android:1.13.9")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
