package dev.ryanhcode.sable.mixinhelpers.loaded_chunk_debug;

import dev.ryanhcode.sable.mixinterface.loaded_chunk_debug.DebugChunkProviderAttachments;
import dev.ryanhcode.sable.mixinterface.loaded_chunk_debug.DebugLevelChunkExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class SableChunkDebugRenderer {
    public static void render(final double cameraY) {
        final Minecraft minecraft = Minecraft.getInstance();
        final var level = minecraft.level;
        final var entity = minecraft.gameRenderer.mainCamera().entity();
        if (level == null || entity == null) return;
        final long time = System.currentTimeMillis();
        final int minY = level.getMinY();
        final int maxY = level.getMaxY() + 1;
        for (final var chunk : ((DebugChunkProviderAttachments) level.getChunkSource()).sable$loadedChunks()) {
            final var pos = chunk.getPos();
            final float age = Mth.clamp(time - ((DebugLevelChunkExtension) chunk).sable$getLastUpdate(), 0, 1000) / 1000.0f;
            final int color = 0xff000000 | (int) ((1 - age) * 255) << 16 | (int) (age * 255) << 8;
            final double inset = .1 * (1 - age);
            square(pos.getMinBlockX(), minY + (cameraY > minY ? inset : -inset), pos.getMinBlockZ(), color);
            square(pos.getMinBlockX(), maxY + (cameraY < maxY ? -inset : inset), pos.getMinBlockZ(), color);
        }
        final var cameraChunk = entity.chunkPosition();
        final int x = cameraChunk.getMinBlockX();
        final int z = cameraChunk.getMinBlockZ();
        for (int dx = 0; dx <= 16; dx += 16) {
            for (int dz = 0; dz <= 16; dz += 16) {
                Gizmos.line(new Vec3(x + dx, minY, z + dz), new Vec3(x + dx, maxY, z + dz), 0xffffff00);
            }
        }
        for (int y = Math.floorDiv(minY, 16) * 16; y <= maxY + 1; y += 16) square(x, y, z, 0xff0000ff);
    }

    private static void square(final double x, final double y, final double z, final int color) {
        final Vec3 a = new Vec3(x, y, z);
        final Vec3 b = new Vec3(x + 16, y, z);
        final Vec3 c = new Vec3(x + 16, y, z + 16);
        final Vec3 d = new Vec3(x, y, z + 16);
        Gizmos.line(a, b, color);
        Gizmos.line(b, c, color);
        Gizmos.line(c, d, color);
        Gizmos.line(d, a, color);
    }
}
