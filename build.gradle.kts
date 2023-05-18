plugins {
    kotlin("jvm") version "1.8.21" apply false
    id("io.ktor.plugin") version "2.3.0" apply false
    id("com.google.cloud.tools.appengine") version "2.4.4" apply false
}

group = "net.sunaba.ktor"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}
