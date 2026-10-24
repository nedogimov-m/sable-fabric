package dev.ryanhcode.sable.fabric;

import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.SableServerConfig;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/** Global common settings and world-owned server settings, using Fabric events. */
public final class FabricSableConfig {
    public static void initialize() {
        SableConfig.SPEC.load(FabricLoader.getInstance().getConfigDir().resolve("sable-common.toml"));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            var target = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig/sable-server.toml");
            var template = FabricLoader.getInstance().getGameDir().resolve("defaultconfigs/sable-server.toml");
            try {
                if (!Files.exists(target) && Files.isRegularFile(template)) {
                    Files.createDirectories(target.getParent()); Files.copy(template, target);
                }
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
            SableServerConfig.SPEC.load(target);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(FabricSableConfig::applyPhysics);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            SableConfig.SPEC.reloadIfChanged();
            if (SableServerConfig.SPEC.reloadIfChanged()) applyPhysics(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SableServerConfig.SPEC.reset());
    }

    private static void applyPhysics(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            var container = SubLevelContainer.getContainer(level);
            if (container == null) continue;
            var physics = container.physicsSystem();
            physics.getConfig().updateFromConfig(); physics.onConfigUpdated();
        }
    }
    private FabricSableConfig() {}
}
