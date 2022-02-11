import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
    application
    id("org.openjfx.javafxplugin") version "0.0.10"
}

group = "ch.obermuhlner.indi.client"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("ch.obermuhlner.indi.client.IndiClientApplication")
}

javafx {
    modules("javafx.controls")
}

repositories {
    mavenCentral()
    maven { setUrl("https://jitpack.io") }
}

dependencies {
    implementation("com.github.INDIForJava:INDIForJava-client:2.1.1")
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