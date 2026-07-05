plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.megasurvivors.game"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.megasurvivors.game"
        minSdk = 24
        targetSdk = 34
        // Номер сборки приходит из CI: каждая сборка — новая версия,
        // видная при установке и в меню игры.
        val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = ciRun ?: 1
        versionName = "0.2." + (ciRun?.toString() ?: "dev")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
