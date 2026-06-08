package com.example.agent.exec;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Extracts failing test identifiers from JUnit XML reports
 * ({@code build/test-results/test/TEST-*.xml} for Gradle,
 * {@code target/surefire-reports/TEST-*.xml} for Maven).
 *
 * <p>Returns identifiers as {@code classname#methodname} so the LLM can quote
 * them back when asking follow-up questions or planning edits.
 */
final class JUnitXmlParser {

    private JUnitXmlParser() {}

    static List<String> findFailingTests(Path projectRoot, List<String> reportSubdirs) {
        List<String> failing = new ArrayList<>();
        for (String subdir : reportSubdirs) {
            collectFailingFromTree(projectRoot.resolve(subdir), failing);
        }
        // Also walk the whole project so multi-module layouts work.
        collectFailingByWalking(projectRoot, reportSubdirs, failing);
        return failing;
    }

    private static void collectFailingFromTree(Path dir, List<String> sink) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().startsWith("TEST-"))
                    .filter(p -> p.getFileName().toString().endsWith(".xml"))
                    .forEach(p -> parseInto(p, sink));
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static void collectFailingByWalking(Path root, List<String> subdirs, List<String> sink) {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> stream = Files.walk(root, 8)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        if (!name.startsWith("TEST-") || !name.endsWith(".xml")) return false;
                        String s = p.toString().replace('\\', '/');
                        return subdirs.stream().anyMatch(sub -> s.contains("/" + sub.replace('\\', '/') + "/"));
                    })
                    .forEach(p -> parseInto(p, sink));
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static void parseInto(Path xmlFile, List<String> sink) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile.toFile());

            NodeList testcases = doc.getElementsByTagName("testcase");
            for (int i = 0; i < testcases.getLength(); i++) {
                Element tc = (Element) testcases.item(i);
                boolean failed = tc.getElementsByTagName("failure").getLength() > 0
                        || tc.getElementsByTagName("error").getLength() > 0;
                if (failed) {
                    String classname = tc.getAttribute("classname");
                    String name = tc.getAttribute("name");
                    String id = (classname.isEmpty() ? "<unknown>" : classname) + "#" + name;
                    if (!sink.contains(id)) {
                        sink.add(id);
                    }
                }
            }
        } catch (Exception ignored) {
            // skip unparseable reports — don't fail the whole runner
        }
    }
}
