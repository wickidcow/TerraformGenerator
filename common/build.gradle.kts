group = "org.terraform"

dependencies {
    // Keep the shared generator on its proven API baseline, but compile against
    // Paper rather than the full Spigot server. The shipped NMS adapter is 26.2-only.
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:20.1.0")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("commons-lang:commons-lang:2.6")
    compileOnly("com.github.AvarionMC:yaml:1.1.7")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
