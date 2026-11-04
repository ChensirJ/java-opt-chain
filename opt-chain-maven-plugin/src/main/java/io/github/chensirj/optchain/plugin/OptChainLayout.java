package io.github.chensirj.optchain.plugin;

/**
 * Where preprocess writes the legal Java mirror.
 * <p>
 * The directory is {@code target/opt-chain}, not {@code target/generated-sources}.
 * IntelliJ's Maven import marks {@code target/generated-sources/**} as a source root
 * and also keeps {@code src/main/java}, which makes every mirrored class a duplicate.
 * A folder under {@code target/} that is not {@code generated-sources} stays excluded.
 * </p>
 */
public final class OptChainLayout {

    /** Relative to {@code ${project.build.directory}}. */
    public static final String MAIN_DIR = "opt-chain/sources";

    /** Relative to {@code ${project.build.directory}}. */
    public static final String TEST_DIR = "opt-chain/test-sources";

    /** Previous main output. Deleted on preprocess so IDEA stops importing it. */
    public static final String LEGACY_MAIN_DIR = "generated-sources/opt-chain";

    /** Previous test output. Deleted on preprocess so IDEA stops importing it. */
    public static final String LEGACY_TEST_DIR = "generated-test-sources/opt-chain";

    private OptChainLayout() {
    }

    /**
     * True when {@code path} is an opt-chain mirror directory (current or legacy),
     * or a file inside one. Separators may be {@code /} or {@code \}.
     */
    public static boolean isOptChainMirrorPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        int scheme = normalized.indexOf("://");
        if (scheme >= 0) {
            normalized = normalized.substring(scheme + 3);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return containsDir(normalized, MAIN_DIR)
                || containsDir(normalized, TEST_DIR)
                || containsDir(normalized, LEGACY_MAIN_DIR)
                || containsDir(normalized, LEGACY_TEST_DIR);
    }

    /** True when {@code path} equals {@code dir} or has {@code /dir} or {@code /dir/} as a segment sequence. */
    private static boolean containsDir(String path, String dir) {
        if (path.equals(dir)) {
            return true;
        }
        String segment = "/" + dir;
        int at = path.indexOf(segment);
        if (at < 0) {
            return false;
        }
        int end = at + segment.length();
        return end == path.length() || path.charAt(end) == '/';
    }
}
