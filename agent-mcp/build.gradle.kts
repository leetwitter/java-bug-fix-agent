plugins {
    java
    application
}

val langchain4jCommunityMcpServerVersion: String by project
val junitVersion: String by project
val assertjVersion: String by project

dependencies {
    implementation(project(":agent-core"))
    implementation("dev.langchain4j:langchain4j-community-mcp-server:$langchain4jCommunityMcpServerVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
}

application {
    // stdio MCP server exposing the same tools as the in-process agent.
    mainClass.set("com.example.agent.mcp.McpServerMain")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
