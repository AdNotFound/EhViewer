plugins {
    id("com.android.application") version "8.5.1" apply false
    kotlin("android") version "2.0.10" apply false
    id("com.google.devtools.ksp") version "2.0.10-1.0.24" apply false
    id("com.diffplug.spotless") version "6.25.0" apply false
}

tasks.register("Delete", Delete::class) {
    delete(rootProject)
}

buildscript {
    dependencies {
        classpath("com.android.tools:r8:8.3.37")
    }
}
