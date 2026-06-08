package com.example.agent.exec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class MavenTestRunner extends ProcessTestRunner {

    public MavenTestRunner(Path projectRoot) {
        super(projectRoot);
    }

    public MavenTestRunner(Path projectRoot, Duration timeout) {
        super(projectRoot, timeout);
    }

    @Override
    protected List<String> testCommand() {
        Path wrapper = projectRoot.resolve(isWindows() ? "mvnw.cmd" : "mvnw");
        // ProcessBuilder on Windows does not apply PATHEXT, so the bare name
        // "mvn" fails with CreateProcess error=2. Use the .cmd explicitly.
        String binary = Files.isRegularFile(wrapper)
                ? wrapper.toAbsolutePath().toString()
                : (isWindows() ? "mvn.cmd" : "mvn");
        return List.of(binary, "test", "-B", "-q");
    }

    @Override
    protected List<String> reportSubdirs() {
        return List.of("target/surefire-reports");
    }
}
