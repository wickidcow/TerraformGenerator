plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
}

subprojects {
    apply<JavaPlugin>()

    group = "org.terraform"
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io")
    }
}
