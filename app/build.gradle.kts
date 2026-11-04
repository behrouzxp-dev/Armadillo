plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.armadillo.game"
        minSdk = 23
        targetSdk = 36
        // The com.armadillo.game identity has never been published, so versioning starts at 1.
        // Historical 2.2.0/versionCode 6 builds used the earlier com.dour.game identity.
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Real org.json implementation so the checkpoint codec can be tested on the JVM.
    testImplementation("org.json:json:20260814")
}
