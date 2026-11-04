package io.ch.java.opt.chain.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
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
        AtomicInteger rewritten = new AtomicInteger();
        AtomicInteger scanned = new AtomicInteger();

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".class"))
                    .forEach(path -> {
                        scanned.incrementAndGet();
                        try {
                            byte[] original = Files.readAllBytes(path);
                            byte[] woven = OptChainWeaver.weave(original);
                            if (woven != null) {
                                Files.write(path, woven);
                                rewritten.incrementAndGet();
                                getLog().info("opt-chain:woven " + root.relativize(path));
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to weave " + path, e);
                        }
                    });
        } catch (IOException | RuntimeException e) {
            throw new MojoExecutionException("opt-chain weave failed", e);
        }

        getLog().info("opt-chain:weave scanned " + scanned.get()
                + " class file(s), rewrote " + rewritten.get());
    }
}
