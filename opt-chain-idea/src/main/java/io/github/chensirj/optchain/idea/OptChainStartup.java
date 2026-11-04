package io.github.chensirj.optchain.idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Unmarks a Maven opt-chain mirror that an earlier import registered as a source root. */
public final class OptChainStartup implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        OptChainSourceRootCleaner.schedule(project);
        return Unit.INSTANCE;
    }
}
