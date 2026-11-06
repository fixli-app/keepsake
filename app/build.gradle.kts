plugins {
    id("com.android.application")
}

android {
    namespace = "com.keepsake.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.keepsake.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    val signingVariables = listOf(
        "KEEPSAKE_STORE_FILE",
        "KEEPSAKE_STORE_PASSWORD",
        "KEEPSAKE_KEY_ALIAS",
        "KEEPSAKE_KEY_PASSWORD"
    )
    val signingValues = signingVariables.associateWith { providers.environmentVariable(it).orNull }
    val configuredSigningValues = signingValues.values.count { it != null }
    if (configuredSigningValues != 0 && configuredSigningValues != signingVariables.size) {
        throw GradleException("Set all KEEPSAKE signing environment variables or leave all of them unset.")
    }

    if (configuredSigningValues == signingVariables.size) {
        signingConfigs {
            create("release") {
                storeFile = file(signingValues.getValue("KEEPSAKE_STORE_FILE")!!)
                storePassword = signingValues.getValue("KEEPSAKE_STORE_PASSWORD")
                keyAlias = signingValues.getValue("KEEPSAKE_KEY_ALIAS")
                keyPassword = signingValues.getValue("KEEPSAKE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (configuredSigningValues == signingVariables.size) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}
