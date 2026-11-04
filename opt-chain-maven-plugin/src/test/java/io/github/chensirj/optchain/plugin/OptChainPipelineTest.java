package io.github.chensirj.optchain.plugin;

import io.github.chensirj.optchain.OptChain;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Compiles preprocessed snippets, weaves them, and checks null behavior.
 * The plugin module is not itself preprocessed, so {@code ?.} appears only inside text blocks.
 */
class OptChainPipelineTest {

    private static ClassLoader loader;
    private static Class<?> carrier;
    private static Class<?> legacy;
    private static Class<?> inits;

    @BeforeAll
    static void compileWeaveAndLoad() throws Exception {
        Map<String, String> sources = Map.of(
                "weave/Carrier.java", CARRIER,
                "weave/Legacy.java", LEGACY,
                "weave/Inits.java", INITS);
        Map<String, byte[]> woven = compileAndWeave(sources);
        loader = new ClassLoader(OptChainPipelineTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = woven.get(name);
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        carrier = loader.loadClass("weave.Carrier");
        legacy = loader.loadClass("weave.Legacy");
        inits = loader.loadClass("weave.Inits");
    }

    @Test
    void plainDotInWovenMethodStillThrows() {
        Object self = carrier("self", null);
        Object linked = carrier("A", carrier("B", null));
        assertThrows(NullPointerException.class, () -> call(carrier, "plain", new Class<?>[]{carrier}, self, new Object[]{null}));
        assertEquals("B", call(carrier, "plain", new Class<?>[]{carrier}, self, new Object[]{linked}));
    }

    @Test
    void optionalChainShortCircuits() {
        Object self = carrier("self", null);
        assertNull(call(carrier, "deep", new Class<?>[]{carrier}, self, new Object[]{null}));
        assertNull(call(carrier, "deep", new Class<?>[]{carrier}, self, new Object[]{carrier("A", null)}));
        assertEquals("B", call(carrier, "deep", new Class<?>[]{carrier}, self,
                new Object[]{carrier("A", carrier("B", null))}));
    }

    @Test
    void optionalThenPlainDotStillThrows() {
        Object self = carrier("self", null);
        assertThrows(NullPointerException.class,
                () -> call(carrier, "thenPlain", new Class<?>[]{carrier}, self, new Object[]{null}));
        assertThrows(NullPointerException.class,
                () -> call(carrier, "thenPlain", new Class<?>[]{carrier}, self, new Object[]{carrier("A", null)}));
        assertEquals("B", call(carrier, "thenPlain", new Class<?>[]{carrier}, self,
                new Object[]{carrier("A", carrier("B", null))}));
    }

    @Test
    void multipleChainsOnOneLine() {
        Object self = carrier("self", null);
        assertEquals("null:null", call(carrier, "two", new Class<?>[]{carrier, carrier}, self, new Object[]{null, null}));
        assertEquals("A:null", call(carrier, "two", new Class<?>[]{carrier, carrier}, self,
                new Object[]{carrier("A", null), null}));
        assertEquals("A:B", call(carrier, "two", new Class<?>[]{carrier, carrier}, self,
                new Object[]{carrier("A", null), carrier("B", null)}));
    }

    @Test
    void mixedPlainAndOptionalOnOneLine() {
        Object self = carrier("self", null);
        assertThrows(NullPointerException.class,
                () -> call(carrier, "mixed", new Class<?>[]{carrier, carrier}, self,
                        new Object[]{null, carrier("B", null)}));
        assertEquals("A:null", call(carrier, "mixed", new Class<?>[]{carrier, carrier}, self,
                new Object[]{carrier("A", null), null}));
        assertEquals("A:B", call(carrier, "mixed", new Class<?>[]{carrier, carrier}, self,
                new Object[]{carrier("A", null), carrier("B", null)}));
    }

    @Test
    void staticMethodLambdaAnonymousAndLocalClass() {
        assertNull(call(carrier, "stat", new Class<?>[]{carrier}, null, new Object[]{null}));
        assertEquals("A", call(carrier, "stat", new Class<?>[]{carrier}, null, new Object[]{carrier("A", null)}));
        assertNull(call(carrier, "lam", new Class<?>[]{carrier}, null, new Object[]{null}));
        assertEquals("A", call(carrier, "lam", new Class<?>[]{carrier}, null, new Object[]{carrier("A", null)}));

        Object self = carrier("self", null);
        assertNull(call(carrier, "anon", new Class<?>[]{carrier}, self, new Object[]{null}));
        assertEquals("A", call(carrier, "anon", new Class<?>[]{carrier}, self, new Object[]{carrier("A", null)}));
        assertNull(call(carrier, "local", new Class<?>[]{carrier}, self, new Object[]{null}));
        assertEquals("A", call(carrier, "local", new Class<?>[]{carrier}, self, new Object[]{carrier("A", null)}));
    }

    @Test
    void arrayLengthCallReceiverAndPrivateCall() {
        Object self = carrier("self", null);
        assertEquals(0, call(carrier, "len", new Class<?>[]{carrier.arrayType()}, self, new Object[]{null}));
        assertEquals(2, call(carrier, "len", new Class<?>[]{carrier.arrayType()}, self,
                new Object[]{java.lang.reflect.Array.newInstance(carrier, 2)}));
        assertNull(call(carrier, "viaCall", new Class<?>[]{carrier}, self, new Object[]{null}));
        assertEquals("A", call(carrier, "viaCall", new Class<?>[]{carrier}, self, new Object[]{carrier("A", null)}));
        assertEquals("Z", call(carrier, "viaNew", new Class<?>[]{String.class}, self, new Object[]{"Z"}));
        assertNull(call(carrier, "readHidden", new Class<?>[]{carrier}, self, new Object[]{null}));
        assertEquals("A", call(carrier, "readHidden", new Class<?>[]{carrier}, self, new Object[]{carrier("A", null)}));
    }

    @Test
    void argumentsAreEvaluatedBeforeTheNullCheck() {
        Object self = carrier("self", null);
        int[] hits = {0};
        assertNull(call(carrier, "callChild", new Class<?>[]{carrier, int[].class}, self, new Object[]{null, hits}));
        assertEquals(1, hits[0]);
        assertEquals("A", call(carrier, "callChild", new Class<?>[]{carrier, int[].class}, self,
                new Object[]{carrier("A", null), hits}));
        assertEquals(3, hits[0]);
    }

    @Test
    void legacyAnnotationStillNullChecksEveryInstanceAccess() {
        Object self = newInstance(legacy);
        assertNull(call(legacy, "city", new Class<?>[]{carrier}, self, new Object[]{null}));
        assertNull(call(legacy, "city", new Class<?>[]{carrier}, self, new Object[]{carrier("A", null)}));
        assertEquals("B", call(legacy, "city", new Class<?>[]{carrier}, self,
                new Object[]{carrier("A", carrier("B", null))}));
        assertEquals(0, call(legacy, "len", new Class<?>[]{carrier}, self, new Object[]{null}));
        assertEquals(1, call(legacy, "len", new Class<?>[]{carrier}, self, new Object[]{carrier("A", null)}));
        assertThrows(NullPointerException.class,
                () -> call(legacy, "preciseWithoutMarkers", new Class<?>[]{carrier}, self, new Object[]{null}));
    }

    @Test
    void constructorsFieldInitializersAndStaticInitializers() throws Exception {
        assertEquals("Field", inits.getField("fieldCaptured").get(newInstance(inits, null)));
        assertEquals("Static", inits.getField("STATIC_OK").get(null));
        assertNull(inits.getField("STATIC_MISSING").get(null));

        Object nullUser = newInstance(inits, null);
        assertNull(inits.getField("ctorBody").get(nullUser));
        Object present = newInstance(inits, carrier("Ctor", null));
        assertEquals("Ctor", inits.getField("ctorBody").get(present));

        assertThrows(NullPointerException.class,
                () -> call(inits, "plainCtor", new Class<?>[]{carrier}, newInstance(inits, carrier("ok", null)),
                        new Object[]{null}));
        assertEquals("A", call(inits, "plainCtor", new Class<?>[]{carrier}, newInstance(inits, carrier("ok", null)),
                new Object[]{carrier("A", null)}));
    }

    private static Object carrier(String name, Object next) {
        try {
            return carrier.getConstructor(String.class, carrier).newInstance(name, next);
        } catch (InvocationTargetException ex) {
            throw unchecked(ex);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Object newInstance(Class<?> type) {
        try {
            return type.getConstructor().newInstance();
        } catch (InvocationTargetException ex) {
            throw unchecked(ex);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Object newInstance(Class<?> type, Object user) {
        try {
            return type.getConstructor(carrier).newInstance(user);
        } catch (InvocationTargetException ex) {
            throw unchecked(ex);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Object call(Class<?> type, String name, Class<?>[] params, Object target, Object[] args) {
        try {
            Method method = type.getMethod(name, params);
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw unchecked(ex);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static RuntimeException unchecked(InvocationTargetException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(cause);
    }

    private static Map<String, byte[]> compileAndWeave(Map<String, String> sources) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK javac is required (ToolProvider.getSystemJavaCompiler() was null)");
        }
        Path root = Files.createTempDirectory("optchain-pipeline");
        Path srcDir = root.resolve("src");
        Path outDir = root.resolve("out");
        Files.createDirectories(outDir);
        List<Path> files = new ArrayList<>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            Path file = srcDir.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, OptChainSourceTransformer.transform(entry.getValue()).source());
            files.add(file);
        }
        String classpath = Path.of(OptChain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager filesManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> units = filesManager.getJavaFileObjectsFromPaths(files);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    filesManager,
                    diagnostics,
                    List.of("-classpath", classpath, "-d", outDir.toString(), "--release", "17", "-g"),
                    null,
                    units);
            if (!Boolean.TRUE.equals(task.call())) {
                fail("javac failed: " + diagnostics.getDiagnostics());
            }
        }

        Map<String, byte[]> woven = new HashMap<>();
        try (var walk = Files.walk(outDir)) {
            for (Path classFile : walk.filter(path -> path.toString().endsWith(".class")).toList()) {
                byte[] original = Files.readAllBytes(classFile);
                byte[] rewritten = OptChainWeaver.weave(original);
                byte[] bytes = rewritten == null ? original : rewritten;
                ClassReader reader = new ClassReader(bytes);
                assertNoOptMarker(reader.getClassName(), bytes);
                woven.put(reader.getClassName().replace('/', '.'), bytes);
            }
        }
        return woven;
    }

    private static void assertNoOptMarker(String internalName, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG);
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode call && OptChainWeaver.isOptCall(call)) {
                    fail(internalName + "." + method.name + " still calls OptChainOps.opt");
                }
            }
        }
    }

    private static final String CARRIER = """
            package weave;

            import java.util.function.Supplier;

            public class Carrier {
                public String name;
                public Carrier next;

                public Carrier(String name, Carrier next) {
                    this.name = name;
                    this.next = next;
                }

                public String getName() {
                    return name;
                }

                public Carrier getNext() {
                    return next;
                }

                public String plain(Carrier a) {
                    String keep = a?.getName();
                    return a.getNext().getName();
                }

                public String deep(Carrier a) {
                    return a?.getNext()?.getName();
                }

                public String thenPlain(Carrier a) {
                    return a?.getNext().getName();
                }

                public String two(Carrier a, Carrier b) {
                    return a?.getName() + ":" + b?.getName();
                }

                public String mixed(Carrier plain, Carrier optional) {
                    return plain.getName() + ":" + optional?.getName();
                }

                public static String stat(Carrier a) {
                    return a?.getName();
                }

                public static String lam(Carrier a) {
                    Supplier<String> supplier = () -> a?.getName();
                    return supplier.get();
                }

                public String anon(Carrier a) {
                    Supplier<String> supplier = new Supplier<String>() {
                        @Override
                        public String get() {
                            return a?.getName();
                        }
                    };
                    return supplier.get();
                }

                public String local(Carrier a) {
                    class Inner {
                        String n() {
                            return a?.getName();
                        }
                    }
                    return new Inner().n();
                }

                public int len(Carrier[] values) {
                    return values?.length;
                }

                public static Carrier id(Carrier a) {
                    return a;
                }

                public String viaCall(Carrier a) {
                    return id(a)?.getName();
                }

                public String viaNew(String value) {
                    return new Carrier(value, null)?.getName();
                }

                private String hidden() {
                    return name;
                }

                public String readHidden(Carrier other) {
                    return other?.hidden();
                }

                public String child(int[] hits) {
                    hits[0]++;
                    return name;
                }

                public static int[] bump(int[] hits) {
                    hits[0]++;
                    return hits;
                }

                public String callChild(Carrier a, int[] hits) {
                    return a?.child(bump(hits));
                }
            }
            """;

    private static final String LEGACY = """
            package weave;

            import io.github.chensirj.optchain.OptChain;

            public class Legacy {
                @OptChain
                public String city(Carrier a) {
                    return a.getNext().getName();
                }

                @OptChain
                public int len(Carrier a) {
                    return a.getName().length();
                }

                @OptChain(precise = true)
                public String preciseWithoutMarkers(Carrier a) {
                    return a.getName();
                }
            }
            """;

    private static final String INITS = """
            package weave;

            public class Inits {
                public final Carrier userField = new Carrier("Field", null);
                public final String fieldCaptured = userField?.getName();

                public static final Carrier STATIC_USER = new Carrier("Static", null);
                public static final String STATIC_OK = STATIC_USER?.getName();
                public static final Carrier STATIC_NULL = null;
                public static final String STATIC_MISSING = STATIC_NULL?.getName();

                public final String ctorBody;

                public Inits(Carrier user) {
                    this.ctorBody = user?.getName();
                }

                public String plainCtor(Carrier user) {
                    return new PlainHolder(user).value;
                }

                public static class PlainHolder {
                    final String value;

                    PlainHolder(Carrier user) {
                        String keep = user?.getName();
                        this.value = user.getName();
                    }
                }
            }
            """;
}
