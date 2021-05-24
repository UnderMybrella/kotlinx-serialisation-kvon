plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "dev.brella"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
//                implementation("org.jetbrains.kotlinx:kotlinx-serialization:1.2.1")
    api(project(":ktor:http"))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")
    implementation(project(":kotlinx-serialisation"))

    implementation("io.ktor:ktor-server-core:1.5.4")
    implementation("io.ktor:ktor-serialization:1.5.4")
}