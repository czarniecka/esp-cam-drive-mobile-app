plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Client ID wpisujesz tylko w Config.kt. Schemat przekierowania (odwrócony Client ID)
// jest stąd wstawiany do AndroidManifest.xml.
val clientId = Regex("googleClientID\\s*=\\s*\"([^\"]+)\"")
    .find(file("src/main/java/com/ola/espcamdrive/Config.kt").readText())!!.groupValues[1]

android {
    namespace = "com.ola.espcamdrive"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ola.espcamdrive"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["redirectScheme"] = clientId.split(".").reversed().joinToString(".")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Podpis kluczem debug, żeby `assembleRelease` dawał APK gotowy do instalacji.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
