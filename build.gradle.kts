plugins {
    id("com.android.application") version "8.1.2" apply false
    kotlin("android") version "1.9.21" apply false
    id("com.google.devtools.ksp") version "1.9.21-1.0.15" apply false
    id("com.diffplug.spotless") version "6.22.0" apply false
}

tasks.register("Delete", Delete::class) {
    delete(rootProject.buildDir)
}

buildscript {
    dependencies {
        classpath("com.android.tools:r8:8.2.9-dev")
    }
}
