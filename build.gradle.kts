plugins {
    kotlin("multiplatform") version "1.5.0" apply false
    kotlin("jvm") version "1.5.0" apply false
    kotlin("plugin.serialization") version "1.5.0" apply false
}

apply(plugin = "maven-publish")

group = "dev.brella"

allprojects {
    group = "dev.brella"

    repositories {
        mavenCentral()
    }
}

configure(subprojects) {
    apply(plugin = "maven-publish")

    configure<PublishingExtension> {
        repositories {
            maven(url = "${rootProject.buildDir}/repo")
        }
    }
}