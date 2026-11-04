package io.github.chensirj.optchain.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Preprocesses Java sources that contain {@code ?.} into legal Java under
 * {@code target/opt-chain/sources} (and {@code target/opt-chain/test-sources}).
 * <p>
 * When any file in a source root contains {@code ?.}, the whole root is mirrored
 * (with {@code ?.} rewritten) and that directory <em>replaces</em> the original
 * compile source root for this Maven build. {@code javac} never sees illegal
 * {@code ?.}. The mirror is intentionally <em>not</em> under
 * {@code target/generated-sources}: IntelliJ marks that tree as a source root
 * and also keeps {@code src/main/java}, which reports every class twice.
 * Authoring sources stay the only IDE source root. CLI builds still compile the mirror.
 * </p>
 */
@Mojo(name = "preprocess", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class OptChainPreprocessMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}/" + OptChainLayout.MAIN_DIR)
    private File outputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/" + OptChainLayout.TEST_DIR)
    private File testOutputDirectory;

    /** When true, also scan test sources (bind a second execution to generate-test-sources). */
    @Parameter(property = "optchain.preprocess.tests", defaultValue = "false")
    private boolean processTestSources;

    @Parameter(property = "optchain.preprocess.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("opt-chain:preprocess skipped (optchain.preprocess.skip=true)");
            return;
        }

        if (processTestSources) {
            process(project.getTestCompileSourceRoots(), testOutputDirectory, true);
        } else {
            process(project.getCompileSourceRoots(), outputDirectory, false);
        }
    }

    private void process(List<String> sourceRoots, File outDir, boolean test)
            throws MojoExecutionException {
        if (sourceRoots == null || sourceRoots.isEmpty()) {
            getLog().info("opt-chain:preprocess — no source roots (" + (test ? "test" : "main") + ")");
            return;
        }

        // Snapshot roots; we may mutate the project's list.
        List<String> rootsSnapshot = new ArrayList<>(sourceRoots);
        Path outRoot = outDir.toPath();
        boolean anyTransformed = false;
        int transformedFiles = 0;
        int replacements = 0;
        List<String> rootsToReplace = new ArrayList<>();

        for (String rootStr : rootsSnapshot) {
            Path root = Path.of(rootStr);
            if (!Files.isDirectory(root)) {
                continue;
            }
            // Skip our own generated root if re-entrant
            if (root.toAbsolutePath().normalize().equals(outRoot.toAbsolutePath().normalize())) {
                continue;
            }

            if (!rootContainsOptChain(root)) {
                getLog().debug("opt-chain:preprocess skipping " + root + " (no ?.)");
                continue;
            }
            rootsToReplace.add(rootStr);
            MirrorResult mirrored = mirrorRoot(root, outRoot);
            anyTransformed = true;
            transformedFiles += mirrored.transformedFiles;
            replacements += mirrored.replacements;
        }

        if (!anyTransformed) {
            deleteLegacyMirror(outDir.toPath());
            getLog().info("opt-chain:preprocess — no ?. sources in " + (test ? "test" : "main"));
            return;
        }

        List<String> liveRoots = test
                ? project.getTestCompileSourceRoots()
                : project.getCompileSourceRoots();
        for (String root : rootsToReplace) {
            liveRoots.remove(root);
            getLog().info("opt-chain:preprocess removed compile root " + root);
        }
        String outAbs = outDir.getAbsolutePath();
        if (!liveRoots.contains(outAbs)) {
            if (test) {
                project.addTestCompileSourceRoot(outAbs);
            } else {
                project.addCompileSourceRoot(outAbs);
            }
        }

        deleteLegacyMirror(outDir.toPath());

        getLog().info("opt-chain:preprocess mirrored roots with ?. → " + outDir
                + " (" + transformedFiles + " file(s) rewritten, "
                + replacements + " ?. wrapped in OptChainOps.opt). "
                + "Not under target/generated-sources, so IntelliJ keeps src as the only source root.");
    }

    /**
     * Removes a previous {@code target/generated-sources/opt-chain} tree when this run
     * writes somewhere else. IDEA's generated-sources scan would otherwise keep the old
     * mirror as a second source root.
     */
    private void deleteLegacyMirror(Path currentOut) throws MojoExecutionException {
        Path buildDir = Path.of(project.getBuild().getDirectory());
        Path legacy = buildDir.resolve(processTestSources
                ? OptChainLayout.LEGACY_TEST_DIR
                : OptChainLayout.LEGACY_MAIN_DIR);
        if (legacy.toAbsolutePath().normalize().equals(currentOut.toAbsolutePath().normalize())) {
            return;
        }
        if (!Files.exists(legacy)) {
            return;
        }
        try {
            deleteTree(legacy);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "opt-chain preprocess could not remove legacy mirror " + legacy, e);
        }
        getLog().info("opt-chain:preprocess removed legacy mirror " + legacy);
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private boolean rootContainsOptChain(Path root) throws MojoExecutionException {
        List<Path> javaFiles;
        try (Stream<Path> walk = Files.walk(root)) {
            javaFiles = walk
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .toList();
        } catch (IOException e) {
            throw new MojoExecutionException("opt-chain preprocess failed for " + root, e);
        }
        for (Path javaFile : javaFiles) {
            String rel = portableRelative(root, javaFile);
            try {
                String content = Files.readString(javaFile, StandardCharsets.UTF_8);
                if (!OptChainSourceTransformer.findOptChainPositions(content).isEmpty()) {
                    return true;
                }
            } catch (IOException | RuntimeException e) {
                throw new MojoExecutionException(
                        "opt-chain preprocess failed for " + rel + " under " + root, e);
            }
        }
        return false;
    }

    private MirrorResult mirrorRoot(Path root, Path outRoot) throws MojoExecutionException {
        int transformed = 0;
        int replacements = 0;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new MojoExecutionException("opt-chain preprocess failed for " + root, e);
        }
        for (Path src : files) {
            String rel = portableRelative(root, src);
            Path dest = outRoot.resolve(root.relativize(src));
            try {
                Path parent = dest.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                if (src.toString().endsWith(".java")) {
                    String content = Files.readString(src, StandardCharsets.UTF_8);
                    OptChainSourceTransformer.Result result = OptChainSourceTransformer.transform(content);
                    if (result.changed()) {
                        Files.writeString(dest, result.source(), StandardCharsets.UTF_8);
                        transformed++;
                        replacements += result.replacements();
                        getLog().info("opt-chain:preprocess " + rel
                                + " (" + result.replacements() + " ?. wrapped in OptChainOps.opt)");
                    } else {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException e) {
                throw new MojoExecutionException(
                        "opt-chain preprocess failed for " + rel + " under " + root, e);
            }
        }
        return new MirrorResult(transformed, replacements);
    }

    private static String portableRelative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private record MirrorResult(int transformedFiles, int replacements) {
    }
}
