package dev.ryanhcode.sable.fabric.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.render.sky_light_shadow.SableSkyLightShadows;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class SableFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SableClientConfig.SPEC.load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("sable-client.toml"));
        SableClient.init();
        SableClientConfig.onUpdate(false);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            SableClient.WATER_OCCLUSION_RENDERER.close();
            SableSkyLightShadows.reload();
        });
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Sable.sablePath("water_occlusion"),
                (state, backgroundExecutor, barrier, gameExecutor) -> barrier.wait(null)
                        .thenRunAsync(() -> { SableClient.WATER_OCCLUSION_RENDERER.reload(); SableSkyLightShadows.reload(); }, gameExecutor));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FloatingBlockMaterialDataHandler.clearMaterials());
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Sable.sablePath("sub_level_renderer"),
                (state, backgroundExecutor, barrier, gameExecutor) ->
                        SubLevelRenderer.getDispatcher().reload(state, backgroundExecutor, barrier, gameExecutor));

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++this.configPollTicks % 20 != 0) return;
            SableConfig.SPEC.reloadIfChanged();
            if (SableClientConfig.SPEC.reloadIfChanged()) SableClientConfig.onUpdate(true);
        });
    }

    private int configPollTicks;
}
