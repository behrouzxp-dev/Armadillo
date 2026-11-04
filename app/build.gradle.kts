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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.armadillo.game"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
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
            isMinifyEnabled = false
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
        unitTests.isReturnDefaultValues = true
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

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn("verifyReleaseSigning")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
