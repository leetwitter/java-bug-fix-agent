plugins {
    java
    `java-library`
}

val langchain4jVersion: String by project
val langchain4jOllamaVersion: String by project
val langchain4jOpenAiVersion: String by project
val javaparserVersion: String by project
val junitVersion: String by project
val mockitoVersion: String by project
val assertjVersion: String by project

dependencies {
    api("dev.langchain4j:langchain4j:$langchain4jVersion")
    api("dev.langchain4j:langchain4j-ollama:$langchain4jOllamaVersion")
    api("dev.langchain4j:langchain4j-open-ai:$langchain4jOpenAiVersion")
    api("com.github.javaparser:javaparser-symbol-solver-core:$javaparserVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
}
