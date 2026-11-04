package io.github.chensirj.optchain.idea.compile;

import io.github.chensirj.optchain.OptChainOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptChainJavacPipelineTest {

    @TempDir
    Path temp;

    @Test
    void rewriteCompileAndWeaveShortCircuitsNull() throws Exception {
        Path root = temp.resolve("src");
        Path pkg = root.resolve("demo");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Box.java"), """
                package demo;

                public class Box {
                    public String name;
                    public Box child;

                    public String city(Box box) {
                        return box?.child?.name;
                    }
                }
                """);
        Path output = temp.resolve("out");
        Path scratch = temp.resolve("scratch");
        String javac = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "javac.exe" : "javac").toString();

        Path annotations = classpathRoot(OptChainOps.class);
        OptChainJavacPipeline.Result result = OptChainJavacPipeline.compile(
                List.of(root),
                List.of(annotations),
                output,
                scratch,
                javac,
                "17");

        assertTrue(result.success(), result.summary());
        assertEquals(1, result.rewrittenFiles());
        assertTrue(result.wovenClasses() >= 1, result.summary());

        String rewritten = Files.readString(scratch.resolve("r0/demo/Box.java"));
        assertFalse(rewritten.contains("?."));
        assertTrue(rewritten.contains("OptChainOps.opt("));

        try (URLClassLoader loader = new URLClassLoader(new URL[]{output.toUri().toURL()}, null)) {
            Class<?> box = loader.loadClass("demo.Box");
            Object parent = box.getDeclaredConstructor().newInstance();
            box.getField("name").set(parent, "Shanghai");
            Object rootBox = box.getDeclaredConstructor().newInstance();
            box.getField("child").set(rootBox, parent);
            Method city = box.getMethod("city", box);
            assertEquals("Shanghai", city.invoke(rootBox, rootBox));
            assertNull(city.invoke(rootBox, new Object[]{null}));
            Object missingChild = box.getDeclaredConstructor().newInstance();
            assertNull(city.invoke(rootBox, missingChild));
        }
    }

    private static Path classpathRoot(Class<?> type) throws Exception {
        String resource = type.getName().replace('.', '/') + ".class";
        URL url = type.getClassLoader().getResource(resource);
        if (url == null) {
            throw new IllegalStateException("missing " + resource);
        }
        if ("jar".equals(url.getProtocol())) {
            String file = url.getPath();
            int bang = file.indexOf('!');
            String jar = bang >= 0 ? file.substring(0, bang) : file;
            if (jar.startsWith("file:")) {
                return Path.of(URI.create(jar));
            }
            return Path.of(jar);
        }
        Path classFile = Path.of(url.toURI());
        Path relative = Path.of(resource);
        Path root = classFile;
        for (int i = 0; i < relative.getNameCount(); i++) {
            root = root.getParent();
        }
        return root;
    }

    @Test
    void releaseOfMapsJdkNames() {
        assertEquals("17", OptChainJavacPipeline.releaseOf("JDK_17"));
        assertEquals("8", OptChainJavacPipeline.releaseOf("JDK_1_8"));
        assertEquals("21", OptChainJavacPipeline.releaseOf("JDK_21"));
        assertEquals("17", OptChainJavacPipeline.releaseOf(null));
    }

    @Test
    void javacErrorLineKeepsWindowsDrive() {
        OptChainJavacPipeline.Parsed parsed = OptChainJavacPipeline.parseJavacLine(
                "C:\\src\\Demo.java:16: error: illegal start of expression");
        assertNotNull(parsed);
        assertEquals("C:\\src\\Demo.java", parsed.path());
        assertEquals(16, parsed.line());
        assertEquals("error", parsed.kind());
    }

    @Test
    void jpsServicesAreOnTheClasspath() {
        assertNotNull(OptChainJpsBuilderService.class.getClassLoader().getResource(
                "META-INF/services/org.jetbrains.jps.incremental.BuilderService"));
        assertNotNull(OptChainExcludedJavaSourceRootProvider.class.getClassLoader().getResource(
                "META-INF/services/org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider"));
    }
}
