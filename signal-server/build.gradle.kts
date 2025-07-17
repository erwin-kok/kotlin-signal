import com.google.protobuf.gradle.id

plugins {
    id("kotlin-conventions")
    id("com.google.protobuf")
}

ktlint {
    filter {
        exclude { element ->
            val path = element.file.path
            path.contains("/generated/")
        }
    }
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.kotlinx.datetime)
    implementation(libs.oshai.logging)
    implementation(libs.logback.classic)
    implementation(libs.lettuce)

    implementation("io.grpc:grpc-kotlin-stub:1.4.3")
    implementation("io.grpc:grpc-protobuf:1.73.0")
    implementation("io.grpc:grpc-stub:1.73.0")
    implementation("io.grpc:grpc-netty:1.73.0")
    implementation("com.google.protobuf:protobuf-kotlin:4.31.1")
    implementation("io.github.resilience4j:resilience4j-all:2.3.0")
    implementation("io.github.resilience4j:resilience4j-kotlin:2.3.0")
    implementation("org.signal:libsignal-server:0.76.3")

    testImplementation(libs.io.mockk.mockk)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.coroutines.debug)
    testImplementation("org.signal:embedded-redis:0.9.1")

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val grpcVersion = "1.73.0" // Check for latest version
val protobufVersion = "4.31.1" // Check for latest version
val grpcKotlinVersion = "1.4.3" // Check for latest version

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.builtins {
                id("kotlin")
            }
            it.plugins {
                id("grpc")
                id("grpckt")
            }
        }
    }
}
