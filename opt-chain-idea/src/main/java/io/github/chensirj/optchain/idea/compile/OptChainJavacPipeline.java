package io.github.chensirj.optchain.idea.compile;

import io.github.chensirj.optchain.plugin.OptChainLayout;
import io.github.chensirj.optchain.plugin.OptChainSourceTransformer;
import io.github.chensirj.optchain.plugin.OptChainWeaver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Rewrites {@code ?.} the same way Maven preprocess does, compiles with the project
 * {@code javac}, then weaves {@code OptChainOps.opt} markers out of the class files.
 * <p>
 * No IntelliJ types. The JPS builder and the unit test both call this.
 * </p>
 */
public final class OptChainJavacPipeline {

    public record Diagnostic(String sourcePath, int line, String message) {
    }

    public record Result(boolean success, int rewrittenFiles, int wovenClasses, List<Diagnostic> errors) {
        public String summary() {
            if (errors.isEmpty()) {
                return "rewrote " + rewrittenFiles + " file(s), wove " + wovenClasses + " class file(s)";
            }
            StringBuilder sb = new StringBuilder();
            for (Diagnostic error : errors) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(error.sourcePath());
                if (error.line() > 0) {
                    sb.append(':').append(error.line());
                }
                sb.append(": ").append(error.message());
            }
            return sb.toString();
        }
    }

    private OptChainJavacPipeline() {
    }

    /** True when any {@code .java} file under {@code root} contains a real {@code ?.} operator. */
    public static boolean treeContainsOptChain(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            return false;
        }
        if (OptChainLayout.isOptChainMirrorPath(root.toString())) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> javaFiles = walk
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .toList();
            for (Path javaFile : javaFiles) {
                if (fileContainsOptChain(javaFile)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean fileContainsOptChain(Path javaFile) throws IOException {
        String text = Files.readString(javaFile, StandardCharsets.UTF_8);
        return !OptChainSourceTransformer.findOptChainPositions(text).isEmpty();
    }

    /**
     * Compiles every {@code .java} file under {@code sourceRoots}. Files that contain {@code ?.}
     * are rewritten into {@code scratchDir} first. Class files land in {@code outputDir} and
     * are woven when they still contain {@code OptChainOps.opt}.
     *
     * @param javac executable from the module JDK ({@code bin/javac}), or {@code javac} on PATH
     * @param release value for {@code --release} ({@code 17}, {@code 21}, {@code 8}, …)
     */
    public static Result compile(
            List<Path> sourceRoots,
            List<Path> classPath,
            Path outputDir,
            Path scratchDir,
            String javac,
            String release
    ) throws IOException, InterruptedException {
        List<Path> originals = new ArrayList<>();
        for (Path root : sourceRoots) {
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }
            if (OptChainLayout.isOptChainMirrorPath(root.toString())) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(path -> path.toString().endsWith(".java"))
                        .filter(Files::isRegularFile)
                        .forEach(originals::add);
            }
        }
        if (originals.isEmpty()) {
            return new Result(true, 0, 0, List.of());
        }

        Files.createDirectories(scratchDir);
        Files.createDirectories(outputDir);
        Map<Path, Path> compileToOriginal = new LinkedHashMap<>();
        List<Path> rewrittenClassRels = new ArrayList<>();
        int rewritten = 0;
        int rootIndex = 0;
        for (Path root : sourceRoots) {
            if (root == null || !Files.isDirectory(root) || OptChainLayout.isOptChainMirrorPath(root.toString())) {
                rootIndex++;
                continue;
            }
            Path rootAbs = root.toAbsolutePath().normalize();
            for (Path original : originals) {
                Path abs = original.toAbsolutePath().normalize();
                if (!abs.startsWith(rootAbs)) {
                    continue;
                }
                Path relative = rootAbs.relativize(abs);
                String text = Files.readString(abs, StandardCharsets.UTF_8);
                OptChainSourceTransformer.Result transformed = OptChainSourceTransformer.transform(text);
                if (transformed.changed()) {
                    Path dest = scratchDir.resolve("r" + rootIndex).resolve(relative);
                    Path parent = dest.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.writeString(dest, transformed.source(), StandardCharsets.UTF_8);
                    compileToOriginal.put(dest.toAbsolutePath().normalize(), abs);
                    String fileName = relative.getFileName().toString();
                    String className = fileName.substring(0, fileName.length() - ".java".length());
                    rewrittenClassRels.add(relative.resolveSibling(className));
                    rewritten++;
                } else {
                    compileToOriginal.put(abs, abs);
                }
            }
            rootIndex++;
        }
        if (rewritten == 0) {
            return new Result(true, 0, 0, List.of());
        }

        List<String> command = new ArrayList<>();
        command.add(javac);
        command.add("--release");
        command.add(sanitizeRelease(release));
        command.add("-encoding");
        command.add("UTF-8");
        command.add("-proc:none");
        command.add("-d");
        command.add(outputDir.toAbsolutePath().normalize().toString());
        List<Path> existingClasspath = new ArrayList<>();
        for (Path entry : classPath) {
            if (entry != null && Files.exists(entry)) {
                existingClasspath.add(entry.toAbsolutePath().normalize());
            }
        }
        if (!existingClasspath.isEmpty()) {
            command.add("-classpath");
            command.add(joinClasspath(existingClasspath));
        }
        command.addAll(compileToOriginal.keySet().stream().map(Path::toString).toList());

        Path argFile = scratchDir.resolve("javac.args");
        Files.writeString(argFile, toArgFile(command.subList(1, command.size())), StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(javac, "@" + argFile.toAbsolutePath())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) {
            return new Result(false, rewritten, 0, parseErrors(output, compileToOriginal));
        }
        int woven = weaveClasses(outputDir, rewrittenClassRels);
        return new Result(true, rewritten, woven, List.of());
    }

    /** Maps a JPS/IDE language-level name ({@code JDK_17}, {@code JDK_1_8}) to a {@code --release} value. */
    public static String releaseOf(String languageLevelName) {
        if (languageLevelName == null || languageLevelName.isBlank()) {
            return "17";
        }
        String name = languageLevelName.trim();
        if (name.startsWith("JDK_")) {
            name = name.substring(4);
        }
        name = name.replace('_', '.');
        if (name.equals("1.8") || name.equals("8")) {
            return "8";
        }
        if (name.startsWith("1.") && name.length() > 2) {
            name = name.substring(2);
        }
        return sanitizeRelease(name);
    }

    private static String sanitizeRelease(String release) {
        if (release == null || !release.matches("[0-9]+(\\.[0-9]+)?")) {
            return "17";
        }
        return release;
    }

    private static String joinClasspath(List<Path> entries) {
        StringBuilder sb = new StringBuilder();
        for (Path entry : entries) {
            if (!sb.isEmpty()) {
                sb.append(java.io.File.pathSeparatorChar);
            }
            sb.append(entry);
        }
        return sb.toString();
    }

    /** One javac argument per line, quoted so spaces survive {@code @argfile}. */
    static String toArgFile(List<String> args) {
        StringBuilder sb = new StringBuilder();
        for (String arg : args) {
            sb.append('"');
            for (int i = 0; i < arg.length(); i++) {
                char c = arg.charAt(i);
                if (c == '\\' || c == '"') {
                    sb.append('\\');
                }
                sb.append(c);
            }
            sb.append('"').append('\n');
        }
        return sb.toString();
    }

    private static List<Diagnostic> parseErrors(String output, Map<Path, Path> compileToOriginal) {
        List<Diagnostic> errors = new ArrayList<>();
        if (output == null || output.isBlank()) {
            errors.add(new Diagnostic("", 0, "javac failed without a message"));
            return errors;
        }
        for (String line : output.split("\\R")) {
            Parsed parsed = parseJavacLine(line);
            if (parsed == null) {
                continue;
            }
            if (!"error".equals(parsed.kind())) {
                continue;
            }
            String source = remap(parsed.path(), compileToOriginal);
            errors.add(new Diagnostic(source, parsed.line(), parsed.message()));
        }
        if (errors.isEmpty()) {
            errors.add(new Diagnostic("", 0, output.strip()));
        }
        return errors;
    }

    record Parsed(String path, int line, String kind, String message) {
    }

    static Parsed parseJavacLine(String line) {
        // file:line: error: message
        int first = line.indexOf(':');
        if (first < 0) {
            return null;
        }
        int second = line.indexOf(':', first + 1);
        if (second < 0) {
            return null;
        }
        String path = line.substring(0, first);
        String lineNo = line.substring(first + 1, second).trim();
        int number;
        try {
            number = Integer.parseInt(lineNo);
        } catch (NumberFormatException ex) {
            // Windows drive letter: "C:\path\File.java:12: error: ..."
            int third = line.indexOf(':', second + 1);
            if (third < 0) {
                return null;
            }
            path = line.substring(0, second);
            try {
                number = Integer.parseInt(line.substring(second + 1, third).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
            second = third;
        }
        String rest = line.substring(second + 1).trim();
        int kindEnd = rest.indexOf(':');
        if (kindEnd < 0) {
            return null;
        }
        String kind = rest.substring(0, kindEnd).trim();
        String message = rest.substring(kindEnd + 1).trim();
        if (!kind.equals("error") && !kind.equals("warning")) {
            return null;
        }
        return new Parsed(path, number, kind, message);
    }

    private static String remap(String javacPath, Map<Path, Path> compileToOriginal) {
        try {
            Path compiled = Path.of(javacPath).toAbsolutePath().normalize();
            Path original = compileToOriginal.get(compiled);
            if (original != null) {
                return original.toString();
            }
        } catch (RuntimeException ignored) {
            // keep the compiler path
        }
        return javacPath;
    }

    /** Weaves {@code Name.class} and {@code Name$*.class} for each rewritten compilation unit. */
    private static int weaveClasses(Path outputDir, List<Path> classRels) throws IOException {
        int woven = 0;
        for (Path classRel : classRels) {
            Path classFile = outputDir.resolve(classRel.toString() + ".class");
            Path dir = classFile.getParent();
            if (dir == null || !Files.isDirectory(dir)) {
                continue;
            }
            String prefix = classFile.getFileName().toString();
            prefix = prefix.substring(0, prefix.length() - ".class".length());
            try (Stream<Path> list = Files.list(dir)) {
                for (Path candidate : list.toList()) {
                    String name = candidate.getFileName().toString();
                    if (name.equals(prefix + ".class")
                            || (name.startsWith(prefix + "$") && name.endsWith(".class"))) {
                        byte[] original = Files.readAllBytes(candidate);
                        byte[] rewritten = OptChainWeaver.weave(original);
                        if (rewritten != null) {
                            Files.write(candidate, rewritten);
                            woven++;
                        }
                    }
                }
            }
        }
        return woven;
    }
}
