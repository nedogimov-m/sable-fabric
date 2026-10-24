package dev.ryanhcode.sable.fabric;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ResourceReloadDelegate implements PreparableReloadListener {
    private final Identifier id;
    private final SimpleJsonResourceReloadListener<?> delegate;

    public ResourceReloadDelegate(final Identifier id, final SimpleJsonResourceReloadListener<?> delegate) {
        this.id = id;
        this.delegate = delegate;
    }

    public Identifier getFabricId() {
        return this.id;
    }

    @Override
    public CompletableFuture<Void> reload(final SharedState state, final Executor backgroundExecutor,
                                          final PreparationBarrier barrier, final Executor gameExecutor) {
        return this.delegate.reload(state, backgroundExecutor, barrier, gameExecutor);
    }
}
