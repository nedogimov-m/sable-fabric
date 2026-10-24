package dev.ryanhcode.sable.sublevel.plot;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.TickPriority;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlotChunkSerializationTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void mixedSectionPreservesAll4096StatesAndProperties() {
        final PalettedContainer<BlockState> original = new PalettedContainer<>(Blocks.AIR.defaultBlockState(),
                Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
        final BlockState[] states = {Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z),
                Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST)};
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            original.set(x, y, z, states[(x + y * 3 + z * 7) % states.length]);
        }
        final Tag encoded = PlotChunkSerialization.writeBlockStates(original);
        assertTrue(((CompoundTag) encoded).contains("palette"));
        final var restored = PlotChunkSerialization.readBlockStates(encoded, message -> fail(message));
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            assertSame(original.get(x, y, z), restored.get(x, y, z));
        }
    }

    @Test
    void legacySingletonPaletteLoadsAndInvalidPaletteFails() {
        final CompoundTag legacy = new CompoundTag();
        final ListTag palette = new ListTag();
        final CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:oak_log");
        final CompoundTag properties = new CompoundTag();
        properties.putString("axis", "z");
        state.put("Properties", properties);
        palette.add(state);
        legacy.put("palette", palette);
        final var restored = PlotChunkSerialization.readBlockStates(legacy, message -> fail(message));
        final BlockState expected = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        assertSame(expected, restored.get(0, 0, 0));
        assertSame(expected, restored.get(15, 15, 15));
        assertTrue(state.contains("Name"), "Loading must preserve the caller's original NBT");
        assertThrows(SerializableChunkData.ChunkReadException.class,
                () -> PlotChunkSerialization.readBlockStates(new CompoundTag(), ignored -> {}));
    }

    @Test
    void legacyBlockTicksRetainDelayPriorityAndFilterMissingOrForeignEntries() {
        final ListTag legacy = new ListTag();
        legacy.add(legacyTick("minecraft:redstone_torch", -31, 70, 50));
        legacy.add(legacyTick("minecraft:redstone_torch", 0, 70, 0));
        legacy.add(legacyTick("missing:removed_block", -30, 70, 50));
        final List<String> warnings = new ArrayList<>();
        final var restored = PlotChunkSerialization.readBlockTicks(legacy, new ChunkPos(-2, 3), warnings::add);
        assertEquals(1, warnings.size());
        assertEquals(1, restored.count());
        final List<SavedTick<Block>> packed = restored.pack(1000);
        final var encoded = (ListTag) PlotChunkSerialization.writeBlockTicks(packed);
        assertEquals("minecraft:redstone_torch", encoded.getCompoundOrEmpty(0).getStringOr("i", ""));
        assertEquals(7, encoded.getCompoundOrEmpty(0).getIntOr("t", -1));
        restored.unpack(5000);
        final var tick = restored.poll();
        assertNotNull(tick);
        assertEquals(new BlockPos(-31, 70, 50), tick.pos());
        assertEquals(5007, tick.triggerTick());
        assertSame(TickPriority.HIGH, tick.priority());
    }

    @Test
    void fluidTicksPreserveOverdueAndFutureDelaysAcrossSaveReload() {
        final var encoded = (ListTag) PlotChunkSerialization.writeFluidTicks(List.of(
                new SavedTick<>(Fluids.FLOWING_WATER, new BlockPos(1, 70, 1), -3, TickPriority.NORMAL),
                new SavedTick<>(Fluids.LAVA, new BlockPos(2, 70, 1), 200, TickPriority.LOW)));
        final var restored = PlotChunkSerialization.readFluidTicks(encoded, ChunkPos.ZERO, message -> fail(message));
        restored.unpack(9000);
        final var overdue = restored.poll();
        final var future = restored.poll();
        assertNotNull(overdue);
        assertNotNull(future);
        assertSame(Fluids.FLOWING_WATER, overdue.type());
        assertEquals(8997, overdue.triggerTick());
        assertSame(Fluids.LAVA, future.type());
        assertEquals(9200, future.triggerTick());
        assertSame(TickPriority.LOW, future.priority());
    }

    private static CompoundTag legacyTick(final String type, final int x, final int y, final int z) {
        final CompoundTag tag = new CompoundTag();
        tag.putString("i", type);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.putInt("t", 7);
        tag.putInt("p", -1);
        return tag;
    }
}
