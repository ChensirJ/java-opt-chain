package io.github.chensirj.optchain.idea.compile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.BuilderCategory;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.LanguageLevel;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Compiles modules that contain {@code ?.} before the regular Java builder.
 * Those roots are excluded from javac (see {@link OptChainExcludedJavaSourceRootProvider});
 * this builder feeds javac the Maven-equivalent {@code OptChainOps.opt} rewrite and weaves
 * the class files so Run/Debug short-circuits null receivers.
 */
public final class OptChainJpsBuilder extends ModuleLevelBuilder {

    static final String NAME = "Opt Chain";

    public OptChainJpsBuilder() {
        super(BuilderCategory.SOURCE_PROCESSOR);
    }

    @Override
    public @NotNull String getPresentableName() {
        return NAME;
    }

    @Override
    public @NotNull List<String> getCompilableFileExtensions() {
        // Do not claim ".java". JavaBuilder still owns roots that have no ?. .
        return Collections.emptyList();
    }

    @Override
    public ExitCode build(
            @NotNull CompileContext context,
            @NotNull ModuleChunk chunk,
            @NotNull DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
            @NotNull OutputConsumer outputConsumer
    ) throws ProjectBuildException, IOException {
        boolean did = false;
        for (ModuleBuildTarget target : chunk.getTargets()) {
            switch (compileTarget(context, chunk, target)) {
                case FAILED -> {
                    return ExitCode.ABORT;
                }
                case COMPILED -> did = true;
                case NOTHING -> {
                    // this target has no ?.
                }
            }
        }
        return did ? ExitCode.OK : ExitCode.NOTHING_DONE;
    }

    private enum TargetOutcome {
        NOTHING, COMPILED, FAILED
    }

    private static TargetOutcome compileTarget(CompileContext context, ModuleChunk chunk, ModuleBuildTarget target)
            throws ProjectBuildException, IOException {
        JpsModule module = target.getModule();
        boolean tests = target.isTests();
        List<Path> roots = OptChainJpsSources.authoringRoots(module, tests);
        if (!OptChainJpsSources.anyTreeContainsOptChain(roots)) {
            return TargetOutcome.NOTHING;
        }
        File output = ProjectPaths.getModuleOutputDir(module, tests);
        if (output == null) {
            context.processMessage(new CompilerMessage(
                    NAME,
                    BuildMessage.Kind.ERROR,
                    "Opt Chain has no output directory for module " + module.getName()));
            throw new ProjectBuildException("Opt Chain has no output directory for " + module.getName());
        }
        output.mkdirs();

        String javac = resolveJavac(module);
        String release = resolveRelease(module);
        List<Path> classpath = toPaths(ProjectPaths.getCompilationClasspath(chunk, true));
        // The module output is not on the classpath (excludeSelf). Put dependency module
        // outputs there via ProjectPaths. Also add this module's main output when compiling tests.
        if (tests) {
            File mainOut = ProjectPaths.getModuleOutputDir(module, false);
            if (mainOut != null) {
                classpath.add(mainOut.toPath());
            }
        }

        Path scratch = Files.createTempDirectory("opt-chain-idea-");
        try {
            OptChainJavacPipeline.Result result = OptChainJavacPipeline.compile(
                    roots,
                    classpath,
                    output.toPath(),
                    scratch,
                    javac,
                    release);
            if (!result.success()) {
                for (OptChainJavacPipeline.Diagnostic error : result.errors()) {
                    String path = error.sourcePath().isEmpty() ? null : error.sourcePath();
                    context.processMessage(new CompilerMessage(
                            NAME,
                            BuildMessage.Kind.ERROR,
                            error.message(),
                            path,
                            -1,
                            -1,
                            -1,
                            error.line(),
                            1));
                }
                return TargetOutcome.FAILED;
            }
            if (result.rewrittenFiles() > 0) {
                context.processMessage(new CompilerMessage(
                        NAME,
                        BuildMessage.Kind.INFO,
                        "Opt Chain compiled " + module.getName()
                                + (tests ? " tests" : "")
                                + " (" + result.summary() + ")"));
            }
            return result.rewrittenFiles() > 0 ? TargetOutcome.COMPILED : TargetOutcome.NOTHING;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProjectBuildException("Opt Chain compile interrupted", ex);
        } finally {
            deleteQuietly(scratch);
        }
    }

    private static String resolveJavac(JpsModule module) {
        JpsSdk<JpsDummyElement> sdk = module.getSdk(JpsJavaSdkType.INSTANCE);
        if (sdk != null && sdk.getHomePath() != null) {
            File home = new File(sdk.getHomePath());
            File javac = new File(new File(home, "bin"), isWindows() ? "javac.exe" : "javac");
            if (javac.isFile()) {
                return javac.getAbsolutePath();
            }
        }
        File bundled = new File(new File(System.getProperty("java.home", ""), "bin"),
                isWindows() ? "javac.exe" : "javac");
        if (bundled.isFile()) {
            return bundled.getAbsolutePath();
        }
        return isWindows() ? "javac.exe" : "javac";
    }

    private static String resolveRelease(JpsModule module) {
        LanguageLevel level = JpsJavaExtensionService.getInstance().getLanguageLevel(module);
        if (level == null) {
            return "17";
        }
        return OptChainJavacPipeline.releaseOf(level.name());
    }

    private static List<Path> toPaths(Collection<File> files) {
        List<Path> paths = new ArrayList<>();
        if (files == null) {
            return paths;
        }
        for (File file : files) {
            if (file != null) {
                paths.add(file.toPath());
            }
        }
        return paths;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void deleteQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(java.util.Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // scratch is under java.io.tmpdir; a leftover does not affect the next build
        }
    }
}
