plugins {
    java
    application
}

dependencies {
    implementation(project(":agent-core"))
}

application {
    mainClass.set("com.example.agent.cli.App")
}

tasks.named<JavaExec>("run") {
    // Forward env vars and stdin to the running agent CLI.
    standardInput = System.`in`
}
