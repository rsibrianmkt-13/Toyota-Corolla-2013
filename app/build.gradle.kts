import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firma de release: crea keystore.properties en la raíz (ver README).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.bryan.corolladash"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bryan.corolladash"
        minSdk = 26
        targetSdk = 35
        // En GitHub Actions: 100 + número de ejecución (siempre sube → la app detecta la actualización).
        versionCode = (project.findProperty("ciVersionCode") as String?)?.toInt() ?: 14
        versionName = "1.2.0"
    }

    signingConfigs {
        // Firma de debug fija: si existe keystore/debug.keystore (copia de ~/.android/debug.keystore de tu Mac),
        // Android Studio y GitHub firman igual y el APK se instala encima sin desinstalar.
        getByName("debug") {
            val ks = rootProject.file("keystore/debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    // Instala los perfiles de arranque de Compose al instalar el APK fuera de Play: menos tirones y arranque más rápido.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // MediaControllerCompat: leer/cambiar aleatorio y repetir de la sesión de Música BT.
    implementation("androidx.media:media:1.7.0")
    // Mapa del Inicio (MapLibre + estilo oscuro de OpenFreeMap, sin clave de API).
    implementation("org.maplibre.gl:android-sdk:11.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
