package io.github.chensirj.optchain.idea.compile;

import io.github.chensirj.optchain.plugin.OptChainLayout;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Shared root selection for the JPS builder and the Java-builder exclude filter. */
public final class OptChainJpsSources {

    private OptChainJpsSources() {
    }

    public static List<Path> authoringRoots(JpsModule module, boolean tests) {
        List<Path> roots = new ArrayList<>();
        for (JpsModuleSourceRoot root : module.getSourceRoots()) {
            if (!isJavaRoot(root, tests)) {
                continue;
            }
            Path path = JpsPathUtil.urlToFile(root.getUrl()).toPath().toAbsolutePath().normalize();
            if (OptChainLayout.isOptChainMirrorPath(path.toString())) {
                continue;
            }
            roots.add(path);
        }
        return roots;
    }

    public static boolean isJavaRoot(JpsModuleSourceRoot root, boolean tests) {
        if (tests) {
            return JavaSourceRootType.TEST_SOURCE.equals(root.getRootType());
        }
        return JavaSourceRootType.SOURCE.equals(root.getRootType());
    }

    public static boolean anyTreeContainsOptChain(List<Path> roots) throws IOException {
        for (Path root : roots) {
            if (OptChainJavacPipeline.treeContainsOptChain(root)) {
                return true;
            }
        }
        return false;
    }
}
