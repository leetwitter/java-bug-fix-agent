package com.example.agent.exec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class GradleTestRunner extends ProcessTestRunner {

    public GradleTestRunner(Path projectRoot) {
        super(projectRoot);
    }

    public GradleTestRunner(Path projectRoot, Duration timeout) {
        super(projectRoot, timeout);
    }

    @Override
    protected List<String> testCommand() {
        Path wrapper = projectRoot.resolve(isWindows() ? "gradlew.bat" : "gradlew");
        // ProcessBuilder on Windows does not apply PATHEXT, so the bare name
        // "gradle" fails with CreateProcess error=2. Use the .bat explicitly.
        String binary = Files.isRegularFile(wrapper)
                ? wrapper.toAbsolutePath().toString()
                : (isWindows() ? "gradle.bat" : "gradle");
        return List.of(binary, "test", "--console=plain");
    }

    @Override
    protected List<String> reportSubdirs() {
        return List.of("build/test-results/test");
    }
}
