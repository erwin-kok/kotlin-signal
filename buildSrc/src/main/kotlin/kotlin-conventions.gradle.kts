import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("java-conventions")
    id("com.adarshr.test-logger")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlinx.kover")
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    config.setFrom("${project.rootDir}/detekt-config.yml")
}

testlogger {
    theme = ThemeType.MOCHA
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}
