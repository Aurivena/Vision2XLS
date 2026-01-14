plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.aurivena.lms"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

javafx {
    version = "20.0.2"
    modules("javafx.controls", "javafx.fxml", "javafx.graphics")
}

application {
    mainClass.set("app.view.SimpleUi")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("org.apache.poi:poi-ooxml:5.5.1")
    implementation("org.apache.logging.log4j:log4j-core:2.22.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("Vision2XLS")
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "app.view.SimpleUi"
    }
}