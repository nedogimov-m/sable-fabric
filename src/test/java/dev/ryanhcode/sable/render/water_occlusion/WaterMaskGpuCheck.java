package dev.ryanhcode.sable.render.water_occlusion;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
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

/** Test-only native Vulkan entrypoint; no Sable initializer or game world is loaded. */
public final class WaterMaskGpuCheck implements ClientModInitializer {
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
                     final var masks = new WaterMaskPass(device, sources);
                     final var mesh = VertexArray.create(device, "Sable water mask test mesh");
                     final var memory = new ByteBufferBuilder(1024);
                     final var front = GpuFramebuffer.builder("front water mask", 4, 2).depth(GpuFormat.D32_FLOAT).build(device);
                     final var back = GpuFramebuffer.builder("back water mask", 4, 2).depth(GpuFormat.D32_FLOAT).build(device);
                     final var inside = GpuFramebuffer.builder("inside water mask", 4, 2).depth(GpuFormat.D32_FLOAT).build(device)) {
                    final var vertices = new BufferBuilder(memory, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
                    // Opposite winding for entry and exit surfaces in reverse-depth clip space.
                    vertices.addVertex(-1, -1, .8f); vertices.addVertex(1, -1, .8f);
                    vertices.addVertex(1, 1, .8f); vertices.addVertex(-1, 1, .8f);
                    vertices.addVertex(-1, -1, .2f); vertices.addVertex(-1, 1, .2f);
                    vertices.addVertex(1, 1, .2f); vertices.addVertex(1, -1, .2f);
                    final var encoder = device.createCommandEncoder();
                    mesh.upload(encoder, vertices.buildOrThrow(), VertexArray.DrawUsage.STATIC);
                    try (final var geometry = mesh.retain()) {
                        final Matrix4f left = new Matrix4f().translate(-.5f, 0, 0).scale(.5f, 1, 1);
                        final Matrix4f right = new Matrix4f().translate(.5f, 0, -.1f).scale(.5f, 1, 1);
                        final var inputs = List.of(new WaterMaskPass.Input(geometry, left), new WaterMaskPass.Input(geometry, right));
                        left.identity(); right.identity();
                        try (final var a = masks.render(encoder, front, new Matrix4f(), inputs, 1, 0);
                             final var b = masks.render(encoder, back, new Matrix4f(), inputs, -1, 0);
                             final var c = masks.render(encoder, inside, new Matrix4f(), inputs, 1, 1)) {
                            encoder.submit();
                            assertDepth(device, front, .8f, .7f);
                            assertDepth(device, back, .2f, .1f);
                            assertDepth(device, inside, 1, 1);
                        }
                    }
                    report.put("checks", List.of("native entry/exit reverse-depth masks", "copied distinct region matrices in one encoder", "camera-inside near-depth initialization"));
                    report.put("cases", 3);
                    report.put("status", "passed");
                }
            } catch (final Throwable failure) {
                failure.printStackTrace();
                report.put("status", "failed");
                report.put("error", failure.toString());
            } finally {
                try {
                    Files.writeString(Path.of(System.getProperty("sable.water.mask.report")),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
                } catch (final Exception failure) { throw new RuntimeException(failure); }
                client.stop();
            }
        });
    }

    private static void assertDepth(final com.mojang.renderpearl.api.device.GpuDevice device,
                                    final GpuFramebuffer target, final float left, final float right) {
        try (final var readback = device.createBuffer(() -> "water mask depth readback", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, 32)) {
            final var encoder = device.createCommandEncoder();
            encoder.copyTextureToBuffer(target.depth().texture(), readback, 0, () -> {}, 0);
            try (final var fence = encoder.createFence()) {
                encoder.submit();
                check(fence.awaitCompletion(10_000_000_000L), "Depth readback timed out");
                try (final var mapped = readback.map(true, false)) {
                    final var bytes = mapped.data().order(ByteOrder.nativeOrder());
                    for (int y = 0; y < 2; y++) for (int x = 0; x < 4; x++) {
                        final float actual = bytes.getFloat((y * 4 + x) * 4);
                        final float expected = x < 2 ? left : right;
                        check(Math.abs(actual - expected) < 1e-5, "Depth (" + x + "," + y + ") was " + actual + "; expected " + expected);
                    }
                }
            }
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
