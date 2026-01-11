plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "dev.aurivena.lms"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

javafx {
    version = "20.0.2"
    modules("javafx.controls")
}

application {
    mainClass.set("app.ui.SimpleUi")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}