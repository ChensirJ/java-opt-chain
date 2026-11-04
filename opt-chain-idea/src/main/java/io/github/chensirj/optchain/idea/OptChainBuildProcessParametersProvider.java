package io.github.chensirj.optchain.idea;

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts this plugin (and its ASM jars) on the JPS classpath so
 * {@code OptChainJpsBuilderService} is loaded in the build process.
 */
public final class OptChainBuildProcessParametersProvider extends BuildProcessParametersProvider {

    @Override
    public @NotNull Iterable<String> getClassPath() {
        List<String> jars = new ArrayList<>();
        String self = PathManager.getJarPathForClass(OptChainBuildProcessParametersProvider.class);
        if (self == null) {
            return jars;
        }
        jars.add(self);
        Path path = Path.of(self);
        Path lib = Files.isDirectory(path) ? path.getParent() : path.getParent();
        if (lib != null && Files.isDirectory(lib)) {
            try (var stream = Files.list(lib)) {
                stream.filter(candidate -> {
                    String name = candidate.getFileName().toString();
                    return name.startsWith("asm-") && name.endsWith(".jar");
                }).forEach(candidate -> jars.add(candidate.toString()));
            } catch (Exception ignored) {
                // the plugin jar already shades nothing; ASM is also resolved from the plugin lib when packaged
            }
        }
        return jars;
    }

    @Override
    public boolean isProcessPreloadingEnabled() {
        // A preloaded build process would miss jars added by this provider.
        return false;
    }
}
