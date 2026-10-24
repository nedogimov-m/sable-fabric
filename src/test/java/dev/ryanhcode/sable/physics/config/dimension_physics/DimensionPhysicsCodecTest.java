package dev.ryanhcode.sable.physics.config.dimension_physics;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DimensionPhysicsCodecTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void datapackVectorsPressureAndPrioritySurviveNetworkRoundtrip() {
        final var input = JsonParser.parseString("""
                {"dimension":"minecraft:overworld","priority":15,"universal_drag":0.12,
                 "base_gravity":[1.0,-4.0,2.0],"base_pressure":0.8,
                 "magnetic_north":[0.0,0.0,-1.0],"ignore_chunks":true,
                 "pressure_function":[{"altitude":0.0,"value":1.0,"slope":0.0},
                                      {"altitude":100.0,"value":0.5,"slope":0.0}]}
                """);
        final var profile = DimensionPhysics.CODEC.parse(JsonOps.INSTANCE, input).getOrThrow();
        assertEquals(-4.0f, profile.baseGravity().orElseThrow().y());
        assertEquals(-1.0f, profile.magneticNorth().orElseThrow().z());
        final var buffer = Unpooled.buffer();
        try {
            DimensionPhysics.STREAM_CODEC.encode(buffer, profile);
            final var restored = DimensionPhysics.STREAM_CODEC.decode(buffer);
            assertEquals(0, buffer.readableBytes(), "Every optional field must consume exactly its own bytes");
            assertEquals(profile.dimension(), restored.dimension());
            assertEquals(15, restored.priority());
            assertEquals(profile.universalDrag(), restored.universalDrag());
            assertEquals(profile.baseGravity(), restored.baseGravity());
            assertEquals(profile.basePressure(), restored.basePressure());
            assertEquals(profile.magneticNorth(), restored.magneticNorth());
            assertEquals(profile.pressureFunction().orElseThrow().getPoints(),
                    restored.pressureFunction().orElseThrow().getPoints());
            assertTrue(restored.ignoreChunks());
        } finally {
            buffer.release();
        }
    }

    @Test
    void omittedOptionsKeepDefaultsAndRemainAbsentOnWire() {
        final var profile = DimensionPhysics.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"dimension\":\"minecraft:the_nether\"}")).getOrThrow();
        final var buffer = Unpooled.buffer();
        try {
            DimensionPhysics.STREAM_CODEC.encode(buffer, profile);
            final var restored = DimensionPhysics.STREAM_CODEC.decode(buffer);
            assertEquals(1000, restored.priority());
            assertFalse(restored.ignoreChunks());
            assertTrue(restored.baseGravity().isEmpty());
            assertTrue(restored.basePressure().isEmpty());
            assertTrue(restored.universalDrag().isEmpty());
            assertTrue(restored.magneticNorth().isEmpty());
            assertTrue(restored.pressureFunction().isEmpty());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
