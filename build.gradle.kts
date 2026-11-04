plugins {
    // AGP 9 compiles Kotlin itself, so the org.jetbrains.kotlin.android plugin is gone.
    id("com.android.application") version "9.5.0" apply false
    // :game-core is a plain JVM module and still needs the Kotlin JVM plugin.
    id("org.jetbrains.kotlin.jvm") version "2.4.21" apply false
}
