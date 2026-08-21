group = "org.terraform"

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("org.jetbrains:annotations:20.1.0")
    compileOnly("com.github.AvarionMC:yaml:1.1.7")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
