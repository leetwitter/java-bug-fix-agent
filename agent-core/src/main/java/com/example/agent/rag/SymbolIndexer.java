package com.example.agent.rag;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Walks every {@code .java} file under a project root and produces a
 * {@link SymbolIndex}. Build/target directories are skipped.
 *
 * <p>Parsing failures (syntax errors etc.) are tolerated: the broken file is
 * dropped from the index but indexing of the rest of the tree continues.
 */
public final class SymbolIndexer {

    private static final int MAX_SNIPPET_CHARS = 4_000;

    public SymbolIndex index(Path projectRoot) {
        Path root = projectRoot.toAbsolutePath().normalize();
        SymbolIndex index = new SymbolIndex();
        JavaParser parser = new JavaParser();

        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !isUnderIgnoredDir(root, p))
                    .forEach(p -> indexFile(parser, root, p, index));
        } catch (IOException e) {
            // best-effort: return whatever we managed to index
        }
        return index;
    }

    private static boolean isUnderIgnoredDir(Path root, Path file) {
        String rel = root.relativize(file).toString().replace('\\', '/');
        return rel.startsWith("build/")
                || rel.contains("/build/")
                || rel.startsWith("target/")
                || rel.contains("/target/")
                || rel.startsWith(".gradle/")
                || rel.contains("/.gradle/");
    }

    private void indexFile(JavaParser parser, Path root, Path file, SymbolIndex index) {
        ParseResult<CompilationUnit> result;
        try {
            result = parser.parse(file);
        } catch (IOException e) {
            return;
        }
        if (!result.isSuccessful() || result.getResult().isEmpty()) return;

        CompilationUnit cu = result.getResult().get();
        Path relFile = root.relativize(file);
        String pkg = cu.getPackageDeclaration()
                .map(NodeWithName::getNameAsString)
                .orElse("");

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(decl ->
                index.add(buildClassSymbol(pkg, decl, relFile)));

        cu.findAll(MethodDeclaration.class).forEach(method ->
                index.add(buildMethodSymbol(pkg, method, relFile)));

        cu.findAll(FieldDeclaration.class).forEach(field -> {
            for (VariableDeclarator var : field.getVariables()) {
                index.add(buildFieldSymbol(pkg, field, var, relFile));
            }
        });
    }

    private static Symbol buildClassSymbol(String pkg, ClassOrInterfaceDeclaration decl, Path relFile) {
        String simple = decl.getNameAsString();
        String fqn = pkg.isEmpty() ? simple : pkg + "." + simple;
        return new Symbol(
                Symbol.Kind.CLASS,
                simple,
                fqn,
                (decl.isInterface() ? "interface " : "class ") + simple,
                relFile,
                lineOrZero(decl.getBegin().map(p -> p.line)),
                lineOrZero(decl.getEnd().map(p -> p.line)),
                truncate(decl.toString()));
    }

    private static Symbol buildMethodSymbol(String pkg, MethodDeclaration method, Path relFile) {
        String simple = method.getNameAsString();
        String containingClass = method.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(NodeWithSimpleName::getNameAsString)
                .orElse("");
        String fqnPrefix = containingClass.isEmpty()
                ? (pkg.isEmpty() ? "" : pkg + ".")
                : (pkg.isEmpty() ? containingClass + "." : pkg + "." + containingClass + ".");
        String fqn = fqnPrefix + simple;
        String signature = method.getSignature().toString() + " : " + method.getTypeAsString();
        return new Symbol(
                Symbol.Kind.METHOD,
                simple,
                fqn,
                signature,
                relFile,
                lineOrZero(method.getBegin().map(p -> p.line)),
                lineOrZero(method.getEnd().map(p -> p.line)),
                truncate(method.toString()));
    }

    private static Symbol buildFieldSymbol(String pkg,
                                           FieldDeclaration field,
                                           VariableDeclarator var,
                                           Path relFile) {
        String simple = var.getNameAsString();
        String containingClass = field.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(NodeWithSimpleName::getNameAsString)
                .orElse("");
        String fqnPrefix = containingClass.isEmpty()
                ? (pkg.isEmpty() ? "" : pkg + ".")
                : (pkg.isEmpty() ? containingClass + "." : pkg + "." + containingClass + ".");
        String fqn = fqnPrefix + simple;
        String signature = field.getElementType() + " " + simple;
        return new Symbol(
                Symbol.Kind.FIELD,
                simple,
                fqn,
                signature,
                relFile,
                lineOrZero(field.getBegin().map(p -> p.line)),
                lineOrZero(field.getEnd().map(p -> p.line)),
                truncate(field.toString()));
    }

    private static int lineOrZero(java.util.Optional<Integer> maybe) {
        return maybe.orElse(0);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_SNIPPET_CHARS ? s : s.substring(0, MAX_SNIPPET_CHARS) + "\n…[truncated]";
    }
}
