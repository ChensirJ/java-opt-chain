package io.github.chensirj.optchain.idea.compile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;

import java.util.List;

public final class OptChainJpsBuilderService extends BuilderService {

    @Override
    public @NotNull List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
        return List.of(new OptChainJpsBuilder());
    }
}
