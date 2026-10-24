package dev.ryanhcode.sable.render.region;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import foundry.veil.api.client.render.vertex.VertexArray;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

import java.util.Collection;
import java.util.List;

/** Explicitly uploaded native voxel mesh; its caller owns the render pass and draw lease. */
@ApiStatus.Internal
public abstract class SimpleCulledRenderRegion {
    private List<BlockPos> unbuiltData;
    private VertexArray buffer;
    private final Vec3 origin;
    private final int gridSize;
    private boolean closed;
    private int snapshots;

    public SimpleCulledRenderRegion(final Collection<BlockPos> blocks) {
        if (blocks.isEmpty()) throw new IllegalArgumentException("An occlusion region needs blocks");
        this.unbuiltData = blocks.stream().map(BlockPos::immutable).toList();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (final BlockPos block : this.unbuiltData) {
            minX = Math.min(minX, block.getX()); minY = Math.min(minY, block.getY()); minZ = Math.min(minZ, block.getZ());
            maxX = Math.max(maxX, block.getX()); maxY = Math.max(maxY, block.getY()); maxZ = Math.max(maxZ, block.getZ());
        }
        this.origin = new Vec3(minX, minY, minZ);
        this.gridSize = Math.max(maxX - minX + 1, Math.max(maxY - minY + 1, maxZ - minZ + 1));
    }

    /** Retains the CPU mesh owner until submission or render-state reset. */
    public final class Snapshot implements AutoCloseable {
        private final Matrix4fc modelView;
        private boolean released;
        private Snapshot(final Matrix4fc modelView) {
            this.modelView = new Matrix4f(modelView);
            SimpleCulledRenderRegion.this.snapshots++;
        }
        public Matrix4fc modelView() { return this.modelView; }
        public VertexArray.Draw prepare(final CommandEncoder encoder) {
            if (this.released) throw new IllegalStateException("Occlusion snapshot is closed");
            return SimpleCulledRenderRegion.this.prepareRetained(encoder);
        }
        @Override public void close() {
            if (this.released) return;
            this.released = true;
            if (--SimpleCulledRenderRegion.this.snapshots == 0 && SimpleCulledRenderRegion.this.closed)
                SimpleCulledRenderRegion.this.dispose();
        }
    }

    /** Called during world extraction; submission only receives the copied matrix and mesh owner. */
    public Snapshot extract(final ClientLevel level, final Vec3 cameraPosition, final float partialTick, final Matrix4fc viewRotation) {
        if (this.closed) throw new IllegalStateException("Occlusion region is closed");
        final var subLevel = Sable.HELPER.getContaining(level, this.origin);
        final PoseStack pose = new PoseStack();
        if (subLevel instanceof final ClientSubLevel clientPlot) {
            final var frame = clientPlot.renderPose(partialTick);
            pose.translate(frame.transformPosition(this.origin).subtract(cameraPosition));
            pose.rotate(new Quaternionf(frame.orientation()));
            pose.scale((float) frame.scale().x(), (float) frame.scale().y(), (float) frame.scale().z());
        } else {
            pose.translate(this.origin.subtract(cameraPosition));
        }
        return new Snapshot(new Matrix4f(viewRotation).mul(pose.last().pose()));
    }

    public VertexArray.Draw prepare(final CommandEncoder encoder) {
        if (this.closed) throw new IllegalStateException("Occlusion region is closed");
        return this.prepareRetained(encoder);
    }

    private VertexArray.Draw prepareRetained(final CommandEncoder encoder) {
        if (this.buffer == null) this.build(encoder);
        return this.buffer.retain();
    }

    private void build(final CommandEncoder encoder) {
        final SimpleCulledRenderRegionBuilder builder = this.createMeshBuilder(this.gridSize);
        for (final BlockPos block : this.unbuiltData) builder.add(block.getX() - (int) this.origin.x,
                block.getY() - (int) this.origin.y, block.getZ() - (int) this.origin.z);
        builder.buildNoGreedy();
        final VertexArray replacement = VertexArray.create(RenderSystem.getDevice(), "Sable occlusion region");
        try (final ByteBufferBuilder memory = new ByteBufferBuilder(16384)) {
            final BufferBuilder vertices = new BufferBuilder(memory, PrimitiveTopology.QUADS, this.getVertexFormat());
            builder.render(new Matrix4f(), vertices);
            replacement.upload(encoder, vertices.buildOrThrow(), VertexArray.DrawUsage.STATIC);
        } catch (final RuntimeException | Error failure) {
            replacement.close();
            throw failure;
        }
        this.buffer = replacement;
        this.unbuiltData = List.of();
    }

    public Vec3 getOrigin() { return this.origin; }
    public abstract SimpleCulledRenderRegionBuilder createMeshBuilder(int gridSize);
    public abstract VertexFormat getVertexFormat();
    public void free() {
        if (this.closed) return;
        this.closed = true;
        if (this.snapshots == 0) this.dispose();
    }
    private void dispose() {
        this.unbuiltData = List.of();
        if (this.buffer != null) this.buffer.close();
    }
}
