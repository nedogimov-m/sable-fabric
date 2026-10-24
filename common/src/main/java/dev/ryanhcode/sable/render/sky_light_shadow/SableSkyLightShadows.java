package dev.ryanhcode.sable.render.sky_light_shadow;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.ryanhcode.sable.mixinterface.PlotTerrainRenderStateExtension;
import dev.ryanhcode.sable.render.NativeShaderDefaults;
import foundry.veil.api.client.render.gpu.GpuFramebuffer;
import foundry.veil.api.client.render.gpu.GpuShaderSource;
import foundry.veil.api.client.render.gpu.GpuUniformBuffer;
import foundry.veil.api.client.render.vertex.VertexArray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/** Native depth rendering of the extracted plot terrain; no nested world render or GL state. */
public final class SableSkyLightShadows {
    public static final float SHADOW_VOLUME_SIZE = 128;
    public static final float NEAR_PLANE = .5f;
    public static final int MAP_SIZE = 1024;
    public static final String PARAMETERS = "SableSkyLightShadow";
    public static final GpuUniformBuffer.Layout PARAMETER_LAYOUT = GpuUniformBuffer.layoutBuilder()
            .field("SableShadowOrigin", GpuUniformBuffer.ValueType.VEC3)
            .field("SableShadowVolumeSize", GpuUniformBuffer.ValueType.FLOAT)
            .field("SableShadowTexelSize", GpuUniformBuffer.ValueType.VEC2)
            .field("SableShadowsEnabled", GpuUniformBuffer.ValueType.FLOAT).build();
    private static boolean enabled;
    private static SkyShadowPass pass;
    private static GpuShaderSource sources;
    private static GpuUniformBuffer parameters;
    private static GpuFramebuffer target;
    private static GpuSampler sampler;
    private static Bindings bindings;
    private static NativeShaderDefaults defaults;
    private static Bindings disabledBindings;
    private SableSkyLightShadows() { }
    public static boolean isEnabled() { return enabled; }
    public static void setIsEnabled(final boolean value) { enabled = value; }
    /** Borrowed resources, valid for the current level render until next prepass/reload/close. */
    public record Bindings(GpuBufferSlice parameters, GpuTextureView depth, GpuSampler sampler) { }
    public static @Nullable Bindings bindings() { return dev.ryanhcode.sable.render.SableNativeWorldShaders.worldEffectsDisabled() ? disabledBindings : bindings != null ? bindings : disabledBindings; }
    /** Called at GameRenderer.render HEAD, including frames with no level/plot terrain. */
    public static void prepareFrame() {
        if (defaults == null) {
            defaults = new NativeShaderDefaults(RenderSystem.getDevice(), PARAMETERS, PARAMETER_LAYOUT, uniform -> {
                uniform.setVector("SableShadowOrigin", 0, 0, 0);
                uniform.setFloat("SableShadowVolumeSize", SHADOW_VOLUME_SIZE);
                uniform.setVector("SableShadowTexelSize", 1, 1);
                uniform.setFloat("SableShadowsEnabled", 0);
            });
            disabledBindings = new Bindings(defaults.parameters().buffer().slice(), defaults.depth().depth().view(), defaults.sampler());
        }
        bindings = null;
    }
    public static @Nullable GpuFramebuffer getShadowsFramebuffer() { return target; }

    /** Called at LevelRenderer.render HEAD, before vanilla records its frame graph. */
    public static void renderShadowMap(final LevelRenderState state) {
        bindings = null;
        final var terrain = ((PlotTerrainRenderStateExtension) state).sable$getPlotTerrain();
        if (!enabled || terrain.isEmpty()) { closeTarget(); return; }
        final var client = Minecraft.getInstance();
        final var device = RenderSystem.getDevice();
        if (pass == null) {
            sources = new GpuShaderSource(client.getResourceManager());
            pass = new SkyShadowPass(device, sources);
            parameters = new GpuUniformBuffer(device, PARAMETERS, PARAMETER_LAYOUT);
        }
        if (target == null) {
            target = GpuFramebuffer.builder("Sable plot sky shadows", MAP_SIZE, MAP_SIZE).depth(GpuFormat.D32_FLOAT).build(device);
            sampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
        }
        final Vec3 camera = state.cameraRenderState.pos;
        final Vec3 shadowOrigin = new Vec3(Math.floor(camera.x), camera.y + SHADOW_VOLUME_SIZE / 2, Math.floor(camera.z));
        final Vec3 offset = shadowOrigin.subtract(camera);
        // JOML zero-to-one projection with reversed near/far matches native GREATER_OR_EQUAL depth.
        final var projection = new Matrix4f().setOrtho(-SHADOW_VOLUME_SIZE, SHADOW_VOLUME_SIZE,
                -SHADOW_VOLUME_SIZE, SHADOW_VOLUME_SIZE, SHADOW_VOLUME_SIZE, NEAR_PLANE, true);
        final var view = new Matrix4f().rotationX((float) (Math.PI / 2)).translate((float) -offset.x, (float) -offset.y, (float) -offset.z);
        final var encoder = device.createCommandEncoder();
        final List<VertexArray> owners = new ArrayList<>();
        final List<VertexArray.Draw> meshes = new ArrayList<>();
        try {
            final List<SkyShadowPass.Input> inputs = new ArrayList<>();
            for (final var section : terrain) {
                final var modelView = new Matrix4f(view).translate((float) section.cameraRelativeOrigin().x,
                        (float) section.cameraRelativeOrigin().y, (float) section.cameraRelativeOrigin().z)
                        .rotate(section.orientation()).scale(section.scale());
                for (final var layer : section.mesh().layers().entrySet()) {
                    if (layer.getKey() == ChunkSectionLayer.TRANSLUCENT || layer.getValue().isEmpty()) continue;
                    final var mesh = VertexArray.create(device, "Sable shadow section");
                    owners.add(mesh);
                    try (final var memory = new ByteBufferBuilder(Math.max(1024, layer.getValue().size() * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize()))) {
                        final var vertices = new BufferBuilder(memory, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                        for (final var vertex : layer.getValue()) vertices.addVertex(vertex.x(), vertex.y(), vertex.z())
                                .setUv(vertex.u(), vertex.v()).setColor(vertex.color());
                        mesh.upload(encoder, vertices.buildOrThrow(), VertexArray.DrawUsage.STATIC);
                    }
                    final var draw = mesh.retain();
                    meshes.add(draw);
                    inputs.add(new SkyShadowPass.Input(draw, modelView, layer.getKey() == ChunkSectionLayer.CUTOUT ? .5f : 0));
                }
            }
            final var atlas = client.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            try (final var program = pass.render(encoder, target, projection, inputs, atlas.getTextureView(), atlas.getSampler())) {
                parameters.setVector("SableShadowOrigin", (float) offset.x, (float) offset.y, (float) offset.z);
                parameters.setFloat("SableShadowVolumeSize", SHADOW_VOLUME_SIZE);
                parameters.setVector("SableShadowTexelSize", 1.0f / MAP_SIZE, 1.0f / MAP_SIZE);
                parameters.setFloat("SableShadowsEnabled", 1);
                final var slice = encoder.transientMemory().uploadGpu(parameters.snapshot().bytes(),
                        device.getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM);
                encoder.submit();
                bindings = new Bindings(slice, target.depth().view(), sampler);
            }
        } finally {
            meshes.forEach(VertexArray.Draw::close);
            owners.forEach(VertexArray::close);
        }
    }
    private static void closeTarget() {
        bindings = null;
        if (target != null) { target.close(); target = null; }
        if (sampler != null) { sampler.close(); sampler = null; }
    }
    public static void reload() {
        closeTarget();
        disabledBindings = null;
        if (defaults != null) { defaults.close(); defaults = null; }
        if (pass != null) { pass.close(); pass = null; }
        if (parameters != null) { parameters.close(); parameters = null; }
        if (sources != null) { sources.close(); sources = null; }
    }
}
