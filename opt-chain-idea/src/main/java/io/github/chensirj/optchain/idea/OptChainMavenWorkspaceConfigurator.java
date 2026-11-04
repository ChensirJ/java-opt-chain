package io.github.chensirj.optchain.idea;

import io.github.chensirj.optchain.plugin.OptChainLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.MavenWorkspaceConfigurator;
import org.jetbrains.idea.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * During Maven import, exclude the preprocess mirror so it is not a source root
 * next to {@code src/main/java}.
 */
public final class OptChainMavenWorkspaceConfigurator implements MavenWorkspaceConfigurator {

    @Override
    public @NotNull Stream<String> getFoldersToExclude(@NotNull FoldersContext context) {
        MavenProject mavenProject = context.getMavenProject();
        if (mavenProject.findPlugin("io.github.chensirj", "opt-chain-maven-plugin") == null) {
            return Stream.empty();
        }
        String buildDirectory = mavenProject.getBuildDirectory();
        if (buildDirectory == null || buildDirectory.isBlank()) {
            return Stream.empty();
        }
        Path build = Path.of(buildDirectory);
        List<String> folders = new ArrayList<>();
        folders.add(build.resolve(OptChainLayout.MAIN_DIR).toString());
        folders.add(build.resolve(OptChainLayout.TEST_DIR).toString());
        folders.add(build.resolve(OptChainLayout.LEGACY_MAIN_DIR).toString());
        folders.add(build.resolve(OptChainLayout.LEGACY_TEST_DIR).toString());
        return folders.stream();
    }
}
