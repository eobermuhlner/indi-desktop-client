import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
    application
    id("org.openjfx.javafxplugin") version "0.0.10"
    `maven-publish`
}

group = "ch.obermuhlner.indi.client"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("ch.obermuhlner.indi.client.IndiClientApplication")
}

javafx {
    modules("javafx.controls", "javafx.web")
}

repositories {
    mavenCentral()
    maven { setUrl("https://jitpack.io") }
}

dependencies {
    implementation("com.github.INDIForJava:INDIForJava-client:2.1.1")
    //implementation("com.github.eobermuhlner:kimage:main-SNAPSHOT")
    implementation("gov.nasa.gsfc.heasarc:nom-tam-fits:1.15.2")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("ch.qos.logback:logback-core:1.2.3")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}