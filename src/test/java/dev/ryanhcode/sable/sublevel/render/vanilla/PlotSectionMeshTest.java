package dev.ryanhcode.sable.sublevel.render.vanilla;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PlotSectionMeshTest {
    @Test
    void nativeQuadCaptureKeepsEveryVertexInputAndModeAcrossBlockFluidBoundary() {
        final var capture = new PlotSectionMesh.Capture().directional(true);
        final var instance = new QuadInstance();
        instance.setColor(0x7f204060);
        instance.setLightCoords(0x00e00090);
        instance.setOverlayCoords(0x12345678);
        final var quad = quad();
        capture.putBlockBakedQuad(.25f, .5f, .75f, quad, instance);
        capture.directional(false);
        capture.putBlockBakedQuad(1, 2, 3, quad, instance);
        final var vertices = capture.finish();
        assertEquals(8, vertices.size());
        for (int i = 0; i < 8; i++) {
            final var vertex = vertices.get(i);
            assertEquals(i < 4, vertex.directional());
            assertEquals(0x7f204060, vertex.color());
            assertEquals(0x00e00090, vertex.light());
            assertEquals(0x12345678, vertex.overlay());
            assertEquals(0, vertex.nx());
            assertEquals(0, vertex.ny());
            assertEquals(-1, vertex.nz());
            assertEquals(UVPair.unpackU(quad.packedUV(i % 4)), vertex.u());
            assertEquals(UVPair.unpackV(quad.packedUV(i % 4)), vertex.v());
        }
        assertEquals(.25f, vertices.getFirst().x());
        assertEquals(.5f, vertices.getFirst().y());
        assertEquals(.75f, vertices.getFirst().z());
        assertThrows(UnsupportedOperationException.class, () -> vertices.clear());
    }

    @Test
    void deferredNativeSubmissionUsesCopiedTransformLightAndColorWithoutWorldAccess() {
        final var capture = new PlotSectionMesh.Capture().directional(true);
        final var instance = new QuadInstance();
        instance.setColor(0x7fffffff);
        instance.setLightCoords(0x00f000a0);
        capture.putBlockBakedQuad(0, 0, 0, quad(), instance);
        final List<PlotSectionMesh.Vertex> mutable = new ArrayList<>(capture.finish());
        final var mesh = new PlotSectionMesh(SectionPos.of(1_875_000, 4, -1_875_000), Map.of(ChunkSectionLayer.SOLID, mutable), true);
        mutable.clear();
        final Quaternionf rotation = new Quaternionf().rotateZ((float) (Math.PI / 2));
        final var frame = new PlotSectionMesh.Snapshot(mesh, new Vec3(.375, .25, .5), rotation, new Vector3f(1), CardinalLighting.DEFAULT, 5);
        rotation.identity();
        final AtomicInteger submissions = new AtomicInteger();
        final SubmitNodeCollector nodes = (SubmitNodeCollector) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SubmitNodeCollector.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("submitCustomGeometry")) throw new AssertionError(method);
                    submissions.incrementAndGet();
                    final PoseStack pose = (PoseStack) args[0];
                    final var callback = (SubmitNodeCollector.CustomGeometryRenderer) args[2];
                    try (final var memory = new ByteBufferBuilder(1024)) {
                        final var builder = new BufferBuilder(memory, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);
                        callback.render(pose.last().copy(), builder);
                        try (final var data = builder.buildOrThrow()) {
                            final var bytes = data.vertexBuffer().order(ByteOrder.nativeOrder());
                            final int stride = DefaultVertexFormat.ENTITY.getVertexSize();
                            final int position = DefaultVertexFormat.ENTITY.getElement(DefaultVertexFormat.POSITION_SEMANTIC_NAME).offset();
                            final int color = DefaultVertexFormat.ENTITY.getElement(DefaultVertexFormat.COLOR_SEMANTIC_NAME).offset();
                            final int light = DefaultVertexFormat.ENTITY.getElement(DefaultVertexFormat.UV2_SEMANTIC_NAME).offset();
                            assertEquals(.375f, bytes.getFloat(position), 1e-6);
                            assertEquals(.25f, bytes.getFloat(position + 4), 1e-6);
                            assertEquals(.5f, bytes.getFloat(position + 8), 1e-6);
                            assertEquals(.375f, bytes.getFloat(stride + position), 1e-6);
                            assertEquals(1.25f, bytes.getFloat(stride + position + 4), 1e-6);
                            assertEquals(0x005000a0, bytes.getInt(light));
                            assertEquals(0xcc, Byte.toUnsignedInt(bytes.get(color)));
                            assertEquals(0xcc, Byte.toUnsignedInt(bytes.get(color + 1)));
                            assertEquals(0xcc, Byte.toUnsignedInt(bytes.get(color + 2)));
                            assertEquals(0x7f, Byte.toUnsignedInt(bytes.get(color + 3)));
                        }
                    }
                    return null;
                });
        frame.submit(nodes);
        assertEquals(1, submissions.get());
        assertEquals(4, mesh.layers().get(ChunkSectionLayer.SOLID).size());
    }

    @Test
    void cpuDirectionalLightingMatchesNativeCardinalValuesAndKeepsAlpha() {
        for (final var direction : Direction.values()) {
            assertEquals(CardinalLighting.DEFAULT.byFace(direction), PlotSectionMesh.directionalBrightness(CardinalLighting.DEFAULT, direction.getUnitVec3f()), 1e-6);
            assertEquals(CardinalLighting.NETHER.byFace(direction), PlotSectionMesh.directionalBrightness(CardinalLighting.NETHER, direction.getUnitVec3f()), 1e-6);
        }
        final Vector3f diagonal = new Vector3f(1, 0, 1).normalize();
        assertEquals(.7f, PlotSectionMesh.directionalBrightness(CardinalLighting.DEFAULT, diagonal), 1e-6);
        assertEquals(0x7f102030, PlotSectionMesh.shadeColor(0x7f204060, .5f));
        assertEquals(0x7fffffff, PlotSectionMesh.shadeColor(0x7fffffff, 2));
    }

    @Test
    void addonExtensionExtractsCopiedFrameInputsThenSubmitsWithoutLiveOwner() {
        final AtomicInteger extracted = new AtomicInteger(), submitted = new AtomicInteger();
        final List<PlotSectionMeshExtension.Geometry> extensions = new ArrayList<>();
        final SubmitNodeCollector nodes = (SubmitNodeCollector) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SubmitNodeCollector.class}, (proxy, method, args) -> { throw new AssertionError(method); });
        extensions.add((owner, section, partial) -> {
            assertNull(owner); // This fixture needs no level/physics owner; production passes the actual ClientSubLevel.
            assertEquals(.25f, partial);
            assertEquals(2, section.scale().x());
            assertEquals(.5, section.cameraRelativeOrigin().x);
            extracted.incrementAndGet();
            final Vec3 copiedOrigin = section.cameraRelativeOrigin();
            return target -> {
                assertSame(nodes, target);
                assertEquals(.5, copiedOrigin.x);
                submitted.incrementAndGet();
            };
        });
        final var mesh = new PlotSectionMesh(SectionPos.of(0, 0, 0), Map.of(), false, extensions);
        extensions.clear();
        final var scale = new Vector3f(2);
        final var frame = new PlotSectionMesh.Snapshot(mesh, new Vec3(.5, 0, 0), new Quaternionf(), scale,
                CardinalLighting.DEFAULT, 15);
        scale.zero();
        final var extractedFrame = frame.withExtensions(null, .25f);
        assertEquals(1, extracted.get());
        assertEquals(0, submitted.get());
        assertThrows(UnsupportedOperationException.class, () -> extractedFrame.extensions().clear());
        extractedFrame.submit(nodes);
        assertEquals(1, submitted.get());
        assertEquals(1, mesh.extensions().size());
    }

    @Test
    void callerOwnedGroupKeepsNativeFallbackAndParentPoseWhileSkippingWorldEffects() {
        final var capture = new PlotSectionMesh.Capture().directional(false);
        final var instance = new QuadInstance();
        instance.setColor(-1); instance.setLightCoords(0x00f000f0);
        capture.putBlockBakedQuad(0, 0, 0, quad(), instance);
        final var mesh = new PlotSectionMesh(SectionPos.of(0, 0, 0),
                Map.of(ChunkSectionLayer.SOLID, capture.finish()), false);
        final AtomicInteger queuedWorldEffects = new AtomicInteger(), nativeFallbacks = new AtomicInteger();
        final var frame = new PlotSectionMesh.Snapshot(mesh, new Vec3(.25, .5, .75), new Quaternionf(),
                new Vector3f(2), CardinalLighting.DEFAULT, 15, List.of(nodes -> queuedWorldEffects.incrementAndGet()));
        final var parent = new PoseStack(); parent.translate(10, 20, 30);
        parent.rotate(new Quaternionf().rotateZ((float) (Math.PI / 2)));
        final SubmitNodeCollector nodes = (SubmitNodeCollector) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SubmitNodeCollector.class}, (proxy, method, args) -> {
                    assertEquals("submitCustomGeometry", method.getName()); nativeFallbacks.incrementAndGet();
                    final var pose = ((PoseStack) args[0]).last().copy();
                    final var renderer = (SubmitNodeCollector.CustomGeometryRenderer) args[2];
                    try (final var memory = new ByteBufferBuilder(1024)) {
                        final var builder = new BufferBuilder(memory, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);
                        renderer.render(pose, builder);
                        try (final var data = builder.buildOrThrow()) {
                            final var bytes = data.vertexBuffer().order(ByteOrder.nativeOrder());
                            final int position = DefaultVertexFormat.ENTITY.getElement(DefaultVertexFormat.POSITION_SEMANTIC_NAME).offset();
                            final int stride = DefaultVertexFormat.ENTITY.getVertexSize();
                            assertEquals(4, data.drawState().vertexCount());
                            assertEquals(9.5f, bytes.getFloat(position), 1e-5);
                            assertEquals(20.25f, bytes.getFloat(position + 4), 1e-5);
                            assertEquals(30.75f, bytes.getFloat(position + 8), 1e-5);
                            assertEquals(9.5f, bytes.getFloat(stride + position), 1e-5);
                            assertEquals(22.25f, bytes.getFloat(stride + position + 4), 1e-5);
                        }
                    }
                    return null;
                });
        frame.submit(parent, nodes);
        assertEquals(1, nativeFallbacks.get());
        assertEquals(0, queuedWorldEffects.get());
        assertEquals(10, parent.last().pose().m30());
        assertEquals(20, parent.last().pose().m31());
    }

    private static BakedQuad quad() {
        return new BakedQuad(new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), new Vector3f(1, 1, 0), new Vector3f(0, 1, 0),
                UVPair.pack(0, 0), UVPair.pack(1, 0), UVPair.pack(1, 1), UVPair.pack(0, 1), Direction.NORTH,
                new BakedQuad.MaterialInfo(null, ChunkSectionLayer.SOLID, null, null, null, -1, null, 0));
    }
}
