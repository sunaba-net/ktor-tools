plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "net.sunaba.ktor"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("io.ktor:ktor-client-cio:2.3.0")
    implementation("io.ktor:ktor-server-auth:2.3.0")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.0")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")


    //Google Cloud Client Libraries
    //@see https://cloud.google.com/java/docs/compile-native-images
    implementation(platform("com.google.cloud:libraries-bom:26.14.0"))
    implementation("com.google.cloud:google-cloud-storage")
    implementation("com.google.cloud:google-cloud-appengine-admin")

    implementation("com.google.apis:google-api-services-appengine:v1-rev20230424-2.0.0")
    implementation("com.google.apis:google-api-services-cloudresourcemanager:v3-rev20230507-2.0.0")

}

tasks.test {
    useJUnitPlatform()
}


kotlin {
    jvmToolchain(17)
}


val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing {
    repositories {
        mavenLocal()
    }
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["kotlin"])
            artifact(sourcesJar.get())
        }
    }
}