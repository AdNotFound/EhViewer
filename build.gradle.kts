plugins {
    id("com.android.application") version "8.1.0-beta04" apply false
    kotlin("android") version "1.9.10" apply false
    id("com.google.devtools.ksp") version "1.9.10-1.0.13" apply false
}

allprojects {
    apply(from = "$rootDir/ktlint.gradle.kts")
}

tasks.register("Delete", Delete::class) {
    delete(rootProject.buildDir)
}

buildscript {
    dependencies {
        classpath("com.android.tools:r8:8.2.9-dev")
    }
}
