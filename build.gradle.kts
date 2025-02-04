import org.gradle.api.internal.classpath.ManifestUtil

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.0.0"
}

group = "com.gameryt"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.bytedeco:javacv-platform:1.5.11")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        from("src/main/resources/META-INF/MANIFEST.MF")
    }
}

tasks.shadowJar {
    manifest {
        attributes(
            "Main-Class" to "com.gameryt.MainWindow"
        )
    }
}

tasks.processResources {
    exclude("music/**")
}
