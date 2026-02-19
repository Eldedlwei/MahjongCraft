plugins {
    java
}

group = property("group") as String
version = property("version") as String

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.github.ssttkkl:mahjong-utils-jvm:0.7.7")
    compileOnly("com.github.retrooper:packetevents-spigot:2.10.1")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
}

sourceSets {
    named("main") {
        java.setSrcDirs(listOf("src/paper/java"))
        resources.setSrcDirs(listOf("src/paper/resources"))
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        inputs.property("version", project.version)
        filesMatching("plugin.yml") {
            expand(mapOf("version" to project.version))
        }
    }

    jar {
        archiveBaseName.set("mahjongcraft-paper")
        from("LICENSE") { rename { "${it}_${archiveBaseName.get()}" } }
    }
}
