group = "org.terraform"

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("org.jetbrains:annotations:20.1.0")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("commons-lang:commons-lang:2.6")
    compileOnly("com.github.AvarionMC:yaml:1.1.7")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
