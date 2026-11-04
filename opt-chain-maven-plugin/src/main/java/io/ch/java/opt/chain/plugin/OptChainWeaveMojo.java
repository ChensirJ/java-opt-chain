package io.ch.java.opt.chain.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scans {@code ${project.build.outputDirectory}} and weaves {@code @OptChain} methods
 * during {@code process-classes}.
 */
@Mojo(name = "weave", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public class OptChainWeaveMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private java.io.File outputDirectory;

    /** Set {@code -Doptchain.skip=true} to disable weaving. */
    @Parameter(property = "optchain.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("opt-chain:weave skipped (optchain.skip=true)");
            return;
        }
        if (outputDirectory == null || !outputDirectory.isDirectory()) {
            getLog().info("opt-chain:weave — no output directory, nothing to do");
            return;
        }

        Path root = outputDirectory.toPath();
        List<Path> classFiles;
        try (Stream<Path> walk = Files.walk(root)) {
            classFiles = walk.filter(p -> p.toString().endsWith(".class")).toList();
        } catch (IOException e) {
            throw new MojoExecutionException("opt-chain weave failed walking " + root, e);
        }

        int rewritten = 0;
        int scanned = 0;
        for (Path path : classFiles) {
            String rel = root.relativize(path).toString().replace('\\', '/');
            scanned++;
            try {
                byte[] original = Files.readAllBytes(path);
                byte[] woven = OptChainWeaver.weave(original);
                if (woven != null) {
                    Files.write(path, woven);
                    rewritten++;
                    getLog().info("opt-chain:woven " + rel);
                } else {
                    getLog().debug("opt-chain:weave skipping " + rel + " (no @OptChain)");
                }
            } catch (IOException | RuntimeException e) {
                throw new MojoExecutionException("opt-chain weave failed for " + rel, e);
            }
        }

        getLog().info("opt-chain:weave scanned " + scanned
                + " class file(s), rewrote " + rewritten);
    }
}
