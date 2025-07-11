plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.buildscript.detekt)
    implementation(libs.buildscript.kotlin)
    implementation(libs.buildscript.kotlin.serialization)
    implementation(libs.buildscript.ktlint)
    implementation(libs.buildscript.kover)
    implementation(libs.buildscript.protobuf)
    implementation(libs.buildscript.testlogger)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}
