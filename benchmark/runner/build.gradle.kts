plugins {
    java
}

val junitVersion: String by project
val assertjVersion: String by project

dependencies {
    implementation(project(":agent-core"))

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
}

// Runs the agent over the seeded-bug benchmark and prints a metrics summary.
// `benchmark.root` lets the runner locate benchmark/projects regardless of the
// process working directory.
tasks.register<JavaExec>("runEval") {
    group = "verification"
    description = "Run the agent over the seeded-bug benchmark and report resolution metrics."
    mainClass.set("com.example.benchmark.EvalRunner")
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("benchmark.root", rootProject.projectDir.absolutePath)
    standardInput = System.`in`
}

// Runs the whole benchmark once per ablation cell (RAG x self-critique) and
// prints a comparison matrix + per-case grid. Same `benchmark.root` wiring.
tasks.register<JavaExec>("runAblation") {
    group = "verification"
    description = "Run the ablation matrix (RAG x self-critique) over the benchmark and report marginal contributions."
    mainClass.set("com.example.benchmark.AblationRunner")
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("benchmark.root", rootProject.projectDir.absolutePath)
    standardInput = System.`in`
}
