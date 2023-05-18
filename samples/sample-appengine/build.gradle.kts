val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm")
    id("io.ktor.plugin")
    id("com.google.cloud.tools.appengine")
}

group = "net.sunaba"
version = "0.0.1"
application {
    mainClass.set("net.sunaba.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-cio-jvm:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

kotlin {
    jvmToolchain(17)
}

appengine {
    deploy {
        projectId = ""
        version = "GCLOUD_CONFIG"
        stopPreviousVersion = true
        promote = true
    }
}


tasks.register("hoge") {
    doLast {
        println(ktor.fatJar.archiveFileName.get())
    }
}
