import com.google.cloud.tools.appengine.operations.CloudSdk
import com.google.cloud.tools.managedcloudsdk.ManagedCloudSdk

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
    val googleOAuthClientId = gcloud("secrets versions access latest --secret=sample-appengine-google-auth-clientId".split(" "))?:""
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment", "-Dsample.clientId=" + googleOAuthClientId )
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

    implementation(project(":ktor-appengine-util"))
    implementation("io.ktor:ktor-server-auth:2.3.0")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.0")

    implementation(platform("com.google.cloud:libraries-bom:26.14.0"))
    implementation("com.google.cloud:google-cloud-tasks")
}

kotlin {
    jvmToolchain(17)
}

appengine {
    deploy {
        projectId = "GCLOUD_CONFIG"
        version = "GCLOUD_CONFIG"
        stopPreviousVersion = false
        promote = false
    }
}

tasks.appengineStage.get().dependsOn.add(tasks.buildFatJar.name)

tasks.buildFatJar.get().doLast {
    println(file("${project.buildDir}/libs/${ktor.fatJar.archiveFileName.get()}"))
    appengine.stage.setArtifact(file("${project.buildDir}/libs/${ktor.fatJar.archiveFileName.get()}"))
}

fun gcloud(command:List<String>):String? {
    val sdk = CloudSdk.Builder().sdkPath(ManagedCloudSdk.newManagedSdk().sdkHome).build()
    val command = arrayOf(sdk.gCloudPath.toAbsolutePath().toString(), *command.toTypedArray())
    return ProcessBuilder().command(*command).let {
        it.start().let {
            if (it.waitFor()==0) {
                it.inputReader().readLine()
            } else null
        }
    }
}
