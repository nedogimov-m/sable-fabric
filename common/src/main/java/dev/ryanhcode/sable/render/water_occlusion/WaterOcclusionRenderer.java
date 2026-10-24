package dev.ryanhcode.sable.render.water_occlusion;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.render.NativeShaderDefaults;
import dev.ryanhcode.sable.render.region.SimpleCulledRenderRegion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import foundry.veil.api.client.render.gpu.GpuFramebuffer;
import foundry.veil.api.client.render.gpu.GpuShaderProgram;
import foundry.veil.api.client.render.gpu.GpuShaderSource;
import foundry.veil.api.client.render.gpu.GpuUniformBuffer;
import foundry.veil.api.client.render.vertex.VertexArray;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/** Copied extraction inputs and explicit native entry/exit depth passes. */
@ApiStatus.Internal
public class WaterOcclusionRenderer implements AutoCloseable {
    public static final String PARAMETERS = "SableWaterOcclusion";
    public static final GpuUniformBuffer.Layout PARAMETER_LAYOUT = GpuUniformBuffer.layoutBuilder()
            .field("SableScreenSize", GpuUniformBuffer.ValueType.VEC2)
            .field("SableWaterOcclusionEnabled", GpuUniformBuffer.ValueType.FLOAT).build();
    private final Set<SimpleCulledRenderRegion> regions = new ObjectOpenHashSet<>();
    private Level level;
    private WaterMaskPass maskPass;
    private GpuShaderSource sources;
    private GpuUniformBuffer parameters;
    private Targets targets;
    private Bindings bindings;
    private NativeShaderDefaults defaults;
    private Bindings disabledBindings;
    private Matrix4fc renderProjection;
    private static boolean enabled;

    public static boolean isEnabled() { return enabled; }
    public static void setIsEnabled(final boolean enabled) { WaterOcclusionRenderer.enabled = enabled; }

    /** Owned by LevelRenderState and released when that state is reset. */
    public record Snapshot(List<SimpleCulledRenderRegion.Snapshot> regions, Matrix4fc projection,
                           boolean cameraInside) implements AutoCloseable {
        public Snapshot { regions = List.copyOf(regions); projection = new Matrix4f(projection); }
        @Override public void close() { this.regions.forEach(SimpleCulledRenderRegion.Snapshot::close); }
    }

    /** Borrowed native resources, valid until the next pre-pass/reload/close. */
    public record Bindings(GpuBufferSlice parameters, GpuTextureView entryDepth,
                           GpuTextureView exitDepth, GpuSampler sampler) { }
    public @Nullable Bindings bindings() { return dev.ryanhcode.sable.render.SableNativeWorldShaders.worldEffectsDisabled() ? this.disabledBindings : this.bindings != null ? this.bindings : this.disabledBindings; }
    /** Native projection after hurt/view bob and portal/nausea effects, copied on the render thread. */
    public void captureRenderProjection(final Matrix4fc projection) { this.renderProjection = new Matrix4f(projection); }

    public @Nullable Snapshot extract(final ClientLevel level, final CameraRenderState camera, final float partialTick) {
        if (!enabled || this.regions.isEmpty()) return null;
        final List<SimpleCulledRenderRegion.Snapshot> snapshots = new ArrayList<>();
        try {
            for (final var region : this.regions)
                snapshots.add(region.extract(level, camera.pos, partialTick, camera.viewRotationMatrix));
            final var container = WaterOcclusionContainer.getContainer(level);
            boolean inside = false;
            if (container != null) for (final var region : container.getRegions()) {
                final var volume = region.getVolume();
                final var plot = Sable.HELPER.getContaining(level, volume.getMinBlockPos());
                final Vec3 local = plot instanceof final ClientSubLevel clientPlot
                        ? clientPlot.renderPose(partialTick).transformPositionInverse(camera.pos) : camera.pos;
                if (volume.getOccupied(Mth.floor(local.x), Mth.floor(local.y), Mth.floor(local.z))) { inside = true; break; }
            }
            return new Snapshot(snapshots, camera.projectionMatrix, inside);
        } catch (final RuntimeException | Error failure) {
            snapshots.forEach(SimpleCulledRenderRegion.Snapshot::close);
            throw failure;
        }
    }

    @Nullable public SimpleCulledRenderRegion addRegion(final Collection<BlockPos> blocks) {
        if (blocks.isEmpty()) return null;
        final var region = new WaterOcclusionRenderRegion(blocks);
        this.regions.add(region);
        return region;
    }
    public void removeRegion(final SimpleCulledRenderRegion region) {
        if (region != null) { this.regions.remove(region); region.free(); }
    }

    /** Runs before vanilla records its frame graph, using this frame's extracted camera. */
    public void renderMasks(final @Nullable Snapshot snapshot, final RenderTarget mainTarget) {
        this.bindings = null;
        if (!enabled || snapshot == null || mainTarget.width <= 0 || mainTarget.height <= 0) {
            this.closeTargets();
            return;
        }
        final var device = RenderSystem.getDevice();
        if (this.maskPass == null) {
            this.sources = new GpuShaderSource(Minecraft.getInstance().getResourceManager());
            this.maskPass = new WaterMaskPass(device, this.sources);
        }
        if (this.parameters == null) this.parameters = new GpuUniformBuffer(device, PARAMETERS, PARAMETER_LAYOUT);
        if (this.targets == null || this.targets.entry.width() != mainTarget.width || this.targets.entry.height() != mainTarget.height) {
            this.closeTargets();
            this.targets = new Targets(mainTarget.width, mainTarget.height);
        }
        final var encoder = device.createCommandEncoder();
        final List<VertexArray.Draw> meshes = new ArrayList<>();
        final List<GpuShaderProgram.Lease> programs = new ArrayList<>();
        try {
            final List<WaterMaskPass.Input> inputs = new ArrayList<>();
            for (final var region : snapshot.regions) {
                final var mesh = region.prepare(encoder);
                meshes.add(mesh);
                inputs.add(new WaterMaskPass.Input(mesh, region.modelView()));
            }
            final Matrix4fc projection = this.renderProjection != null ? this.renderProjection : snapshot.projection;
            programs.add(this.maskPass.render(encoder, this.targets.entry, projection, inputs, 1, snapshot.cameraInside ? 1 : 0));
            programs.add(this.maskPass.render(encoder, this.targets.exit, projection, inputs, -1, 0));
            this.parameters.setVector("SableScreenSize", mainTarget.width, mainTarget.height);
            this.parameters.setFloat("SableWaterOcclusionEnabled", 1);
            final var slice = encoder.transientMemory().uploadGpu(this.parameters.snapshot().bytes(),
                    device.getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM);
            encoder.submit();
            this.bindings = new Bindings(slice, this.targets.entry.depth().view(), this.targets.exit.depth().view(), this.targets.sampler);
        } finally {
            programs.forEach(GpuShaderProgram.Lease::close);
            meshes.forEach(VertexArray.Draw::close);
        }
    }

    private static final class Targets implements AutoCloseable {
        final GpuFramebuffer entry, exit;
        final GpuSampler sampler;
        Targets(final int width, final int height) {
            final var device = RenderSystem.getDevice();
            this.entry = GpuFramebuffer.builder("Sable water entry", width, height).depth(GpuFormat.D32_FLOAT).build(device);
            try { this.exit = GpuFramebuffer.builder("Sable water exit", width, height).depth(GpuFormat.D32_FLOAT).build(device); }
            catch (final RuntimeException | Error failure) { this.entry.close(); throw failure; }
            this.sampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
        }
        @Override public void close() { this.entry.close(); this.exit.close(); this.sampler.close(); }
    }
    private void closeTargets() { this.bindings = null; if (this.targets != null) { this.targets.close(); this.targets = null; } }
    public void reload() {
        this.closeTargets();
        this.disabledBindings = null;
        if (this.defaults != null) { this.defaults.close(); this.defaults = null; }
        if (this.maskPass != null) { this.maskPass.close(); this.maskPass = null; }
        if (this.parameters != null) { this.parameters.close(); this.parameters = null; }
        if (this.sources != null) { this.sources.close(); this.sources = null; }
    }
    public void update() {
        this.renderProjection = null;
        final var level = Minecraft.getInstance().level;
        if (level != this.level) {
            this.level = level;
            this.regions.forEach(SimpleCulledRenderRegion::free);
            this.regions.clear();
            this.closeTargets();
        }
        if (this.defaults == null) {
            this.defaults = new NativeShaderDefaults(RenderSystem.getDevice(), PARAMETERS, PARAMETER_LAYOUT, uniform -> {
                uniform.setVector("SableScreenSize", 1, 1);
                uniform.setFloat("SableWaterOcclusionEnabled", 0);
            });
            this.disabledBindings = new Bindings(this.defaults.parameters().buffer().slice(), this.defaults.depth().depth().view(),
                    this.defaults.depth().depth().view(), this.defaults.sampler());
        }
        this.bindings = null;
    }
    @Override public void close() { this.regions.forEach(SimpleCulledRenderRegion::free); this.regions.clear(); this.reload(); }
}
