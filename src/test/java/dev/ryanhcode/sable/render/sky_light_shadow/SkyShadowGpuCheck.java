package dev.ryanhcode.sable.render.sky_light_shadow;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import foundry.veil.api.client.render.gpu.GpuFramebuffer;
import foundry.veil.api.client.render.gpu.GpuShaderSource;
import foundry.veil.api.client.render.vertex.VertexArray;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.joml.Matrix4f;

import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** Isolated production sky-depth GPU test; no Sable world/initializer is loaded. */
public final class SkyShadowGpuCheck implements ClientModInitializer {
    private int ticks;
    private boolean ran;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (this.ran || ++this.ticks < 5 || !client.isGameLoadFinished()) return;
            this.ran = true;
            final Map<String, Object> report = new LinkedHashMap<>();
            try {
                final var device = RenderSystem.getDevice();
                report.put("backend", device.getDeviceInfo().backendName());
                report.put("device", device.getDeviceInfo().name());
                check(device.getDeviceInfo().backendName().toLowerCase(java.util.Locale.ROOT).contains("vulkan"), "Actual Vulkan required");
                try (final var sources = new GpuShaderSource(client.getResourceManager());
                     final var shadows = new SkyShadowPass(device, sources);
                     final var mesh = VertexArray.create(device, "Sable alpha shadow test mesh");
                     final var horizontal = VertexArray.create(device, "Sable horizontal shadow test mesh");
                     final var memory = new ByteBufferBuilder(1024);
                     final var pixels = new NativeImage(2, 1, true);
                     final var atlas = GpuFramebuffer.builder("Sable test cutout atlas", 2, 1).color(GpuFormat.RGBA8_UNORM).build(device);
                     final var sampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                             FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
                     final var cutout = GpuFramebuffer.builder("cutout shadow", 4, 2).depth(GpuFormat.D32_FLOAT).build(device);
                     final var solid = GpuFramebuffer.builder("solid shadow", 4, 2).depth(GpuFormat.D32_FLOAT).build(device);
                     final var distinct = GpuFramebuffer.builder("distinct shadow matrices", 4, 2).depth(GpuFormat.D32_FLOAT).build(device);
                     final var topDown = GpuFramebuffer.builder("orthographic shadow", 4, 2).depth(GpuFormat.D32_FLOAT).build(device)) {
                    final var encoder = device.createCommandEncoder();
                    pixels.setPixel(0, 0, 0x00ffffff);
                    pixels.setPixel(1, 0, 0xffffffff);
                    encoder.writeToTexture(atlas.color(0).texture(), pixels);
                    final var vertices = new BufferBuilder(memory, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    vertices.addVertex(-1, -1, .8f).setUv(0, 0).setColor(-1);
                    vertices.addVertex(1, -1, .8f).setUv(1, 0).setColor(-1);
                    vertices.addVertex(1, 1, .8f).setUv(1, 1).setColor(-1);
                    vertices.addVertex(-1, 1, .8f).setUv(0, 1).setColor(-1);
                    mesh.upload(encoder, vertices.buildOrThrow(), VertexArray.DrawUsage.STATIC);
                    final var ground = new BufferBuilder(memory, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    ground.addVertex(-128, 32, -128).setUv(0, 0).setColor(-1);
                    ground.addVertex(128, 32, -128).setUv(1, 0).setColor(-1);
                    ground.addVertex(128, 32, 128).setUv(1, 1).setColor(-1);
                    ground.addVertex(-128, 32, 128).setUv(0, 1).setColor(-1);
                    horizontal.upload(encoder, ground.buildOrThrow(), VertexArray.DrawUsage.STATIC);
                    try (final var geometry = mesh.retain(); final var plane = horizontal.retain()) {
                        final var left = new Matrix4f().translate(-.5f, 0, 0).scale(.5f, 1, 1);
                        final var right = new Matrix4f().translate(.5f, 0, -.1f).scale(.5f, 1, 1);
                        final var transforms = List.of(new SkyShadowPass.Input(geometry, left, 0), new SkyShadowPass.Input(geometry, right, 0));
                        left.identity(); right.identity();
                        final var projection = new Matrix4f().setOrtho(-128, 128, -128, 128, 128, .5f, true);
                        final var view = new Matrix4f().rotationX((float) (Math.PI / 2)).translate(0, -64, 0);
                        try (final var a = shadows.render(encoder, cutout, new Matrix4f(),
                                     List.of(new SkyShadowPass.Input(geometry, new Matrix4f(), .5f)), atlas.color(0).view(), sampler);
                             final var b = shadows.render(encoder, solid, new Matrix4f(),
                                     List.of(new SkyShadowPass.Input(geometry, new Matrix4f(), 0)), atlas.color(0).view(), sampler);
                             final var c = shadows.render(encoder, distinct, new Matrix4f(), transforms, atlas.color(0).view(), sampler);
                             final var d = shadows.render(encoder, topDown, projection,
                                     List.of(new SkyShadowPass.Input(plane, view, 0)), atlas.color(0).view(), sampler)) {
                            encoder.submit();
                            assertDepth(device, cutout, 0, .8f);
                            assertDepth(device, solid, .8f, .8f);
                            assertDepth(device, distinct, .8f, .7f);
                            final float depth = 1 - (32 - .5f) / (128 - .5f);
                            assertDepth(device, topDown, depth, depth);
                        }
                    }
                    report.put("checks", List.of("native atlas alpha cutout", "solid depth ignores texture alpha", "copied distinct section transforms", "top-down zero-to-one reverse-depth projection"));
                    report.put("cases", 4);
                    report.put("status", "passed");
                }
            } catch (final Throwable failure) {
                failure.printStackTrace(); report.put("status", "failed"); report.put("error", failure.toString());
            } finally {
                try { Files.writeString(Path.of(System.getProperty("sable.sky.shadow.report")), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
                catch (final Exception failure) { throw new RuntimeException(failure); }
                client.stop();
            }
        });
    }
    private static void assertDepth(final com.mojang.renderpearl.api.device.GpuDevice device,
                                    final GpuFramebuffer target, final float left, final float right) {
        try (final var readback = device.createBuffer(() -> "sky shadow depth readback", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, 32)) {
            final var encoder = device.createCommandEncoder();
            encoder.copyTextureToBuffer(target.depth().texture(), readback, 0, () -> {}, 0);
            try (final var fence = encoder.createFence()) {
                encoder.submit();
                check(fence.awaitCompletion(10_000_000_000L), "Shadow readback timed out");
                try (final var mapped = readback.map(true, false)) {
                    final var bytes = mapped.data().order(ByteOrder.nativeOrder());
                    for (int y = 0; y < 2; y++) for (int x = 0; x < 4; x++) {
                        final float actual = bytes.getFloat((y * 4 + x) * 4), expected = x < 2 ? left : right;
                        check(Math.abs(actual - expected) < 1e-5, "Depth (" + x + "," + y + ") was " + actual + "; expected " + expected);
                    }
                }
            }
        }
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
