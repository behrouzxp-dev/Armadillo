plugins {
    // Kotlin support is built into AGP 9; there is no kotlin-android plugin any more.
    id("com.android.application")
    // The Compose compiler is versioned with Kotlin, not with the Compose libraries.
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystorePath = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
val releaseSigningRequested = listOf(releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .any { !it.isNullOrBlank() }
val releaseSigningConfigured = listOf(releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }
check(!releaseSigningRequested || releaseSigningConfigured) {
    "Release signing is incomplete. Set RELEASE_KEYSTORE_PATH, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, and RELEASE_KEY_PASSWORD."
}

android {
    namespace = "com.armadillo.game"
    // Compose 1.12 (BOM 2026.09.00) requires compileSdk 37; targetSdk stays at 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.armadillo.game"
        minSdk = 23
        targetSdk = 36
        // The first com.armadillo.game release used versionCode 1.
        // Historical 2.2.0/versionCode 6 builds used the earlier com.dour.game identity.
        versionCode = 4
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        debug {
            // Android Gradle signs this variant with the generated debug key.
        }
        release {
            // Shrink and obfuscate: the app uses platform views only, so the default rules suffice.
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        // Fail loudly instead of silently returning defaults when a test touches a platform API.
        unitTests.isReturnDefaultValues = false
    }
}

tasks.register("verifyReleaseSigning") {
    doLast {
        check(releaseSigningConfigured) {
            "Release signing variables are required: RELEASE_KEYSTORE_PATH, RELEASE_KEY_ALIAS, RELEASE_STORE_PASSWORD, RELEASE_KEY_PASSWORD."
        }
        check(file(releaseKeystorePath!!).isFile) {
            "The release keystore file specified by RELEASE_KEYSTORE_PATH was not found."
        }
    }
}

// Signing is verified only when signing was requested, so an unsigned release build can
// still be produced for CI verification of R8, resource shrinking and lint.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    if (releaseSigningRequested) dependsOn("verifyReleaseSigning")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Engine, seat plan and checkpoint codec live in a pure Kotlin module with its own JVM tests.
    implementation(project(":game-core"))

    // Compose BOM keeps every androidx.compose artifact on one tested version.
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    // Material design-system icons (Settings, Info) used by the home controls.
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // AGP does not inherit the implementation() BOM constraint into the
    // androidTest runtime classpath used by lint, so declare it explicitly.
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
