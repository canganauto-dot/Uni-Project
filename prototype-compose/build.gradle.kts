plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.compose") version "1.10.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
}
kotlin { jvmToolchain(25) }
val backend = tasks.register<Exec>("prepareBackend") {
    workingDir(rootDir.parentFile)
    val args = listOf("-B", "-ntp", "-DskipTests", "package", "org.apache.maven.plugins:maven-dependency-plugin:3.9.0:copy-dependencies", "-DincludeScope=runtime", "-DoutputDirectory=${rootDir.parentFile}/target/compose-libs")
    if (System.getProperty("os.name").startsWith("Windows")) commandLine(listOf("cmd", "/d", "/c", "mvnw.cmd") + args)
    else commandLine(listOf("./mvnw") + args)
    inputs.dir("../src/main"); inputs.file("../pom.xml")
    outputs.file("../target/professor-os-0.1.0-SNAPSHOT.jar"); outputs.dir("../target/compose-libs")
}
dependencies {
    implementation(compose.desktop.currentOs)
    implementation(files("../target/professor-os-0.1.0-SNAPSHOT.jar").builtBy(backend))
    runtimeOnly(fileTree("../target/compose-libs") { include("*.jar") }.builtBy(backend))
    testImplementation(kotlin("test-junit"))
    testImplementation(compose.desktop.uiTestJUnit4)
    // Verify generated files with the same POI/PDFBox versions already packaged by Maven.
    testImplementation(fileTree("../target/compose-libs") { include("*.jar") }.builtBy(backend))
}
compose.desktop { application { mainClass = "com.professoros.compose.MainKt"; jvmArgs += "--enable-native-access=ALL-UNNAMED" } }
tasks.test {
    dependsOn(backend)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("compose.test.output", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
}
tasks.withType<JavaExec>().configureEach {
    workingDir(rootDir.parentFile)
    providers.gradleProperty("previewWidth").orNull?.let { systemProperty("professoros.preview.width", it) }
    providers.gradleProperty("previewHeight").orNull?.let { systemProperty("professoros.preview.height", it) }
}
