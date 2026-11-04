package io.github.chensirj.optchain.idea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.chensirj.optchain.plugin.OptChainLayout;
import org.jetbrains.annotations.NotNull;

/**
 * Drops opt-chain mirror directories from the module source roots.
 * Authoring {@code src/main/java} (with {@code ?.}) stays. The mirror is only an input to Maven javac.
 */
public final class OptChainSourceRootCleaner {

    private OptChainSourceRootCleaner() {
    }

    public static void schedule(@NotNull Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            WriteAction.run(() -> clean(project));
        });
    }

    public static void clean(@NotNull Project project) {
        if (project.isDisposed()) {
            return;
        }
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
            boolean changed = false;
            try {
                for (ContentEntry entry : model.getContentEntries()) {
                    for (SourceFolder folder : entry.getSourceFolders()) {
                        if (isMirror(folder)) {
                            entry.removeSourceFolder(folder);
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    model.commit();
                } else {
                    model.dispose();
                }
            } catch (RuntimeException ex) {
                model.dispose();
                throw ex;
            }
        }
    }

    static boolean isMirror(@NotNull SourceFolder folder) {
        VirtualFile file = folder.getFile();
        if (file != null && OptChainLayout.isOptChainMirrorPath(file.getPath())) {
            return true;
        }
        String url = folder.getUrl();
        return OptChainLayout.isOptChainMirrorPath(url);
    }
}
