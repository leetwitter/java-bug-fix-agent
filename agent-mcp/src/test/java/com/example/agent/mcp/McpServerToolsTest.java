package com.example.agent.mcp;

import com.example.agent.tools.FileTools;
import com.example.agent.tools.SearchCodeTool;
import com.example.agent.tools.TestRunnerTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerToolsTest {

    @Test
    void buildsFileAndSearchToolsAlways(@TempDir Path root) {
        List<Object> tools = McpServerTools.forProject(root);
        assertThat(tools).hasAtLeastOneElementOfType(FileTools.class);
        assertThat(tools).hasAtLeastOneElementOfType(SearchCodeTool.class);
    }

    @Test
    void includesTestRunnerWhenProjectHasABuildFile(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("build.gradle.kts"), "plugins { java }");
        List<Object> tools = McpServerTools.forProject(root);
        assertThat(tools).hasAtLeastOneElementOfType(TestRunnerTool.class);
    }

    @Test
    void omitsTestRunnerWhenNoBuildToolIsRecognized(@TempDir Path root) {
        // bare directory, no gradle/maven markers
        List<Object> tools = McpServerTools.forProject(root);
        assertThat(tools).noneMatch(t -> t instanceof TestRunnerTool);
    }
}
