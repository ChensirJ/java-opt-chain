package io.ch.java.opt.chain.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptChainSourceTransformerTest {

    private static final String IMPORT_LINE = "import io.ch.java.opt.chain.OptChain;";

    @Test
    void rewritesCodeAndInjectsAnnotationAndImport() {
        String src = """
                package demo;

                public class Sample {
                    public String city(User u) {
                        return u?.getAddress()?.getName();
                    }
                }
                """;

        OptChainSourceTransformer.Result result = OptChainSourceTransformer.transform(src);
        String out = result.source();

        assertTrue(result.changed());
        assertEquals(2, result.replacements());
        assertFalse(out.contains("?."));
        assertTrue(out.contains("return u.getAddress().getName();"));
        assertEquals(1, countAnnotations(out));
        assertEquals(1, countExact(out, IMPORT_LINE));
        assertTrue(out.indexOf("package demo;") < out.indexOf(IMPORT_LINE));
        assertTrue(out.indexOf(IMPORT_LINE) < out.indexOf("public String city"));
        assertTrue(out.indexOf("@OptChain") < out.indexOf("public String city"));
    }

    @Test
    void rewritesFieldChainsLikeMethodChains() {
        String src = """
                package demo;

                public class Sample {
                    // keep u?.address in a comment
                    public String cityName(User u) {
                        return u?.address?.city?.name;
                    }

                    public int nameLength(User u) {
                        return u?.address?.city?.name?.length();
                    }
                }
                """;

        OptChainSourceTransformer.Result result = OptChainSourceTransformer.transform(src);
        String out = result.source();

        assertTrue(result.changed());
        assertEquals(7, result.replacements());
        assertFalse(out.contains("return u?."));
        assertTrue(out.contains("// keep u?.address in a comment"));
        assertTrue(out.contains("return u.address.city.name;"));
        assertTrue(out.contains("return u.address.city.name.length();"));
        assertEquals(2, countAnnotations(out));
        assertEquals(1, countExact(out, IMPORT_LINE));
        assertTrue(out.indexOf("@OptChain") < out.indexOf("public String cityName"));
        assertTrue(out.indexOf("public String cityName") < out.lastIndexOf("@OptChain"));
        assertTrue(out.lastIndexOf("@OptChain") < out.indexOf("public int nameLength"));
    }

    @Test
    void commentsAndStringsAreNotRewritten() {
        String src = """
                package demo;

                public class Sample {
                    // keep ?. in line comment
                    /* keep ?. in block comment */
                    String literal = "keep ?. in string";
                    String escaped = "keep \\"?. after escaped quote";

                    public String city(User u) {
                        return u?.getName();
                    }
                }
                """;

        OptChainSourceTransformer.Result result = OptChainSourceTransformer.transform(src);
        String out = result.source();

        assertEquals(1, result.replacements());
        assertTrue(out.contains("// keep ?. in line comment"));
        assertTrue(out.contains("/* keep ?. in block comment */"));
        assertTrue(out.contains("\"keep ?. in string\""));
        assertTrue(out.contains("\\\"?. after escaped quote"));
        assertTrue(out.contains("return u.getName();"));
        assertFalse(out.contains("u?.getName()"));
        assertEquals(1, countAnnotations(out));
        assertEquals(1, countExact(out, IMPORT_LINE));
    }

    @Test
    void commentOnlySourceIsUnchanged() {
        String src = """
                package demo;

                public class Sample {
                    // u?.getName()
                    /* "?. " */
                    String note = "?. stays";

                    public String city(User u) {
                        return u.getName();
                    }
                }
                """;

        OptChainSourceTransformer.Result result = OptChainSourceTransformer.transform(src);

        assertFalse(result.changed());
        assertEquals(0, result.replacements());
        assertEquals(src, result.source());
    }

    @Test
    void alreadyAnnotatedMethodIsNotDoubleAnnotated() {
        String src = """
                package demo;

                import io.ch.java.opt.chain.OptChain;

                public class Sample {
                    @OptChain
                    public String city(User u) {
                        return u?.getName();
                    }

                    @OptChain public int len(User u) {
                        return u?.getName()?.length();
                    }
                }
                """;

        OptChainSourceTransformer.Result result = OptChainSourceTransformer.transform(src);
        String out = result.source();

        assertTrue(result.changed());
        assertEquals(3, result.replacements());
        assertEquals(2, countAnnotations(out));
        assertEquals(1, countExact(out, IMPORT_LINE));
        assertFalse(out.contains("?."));
        assertTrue(out.contains("return u.getName();"));
        assertTrue(out.contains("return u.getName().length();"));
    }

    @Test
    void javadocMentionDoesNotCountAsAnnotation() {
        String src = """
                package demo;

                public class Sample {
                    /** {@code @OptChain} is mentioned but not applied. */
                    public String city(User u) {
                        String label = "@OptChain";
                        return u?.getName();
                    }
                }
                """;

        OptChainSourceTransformer.Result result = OptChainSourceTransformer.transform(src);
        String out = result.source();

        assertEquals(1, result.replacements());
        assertTrue(out.contains("/** {@code @OptChain} is mentioned but not applied. */"));
        assertTrue(out.contains("\"@OptChain\""));
        assertEquals(1, countAnnotations(out));
        assertTrue(out.indexOf("/** {@code @OptChain}") < out.indexOf("@OptChain\n"));
        assertTrue(out.indexOf("@OptChain\n") < out.indexOf("public String city"));
        assertTrue(out.contains("return u.getName();"));
    }

    @Test
    void alreadyAnnotatedMethodWithJavadocIsNotDoubleAnnotated() {
        String src = """
                package demo;

                import io.ch.java.opt.chain.OptChain;

                public class Sample {
                    @OptChain
                    /** {@code @OptChain} already applied. */
                    public String city(User u) {
                        return u?.getName();
                    }
                }
                """;

        String out = OptChainSourceTransformer.transform(src).source();

        assertTrue(out.contains("/** {@code @OptChain} already applied. */"));
        assertEquals(1, countAnnotations(out));
        assertEquals(1, countExact(out, IMPORT_LINE));
        assertFalse(out.contains("?."));
    }

    @Test
    void annotatesOnlyMethodsThatUseTheOperator() {
        String src = """
                package demo;

                public class Sample {
                    public int plain(User u) {
                        return u.getName().length();
                    }

                    public String chained(User u) {
                        return u?.getName();
                    }
                }
                """;

        String out = OptChainSourceTransformer.transform(src).source();

        assertEquals(1, countAnnotations(out));
        assertTrue(out.indexOf("public int plain") < out.indexOf("@OptChain"));
        assertTrue(out.indexOf("@OptChain") < out.indexOf("public String chained"));
    }

    private static int countAnnotations(String source) {
        int count = 0;
        int from = 0;
        String needle = "@OptChain";
        while (from < source.length()) {
            int index = source.indexOf(needle, from);
            if (index < 0) {
                break;
            }
            int after = index + needle.length();
            // Count code-level annotations only: skip javadoc, comments, and string literals.
            if (after >= source.length() || !Character.isJavaIdentifierPart(source.charAt(after))) {
                int lineStart = source.lastIndexOf('\n', index) + 1;
                int lineEnd = source.indexOf('\n', index);
                if (lineEnd < 0) {
                    lineEnd = source.length();
                }
                String line = source.substring(lineStart, lineEnd);
                if (!line.contains("\"") && !line.contains("{@code") && !line.contains("/*") && !line.trim().startsWith("//")
                        && !line.trim().startsWith("*")) {
                    count++;
                }
            }
            from = after;
        }
        return count;
    }

    private static int countExact(String source, String needle) {
        int count = 0;
        int from = 0;
        while (from < source.length()) {
            int index = source.indexOf(needle, from);
            if (index < 0) {
                return count;
            }
            count++;
            from = index + needle.length();
        }
        return count;
    }
}
