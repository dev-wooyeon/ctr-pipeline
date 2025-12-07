import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.example"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

val flinkVersion = "1.18.1"
val jacksonVersion = "2.15.2"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.apache.flink:flink-java:$flinkVersion")
    implementation("org.apache.flink:flink-streaming-java:$flinkVersion")
    implementation("org.apache.flink:flink-clients:$flinkVersion")

    implementation("org.apache.flink:flink-connector-kafka:3.0.2-1.18")
    implementation("org.apache.flink:flink-connector-jdbc:3.1.2-1.18")

    implementation("com.clickhouse:clickhouse-jdbc:0.4.6")

    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")

    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    implementation("org.glassfish:jakarta.el:4.0.2")

    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")
    implementation("org.apache.flink:flink-metrics-prometheus:$flinkVersion")

    testImplementation("org.apache.flink:flink-test-utils:$flinkVersion")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    mergeServiceFiles()

    manifest {
        attributes["Main-Class"] = "com.example.ctr.CtrApplication"
    }

    dependencies {
        exclude(dependency("org.apache.flink:flink-java:.*"))
        exclude(dependency("org.apache.flink:flink-streaming-java:.*"))
        exclude(dependency("org.apache.flink:flink-clients:.*"))
        exclude(dependency("org.apache.flink:flink-core:.*"))
        exclude(dependency("org.apache.flink:flink-runtime:.*"))
        exclude(dependency("org.apache.flink:flink-optimizer:.*"))
        exclude(dependency("org.apache.flink:flink-annotations:.*"))
        exclude(dependency("org.apache.flink:flink-queryable-state-client-java:.*"))
        exclude(dependency("org.apache.flink:flink-shaded-.*:.*"))
        exclude(dependency("org.slf4j:.*:.*"))
        exclude(dependency("log4j:.*:.*"))
        exclude(dependency("ch.qos.logback:.*:.*"))
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.example.ctr.CtrApplication"
    }
}
