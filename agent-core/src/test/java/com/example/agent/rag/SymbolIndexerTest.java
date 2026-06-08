package com.example.agent.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolIndexerTest {

    @Test
    void indexesClassesMethodsAndFields(@TempDir Path root) throws IOException {
        Path src = root.resolve("src/main/java/com/example");
        Files.createDirectories(src);
        Files.writeString(src.resolve("OrderCalculator.java"), """
                package com.example;

                public class OrderCalculator {
                    private double rate = 0.1;

                    public double applyDiscount(double price) {
                        return price * (1 - rate);
                    }
                }
                """);

        SymbolIndex index = new SymbolIndexer().index(root);

        assertThat(index.size()).isGreaterThanOrEqualTo(3);
        assertThat(index.findByFqn("com.example.OrderCalculator")).isPresent()
                .get()
                .extracting(Symbol::kind)
                .isEqualTo(Symbol.Kind.CLASS);
        assertThat(index.findByFqn("com.example.OrderCalculator.applyDiscount")).isPresent()
                .get()
                .extracting(Symbol::kind)
                .isEqualTo(Symbol.Kind.METHOD);
        assertThat(index.findByFqn("com.example.OrderCalculator.rate")).isPresent()
                .get()
                .extracting(Symbol::kind)
                .isEqualTo(Symbol.Kind.FIELD);
    }

    @Test
    void skipsBuildDirectory(@TempDir Path root) throws IOException {
        Path src = root.resolve("src/main/java");
        Path build = root.resolve("build/generated/com/example");
        Files.createDirectories(src);
        Files.createDirectories(build);
        Files.writeString(src.resolve("Live.java"), "package x; public class Live {}");
        Files.writeString(build.resolve("Generated.java"), "package y; public class Generated {}");

        SymbolIndex index = new SymbolIndexer().index(root);

        assertThat(index.findByFqn("x.Live")).isPresent();
        assertThat(index.findByFqn("y.Generated")).isEmpty();
    }

    @Test
    void tolerantOfSyntaxErrors(@TempDir Path root) throws IOException {
        Path src = root.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Good.java"), "package g; public class Good {}");
        Files.writeString(src.resolve("Broken.java"), "package b; pub @@@ broken;");

        SymbolIndex index = new SymbolIndexer().index(root);

        assertThat(index.findByFqn("g.Good")).isPresent();
        // Broken file silently skipped, no exception.
    }

    @Test
    void storesRelativePaths(@TempDir Path root) throws IOException {
        Path src = root.resolve("src/main/java/p");
        Files.createDirectories(src);
        Files.writeString(src.resolve("A.java"), "package p; public class A {}");

        SymbolIndex index = new SymbolIndexer().index(root);

        Symbol a = index.findByFqn("p.A").orElseThrow();
        assertThat(a.relativePath().toString().replace('\\', '/'))
                .isEqualTo("src/main/java/p/A.java");
        assertThat(a.relativePath().isAbsolute()).isFalse();
    }

    @Test
    void capturesMultipleVariablesPerFieldDeclaration(@TempDir Path root) throws IOException {
        Path src = root.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("V.java"), """
                package v;
                public class V {
                    int a, b, c;
                }
                """);

        SymbolIndex index = new SymbolIndexer().index(root);

        assertThat(index.findByFqn("v.V.a")).isPresent();
        assertThat(index.findByFqn("v.V.b")).isPresent();
        assertThat(index.findByFqn("v.V.c")).isPresent();
    }

    @Test
    void indexesMultipleFiles(@TempDir Path root) throws IOException {
        Path pkgA = root.resolve("src/a");
        Path pkgB = root.resolve("src/b");
        Files.createDirectories(pkgA);
        Files.createDirectories(pkgB);
        Files.writeString(pkgA.resolve("Alpha.java"), "package a; public class Alpha {}");
        Files.writeString(pkgB.resolve("Bravo.java"), "package b; public class Bravo {}");

        SymbolIndex index = new SymbolIndexer().index(root);

        List<String> fqns = index.all().stream().map(Symbol::fqn).toList();
        assertThat(fqns).contains("a.Alpha", "b.Bravo");
    }
}
