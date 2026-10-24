package dev.ryanhcode.sable.render;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import dev.ryanhcode.sable.render.sky_light_shadow.SableSkyLightShadows;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import foundry.veil.api.client.render.gpu.*;
import foundry.veil.api.client.render.shader.NativePipelineBridge;
import foundry.veil.api.client.render.vertex.VertexArray;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.file.*;
import java.util.*;

/** Real production bridge/preprocessors/default lifecycle; isolated from full Sable startup. */
public final class NativeWorldShaderGpuCheck implements ModInitializer, ClientModInitializer {
    private static final WaterOcclusionRenderer WATER = new WaterOcclusionRenderer();
    private static WaterOcclusionRenderer.Bindings waterOverride;
    private static SableSkyLightShadows.Bindings shadowOverride;
    private boolean ran;
    private int ticks;
    @Override public void onInitialize() {
        SableNativeWorldShaders.register(() -> waterOverride != null ? waterOverride : WATER.bindings(),
                () -> shadowOverride != null ? shadowOverride : SableSkyLightShadows.bindings());
    }
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (this.ran || ++this.ticks < 5 || !client.isGameLoadFinished()) return;
            this.ran = true;
            Map<String, Object> report = new LinkedHashMap<>();
            var device = RenderSystem.getDevice();
            report.put("backend", device.getDeviceInfo().backendName()); report.put("device", device.getDeviceInfo().name());
            try {
                this.run(client);
                report.put("status", "passed");
                report.put("checks", List.of("real eager vanilla terrain/block/OIT compilation with both production Sable processors",
                        "real disabled/default water and sky resource preparation before native passes",
                        "native reverse-depth water discard and outside-volume visibility with exact RGBA readback",
                        "native shadow lightmap reduction and independent subsequent disabled bindings",
                        "production reload/free and next-frame disabled-resource recreation"));
            } catch (Throwable failure) { failure.printStackTrace(); report.put("status", "failed"); report.put("error", failure.toString()); }
            finally {
                WATER.close(); SableSkyLightShadows.reload();
                try { Files.writeString(Path.of(System.getProperty("sable.world.shaders.report")), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
                catch (Exception failure) { throw new RuntimeException(failure); }
                client.stop();
            }
        });
    }
    private void run(Minecraft client) {
        var device = RenderSystem.getDevice();
        check(device.getDeviceInfo().backendName().toLowerCase(Locale.ROOT).contains("vulkan"), "Actual native Vulkan device required");
        var compiled = RenderSystem.getCompiledPipeline(RenderPipelines.TRANSLUCENT_BLOCK);
        var actual = NativePipelineBridge.actualDefinition(compiled).orElseThrow();
        var bindings = BindGroupLayout.flattenUniforms(actual.getBindGroupLayouts());
        check(bindings.stream().anyMatch(binding -> binding.name().equals(WaterOcclusionRenderer.PARAMETERS))
                && bindings.stream().anyMatch(binding -> binding.name().equals(SableSkyLightShadows.PARAMETERS)), "Both real production shader extensions must augment the same vanilla pipeline");
        check(NativePipelineBridge.actualDefinition(RenderSystem.getCompiledPipeline(RenderPipelines.OIT_TERRAIN.accumulatePipeline())).isPresent(), "Actual vanilla OIT eager cache must use production Sable processors");
        WATER.update(); SableSkyLightShadows.prepareFrame();
        WATER.renderMasks(null, client.gameRenderer.mainRenderTarget());
        check(WATER.bindings() != null && SableSkyLightShadows.bindings() != null, "Disabled/no-region frames must prepare valid production resources");
        var transformLayout = GpuUniformBuffer.layoutBuilder().field("ModelViewMat", GpuUniformBuffer.ValueType.MAT4)
                .field("TextureMat", GpuUniformBuffer.ValueType.MAT4).field("ColorModulator", GpuUniformBuffer.ValueType.VEC4).field("ModelOffset", GpuUniformBuffer.ValueType.VEC3).build();
        var fogLayout = GpuUniformBuffer.layoutBuilder().field("FogColor", GpuUniformBuffer.ValueType.VEC4)
                .field("FogEnvironmentalStart", GpuUniformBuffer.ValueType.FLOAT).field("FogEnvironmentalEnd", GpuUniformBuffer.ValueType.FLOAT)
                .field("FogRenderDistanceStart", GpuUniformBuffer.ValueType.FLOAT).field("FogRenderDistanceEnd", GpuUniformBuffer.ValueType.FLOAT)
                .field("FogSkyEnd", GpuUniformBuffer.ValueType.FLOAT).field("FogCloudsEnd", GpuUniformBuffer.ValueType.FLOAT).build();
        try (var mesh = VertexArray.create(device, "actual block shader quad"); var memory = new ByteBufferBuilder(2048);
             var transform = new GpuUniformBuffer(device, "native world transform", transformLayout);
             var projection = new GpuUniformBuffer(device, "native world projection", GpuUniformBuffer.layoutBuilder().field("ProjMat", GpuUniformBuffer.ValueType.MAT4).build());
             var fog = new GpuUniformBuffer(device, "native world fog", fogLayout);
             var water = new GpuUniformBuffer(device, "active water parameters", WaterOcclusionRenderer.PARAMETER_LAYOUT);
             var shadows = new GpuUniformBuffer(device, "active shadow parameters", SableSkyLightShadows.PARAMETER_LAYOUT);
             var lightmap = GpuFramebuffer.builder("native test lightmap", 16, 16).color(GpuFormat.RGBA8_UNORM).build(device);
             var atlas = GpuFramebuffer.builder("native test white atlas", 1, 1).color(GpuFormat.RGBA8_UNORM).build(device);
             var entry = GpuFramebuffer.builder("water entry", 2, 2).depth(GpuFormat.D32_FLOAT).build(device);
             var exit = GpuFramebuffer.builder("water exit", 2, 2).depth(GpuFormat.D32_FLOAT).build(device);
             var shadowDepth = GpuFramebuffer.builder("sky depth", 2, 2).depth(GpuFormat.D32_FLOAT).build(device);
             var output = GpuFramebuffer.builder("production world shader readback", 2, 2).color(GpuFormat.RGBA8_UNORM).depth(GpuFormat.D32_FLOAT).build(device);
             var pixels = new NativeImage(16, 16, true)) {
            var sampler = WATER.bindings().sampler();
            var encoder = device.createCommandEncoder();
            var vertices = new BufferBuilder(memory, PrimitiveTopology.QUADS, DefaultVertexFormat.BLOCK);
            vertices.addVertex(-1, -1, .5f).setColor(-1).setUv(0, 0).setUv2(0, 240);
            vertices.addVertex(1, -1, .5f).setColor(-1).setUv(1, 0).setUv2(0, 240);
            vertices.addVertex(1, 1, .5f).setColor(-1).setUv(1, 1).setUv2(0, 240);
            vertices.addVertex(-1, 1, .5f).setColor(-1).setUv(0, 1).setUv2(0, 240);
            mesh.upload(encoder, vertices.buildOrThrow(), VertexArray.DrawUsage.STATIC);
            transform.setMatrix("ModelViewMat", new Matrix4f()); transform.setMatrix("TextureMat", new Matrix4f()); transform.setVector("ColorModulator", 1, 1, 1, 1); transform.setVector("ModelOffset", 0, 0, 0);
            projection.setMatrix("ProjMat", new Matrix4f());
            fog.setVector("FogColor", 0, 0, 0, 0);
            for (String name : List.of("FogEnvironmentalStart", "FogRenderDistanceStart")) fog.setFloat(name, 100);
            for (String name : List.of("FogEnvironmentalEnd", "FogRenderDistanceEnd", "FogSkyEnd", "FogCloudsEnd")) fog.setFloat(name, 200);
            water.setVector("SableScreenSize", 2, 2); water.setFloat("SableWaterOcclusionEnabled", 1);
            shadows.setVector("SableShadowOrigin", 0, 32, 0); shadows.setFloat("SableShadowVolumeSize", 128); shadows.setVector("SableShadowTexelSize", .5f, .5f); shadows.setFloat("SableShadowsEnabled", 1);
            transform.upload(encoder); projection.upload(encoder); fog.upload(encoder); water.upload(encoder); shadows.upload(encoder);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) pixels.setPixel(x, y, 0xff000000 | (y * 17) * 0x010101);
            encoder.writeToTexture(lightmap.color(0).texture(), pixels); atlas.clear(encoder, new Vector4f(1), 0);
            entry.clear(encoder, null, .8); exit.clear(encoder, null, .2); shadowDepth.clear(encoder, null, 1); encoder.submit();
            try (var geometry = mesh.retain()) {
                draw(output, compiled, geometry, transform, projection, fog, atlas, lightmap, sampler); assertColor(output, 255, 255, 255, 255);
                waterOverride = new WaterOcclusionRenderer.Bindings(water.buffer().slice(), entry.depth().view(), exit.depth().view(), sampler);
                draw(output, compiled, geometry, transform, projection, fog, atlas, lightmap, sampler); assertColor(output, 0, 0, 0, 0);
                var outside = device.createCommandEncoder(); entry.clear(outside, null, .4); outside.submit();
                draw(output, compiled, geometry, transform, projection, fog, atlas, lightmap, sampler); assertColor(output, 255, 255, 255, 255);
                waterOverride = null;
                shadowOverride = new SableSkyLightShadows.Bindings(shadows.buffer().slice(), shadowDepth.depth().view(), sampler);
                draw(output, compiled, geometry, transform, projection, fog, atlas, lightmap, sampler); assertColor(output, 85, 85, 85, 255);
                shadowOverride = null;
                draw(output, compiled, geometry, transform, projection, fog, atlas, lightmap, sampler); assertColor(output, 255, 255, 255, 255);
                var oldWater = WATER.bindings(); var oldShadow = SableSkyLightShadows.bindings();
                WATER.reload(); SableSkyLightShadows.reload();
                check(oldWater.parameters().buffer().isClosed() && oldWater.entryDepth().isClosed() && oldShadow.depth().isClosed(), "Production reload must free owned disabled resources");
                WATER.update(); SableSkyLightShadows.prepareFrame();
                draw(output, compiled, geometry, transform, projection, fog, atlas, lightmap, WATER.bindings().sampler()); assertColor(output, 255, 255, 255, 255);
            }
        } finally { waterOverride = null; shadowOverride = null; }
    }
    private static void draw(GpuFramebuffer output, CompiledRenderPipeline pipeline, VertexArray.Draw mesh,
                             GpuUniformBuffer transform, GpuUniformBuffer projection, GpuUniformBuffer fog,
                             GpuFramebuffer atlas, GpuFramebuffer lightmap, com.mojang.renderpearl.api.textures.GpuSampler sampler) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        output.clear(encoder, new Vector4f(0), 0);
        try (var pass = output.beginPass(encoder)) {
            pass.setPipeline(pipeline); transform.bind(pass, "DynamicTransforms"); projection.bind(pass, "Projection"); fog.bind(pass, "Fog");
            pass.setUniform("Sampler0", atlas.color(0).view(), sampler); pass.setUniform("Sampler2", lightmap.color(0).view(), sampler); mesh.draw(pass);
        }
        encoder.submit();
    }
    private static void assertColor(GpuFramebuffer target, int... expected) {
        var device = RenderSystem.getDevice();
        try (var readback = device.createBuffer(() -> "Sable world readback", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, 16)) {
            var encoder = device.createCommandEncoder(); encoder.copyTextureToBuffer(target.color(0).texture(), readback, 0, () -> {}, 0);
            try (var fence = encoder.createFence()) {
                encoder.submit(); check(fence.awaitCompletion(10_000_000_000L), "Sable world readback timeout");
                try (var mapped = readback.map(true, false)) {
                    for (int i = 0; i < 16; i++) check(Math.abs((mapped.data().get(i) & 255) - expected[i % 4]) <= 1,
                            "Production world pixel " + i + " was " + (mapped.data().get(i) & 255) + ", expected " + expected[i % 4]);
                }
            }
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
