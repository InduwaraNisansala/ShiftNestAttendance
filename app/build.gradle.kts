plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.shiftnest.attendance"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.shiftnest.attendance"
        minSdk = 24
        targetSdk = 37
        versionCode = 28
        versionName = "1.3.24"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { buildConfig = true }
    flavorDimensions += "backend"
    productFlavors {
        create("production") {
            dimension = "backend"
            buildConfigField("boolean", "DEMO_BACKEND", "false")
        }
        create("demo") {
            dimension = "backend"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "DEMO_BACKEND", "true")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf(
                    "androidx.**",
                    "kotlin.**",
                    "kotlinx.**"
                )
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.android.gms:play-services-maps:19.2.0")
}

// Emulator credentials and cleartext transport are never shipped as a release app.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        if (variant.productFlavors.any { it.second == "demo" }) variant.enable = false
    }
}
