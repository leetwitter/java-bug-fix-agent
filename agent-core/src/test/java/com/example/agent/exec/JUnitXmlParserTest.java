package com.example.agent.exec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JUnitXmlParserTest {

    @Test
    void extractsFailingTestsFromGradleReport(@TempDir Path root) throws IOException {
        Path reportDir = root.resolve("build/test-results/test");
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("TEST-com.example.FooTest.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="com.example.FooTest" tests="3" failures="1" errors="1">
                  <testcase classname="com.example.FooTest" name="passes"/>
                  <testcase classname="com.example.FooTest" name="fails">
                    <failure message="boom" type="AssertionError">stack here</failure>
                  </testcase>
                  <testcase classname="com.example.FooTest" name="errors">
                    <error message="npe">stack</error>
                  </testcase>
                </testsuite>
                """);

        List<String> failing = JUnitXmlParser.findFailingTests(root, List.of("build/test-results/test"));

        assertThat(failing).containsExactlyInAnyOrder(
                "com.example.FooTest#fails",
                "com.example.FooTest#errors");
    }

    @Test
    void returnsEmptyWhenAllTestsPass(@TempDir Path root) throws IOException {
        Path reportDir = root.resolve("build/test-results/test");
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("TEST-com.example.GoodTest.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="com.example.GoodTest" tests="1" failures="0" errors="0">
                  <testcase classname="com.example.GoodTest" name="ok"/>
                </testsuite>
                """);

        List<String> failing = JUnitXmlParser.findFailingTests(root, List.of("build/test-results/test"));

        assertThat(failing).isEmpty();
    }

    @Test
    void findsReportsInMultiModuleLayout(@TempDir Path root) throws IOException {
        Path moduleA = root.resolve("moduleA/build/test-results/test");
        Path moduleB = root.resolve("moduleB/build/test-results/test");
        Files.createDirectories(moduleA);
        Files.createDirectories(moduleB);
        Files.writeString(moduleA.resolve("TEST-A.xml"), failingXml("a.AT", "tA"));
        Files.writeString(moduleB.resolve("TEST-B.xml"), failingXml("b.BT", "tB"));

        List<String> failing = JUnitXmlParser.findFailingTests(root, List.of("build/test-results/test"));

        assertThat(failing).containsExactlyInAnyOrder("a.AT#tA", "b.BT#tB");
    }

    @Test
    void ignoresMalformedReports(@TempDir Path root) throws IOException {
        Path reportDir = root.resolve("build/test-results/test");
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("TEST-broken.xml"), "<<<not xml>>>");
        Files.writeString(reportDir.resolve("TEST-good.xml"), failingXml("X", "y"));

        List<String> failing = JUnitXmlParser.findFailingTests(root, List.of("build/test-results/test"));

        assertThat(failing).containsExactly("X#y");
    }

    private static String failingXml(String classname, String name) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="%s">
                  <testcase classname="%s" name="%s">
                    <failure message="m" type="t">stack</failure>
                  </testcase>
                </testsuite>
                """.formatted(classname, classname, name);
    }
}
