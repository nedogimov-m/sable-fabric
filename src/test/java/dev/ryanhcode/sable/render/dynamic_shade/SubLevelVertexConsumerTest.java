package dev.ryanhcode.sable.render.dynamic_shade;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelVertexConsumerTest {
    @Test
    void nativeQuadBytesKeepGeometryUvsColorsAndLightsWhileApplyingShadeOverride() {
        final BakedQuad quad = quad(Direction.NORTH, Direction.UP);
        final QuadInstance instance = new QuadInstance();
        for (int i = 0; i < 4; i++) {
            instance.setColor(i, 0xff203040 + i);
            instance.setLightCoords(i, 0x00f000d0 + i);
        }
        try (final ByteBufferBuilder memory = new ByteBufferBuilder(1024)) {
            final BufferBuilder builder = new BufferBuilder(memory, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);
            new SubLevelVertexConsumer(builder).putBlockBakedQuad(4, 7, -2, quad, instance);
            try (final var mesh = builder.buildOrThrow()) {
                final ByteBuffer bytes = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                final int stride = DefaultVertexFormat.ENTITY.getVertexSize();
                for (int i = 0; i < 4; i++) {
                    final int start = i * stride;
                    assertEquals(quad.position(i).x() + 4, bytes.getFloat(start + offset(DefaultVertexFormat.POSITION_SEMANTIC_NAME)));
                    assertEquals(quad.position(i).y() + 7, bytes.getFloat(start + offset(DefaultVertexFormat.POSITION_SEMANTIC_NAME) + 4));
                    assertEquals(quad.position(i).z() - 2, bytes.getFloat(start + offset(DefaultVertexFormat.POSITION_SEMANTIC_NAME) + 8));
                    assertEquals(UVPair.unpackU(quad.packedUV(i)), bytes.getFloat(start + offset(DefaultVertexFormat.UV0_SEMANTIC_NAME)));
                    assertEquals(UVPair.unpackV(quad.packedUV(i)), bytes.getFloat(start + offset(DefaultVertexFormat.UV0_SEMANTIC_NAME) + 4));
                    assertEquals(instance.getLightCoords(i), bytes.getInt(start + offset(DefaultVertexFormat.UV2_SEMANTIC_NAME)));
                    assertNormal(bytes, start, 0, 127, 0);
                    final int color = start + offset(DefaultVertexFormat.COLOR_SEMANTIC_NAME);
                    assertEquals(0x20, Byte.toUnsignedInt(bytes.get(color)));
                    assertEquals(0x30, Byte.toUnsignedInt(bytes.get(color + 1)));
                    assertEquals(0x40 + i, Byte.toUnsignedInt(bytes.get(color + 2)));
                    assertEquals(0xff, Byte.toUnsignedInt(bytes.get(color + 3)));
                }
            }
        }
        assertEquals(Direction.NORTH, quad.direction());
        assertEquals(Direction.UP, quad.materialInfo().shadeDirectionOverride());
    }

    @Test
    void transformedOverrideNormalRotatesAndNextRegularQuadUsesItsOwnNormal() {
        final BakedQuad overridden = quad(Direction.NORTH, Direction.UP);
        final BakedQuad regular = quad(Direction.EAST, null);
        assertSame(regular, SubLevelVertexConsumer.withShadeNormal(regular));
        final QuadInstance instance = new QuadInstance();
        instance.setColor(-1);
        final PoseStack pose = new PoseStack();
        pose.rotate(new Quaternionf().rotateZ((float) (Math.PI / 2)));
        try (final ByteBufferBuilder memory = new ByteBufferBuilder(1024)) {
            final BufferBuilder builder = new BufferBuilder(memory, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);
            final SubLevelVertexConsumer consumer = new SubLevelVertexConsumer(builder);
            consumer.putBakedQuad(pose.last(), overridden, instance);
            consumer.putBlockBakedQuad(0, 0, 0, regular, instance);
            try (final var mesh = builder.buildOrThrow()) {
                final ByteBuffer bytes = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                final int stride = DefaultVertexFormat.ENTITY.getVertexSize();
                for (int i = 0; i < 4; i++) {
                    final int normal = i * stride + offset(DefaultVertexFormat.NORMAL_SEMANTIC_NAME);
                    // Native packed normals truncate after floating-point rotation.
                    assertEquals(-127, bytes.get(normal), 1);
                    assertEquals(0, bytes.get(normal + 1));
                    assertEquals(0, bytes.get(normal + 2));
                }
                for (int i = 4; i < 8; i++) assertNormal(bytes, i * stride, 127, 0, 0);
            }
        }
    }

    private static int offset(final String semantic) {
        return DefaultVertexFormat.ENTITY.getElement(semantic).offset();
    }

    private static void assertNormal(final ByteBuffer bytes, final int vertex, final int x, final int y, final int z) {
        final int offset = vertex + offset(DefaultVertexFormat.NORMAL_SEMANTIC_NAME);
        assertEquals(x, bytes.get(offset));
        assertEquals(y, bytes.get(offset + 1));
        assertEquals(z, bytes.get(offset + 2));
    }

    private static BakedQuad quad(final Direction face, final Direction shadeOverride) {
        return new BakedQuad(new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), new Vector3f(1, 1, 0), new Vector3f(0, 1, 0),
                UVPair.pack(0, 0), UVPair.pack(1, 0), UVPair.pack(1, 1), UVPair.pack(0, 1), face,
                new BakedQuad.MaterialInfo(null, ChunkSectionLayer.SOLID, null, null, null, -1, shadeOverride, 0));
    }
}
