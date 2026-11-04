package io.ch.java.opt.chain.plugin;

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
import java.util.List;
import java.util.stream.Stream;

/**
 * Preprocesses Java sources that contain {@code ?.} into legal Java under
 * {@code target/generated-sources/opt-chain} (and generated-test-sources).
 * <p>
 * When any file in a source root contains {@code ?.}, the whole root is mirrored
 * into the generated directory (with {@code ?.} rewritten) and that generated
 * directory <em>replaces</em> the original compile source root. This avoids
 * javac seeing illegal {@code ?.} and avoids exclude patterns that would also
 * drop the generated twin.
 * </p>
 */
@Mojo(name = "preprocess", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class OptChainPreprocessMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/opt-chain")
    private File outputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/generated-test-sources/opt-chain")
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

            try {
                if (!rootContainsOptChain(root)) {
                    continue;
                }
                rootsToReplace.add(rootStr);
                MirrorResult mirrored = mirrorRoot(root, outRoot);
                anyTransformed = true;
                transformedFiles += mirrored.transformedFiles;
                replacements += mirrored.replacements;
            } catch (IOException e) {
                throw new MojoExecutionException("opt-chain preprocess failed for " + root, e);
            }
        }

        if (!anyTransformed) {
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

        getLog().info("opt-chain:preprocess mirrored roots with ?. → " + outDir
                + " (" + transformedFiles + " file(s) rewritten, "
                + replacements + " ?. → .)");
    }

    private static boolean rootContainsOptChain(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .anyMatch(p -> {
                        try {
                            String content = Files.readString(p, StandardCharsets.UTF_8);
                            return !OptChainSourceTransformer.findOptChainPositions(content).isEmpty();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioe) {
                throw ioe;
            }
            throw e;
        }
    }

    private MirrorResult mirrorRoot(Path root, Path outRoot) throws IOException {
        int transformed = 0;
        int replacements = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            for (Path src : files) {
                Path relative = root.relativize(src);
                Path dest = outRoot.resolve(relative);
                Files.createDirectories(dest.getParent());
                if (src.toString().endsWith(".java")) {
                    String content = Files.readString(src, StandardCharsets.UTF_8);
                    OptChainSourceTransformer.Result result = OptChainSourceTransformer.transform(content);
                    if (result.changed()) {
                        Files.writeString(dest, result.source(), StandardCharsets.UTF_8);
                        transformed++;
                        replacements += result.replacements();
                        getLog().info("opt-chain:preprocess " + relative.toString().replace('\\', '/')
                                + " (" + result.replacements() + " ?. → .)");
                    } else {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return new MirrorResult(transformed, replacements);
    }

    private record MirrorResult(int transformedFiles, int replacements) {
    }
}
