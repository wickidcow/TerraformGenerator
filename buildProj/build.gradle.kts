plugins {
    id("com.gradleup.shadow").version("9.4.0")
}

buildscript {
    dependencies {
        classpath("org.yaml:snakeyaml:2.0")
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":implementation:v26_2"))
    implementation("com.github.AvarionMC:yaml:1.1.7")
}

tasks.shadowJar {
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }

    doFirst {
        val yamlFile = file("${rootProject.projectDir}/common/src/main/resources/plugin.yml")
        val yaml = org.yaml.snakeyaml.Yaml()
        val config = yaml.load<Map<String, Any>>(yamlFile.inputStream())

        archiveBaseName.set(config["name"].toString())
        archiveVersion.set(config["version"].toString())
        archiveClassifier.set("")
    }

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    relocate("io.papermc.lib", "org.terraform.lib")
}

tasks.register<Copy>("deploy") {
    dependsOn(tasks.named("shadowJar"))

    from(layout.buildDirectory.dir("libs"))
    include("*.jar")
    into(rootProject.projectDir)

    doNotTrackState("Disable state tracking due to file access issues")
}
