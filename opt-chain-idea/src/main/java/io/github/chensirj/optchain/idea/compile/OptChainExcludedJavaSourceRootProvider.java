package io.github.chensirj.optchain.idea.compile;

import io.github.chensirj.optchain.plugin.OptChainLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.util.JpsPathUtil;

import java.nio.file.Path;

/**
 * Keeps IDEA's javac off sources that contain {@code ?.} (and off the Maven mirror of
 * those sources). {@link OptChainJpsBuilder} compiles the authoring tree instead.
 */
public final class OptChainExcludedJavaSourceRootProvider extends ExcludedJavaSourceRootProvider {

    @Override
    public boolean isExcludedFromCompilation(@NotNull JpsModule module, @NotNull JpsModuleSourceRoot root) {
        boolean tests = JavaSourceRootType.TEST_SOURCE.equals(root.getRootType());
        boolean main = JavaSourceRootType.SOURCE.equals(root.getRootType());
        if (!tests && !main) {
            return false;
        }
        ListSnapshot snapshot;
        try {
            snapshot = ListSnapshot.of(module, tests);
        } catch (Exception ex) {
            return false;
        }
        if (!snapshot.hasOptChain) {
            return false;
        }
        Path path = JpsPathUtil.urlToFile(root.getUrl()).toPath().toAbsolutePath().normalize();
        if (OptChainLayout.isOptChainMirrorPath(path.toString())) {
            return true;
        }
        for (Path authoring : snapshot.authoring) {
            if (authoring.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private record ListSnapshot(java.util.List<Path> authoring, boolean hasOptChain) {
        static ListSnapshot of(JpsModule module, boolean tests) throws java.io.IOException {
            java.util.List<Path> authoring = OptChainJpsSources.authoringRoots(module, tests);
            return new ListSnapshot(authoring, OptChainJpsSources.anyTreeContainsOptChain(authoring));
        }
    }
}
