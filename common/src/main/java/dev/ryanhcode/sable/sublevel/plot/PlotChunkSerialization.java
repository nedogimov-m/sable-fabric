package dev.ryanhcode.sable.sublevel.plot;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.SavedTick;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Minecraft 26.3 codecs for the existing Sable plot NBT layout. */
public final class PlotChunkSerialization {
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATES = PalettedContainer.codecRW(
            BlockState.CODEC, Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY), Blocks.AIR.defaultBlockState());
    private static final Codec<SavedTick<Block>> BLOCK_TICK = SavedTick.codec(BuiltInRegistries.BLOCK.byNameCodec());
    private static final Codec<SavedTick<Fluid>> FLUID_TICK = SavedTick.codec(BuiltInRegistries.FLUID.byNameCodec());

    private PlotChunkSerialization() {}

    public static Tag writeBlockStates(final PalettedContainer<BlockState> states) {
        return BLOCK_STATES.encodeStart(NbtOps.INSTANCE, states).getOrThrow();
    }

    public static PalettedContainer<BlockState> readBlockStates(final Tag tag, final Consumer<String> reportError) {
        return BLOCK_STATES.parse(NbtOps.INSTANCE, migrateLegacyPalette(tag)).promotePartial(reportError)
                .getOrThrow(SerializableChunkData.ChunkReadException::new);
    }

    private static Tag migrateLegacyPalette(final Tag tag) {
        if (!(tag instanceof final CompoundTag container)) return tag;
        final ListTag palette = container.getListOrEmpty("palette");
        CompoundTag migrated = null;
        for (int i = 0; i < palette.size(); i++) {
            if (!(palette.get(i) instanceof final CompoundTag state) || !state.contains("Name")) continue;
            if (migrated == null) migrated = container.copy();
            final CompoundTag converted = migrated.getListOrEmpty("palette").getCompoundOrEmpty(i);
            converted.putString("id", converted.getStringOr("Name", "minecraft:air"));
            converted.remove("Name");
            final Tag properties = converted.remove("Properties");
            if (properties != null) converted.put("properties", properties);
        }
        return migrated == null ? tag : migrated;
    }

    public static Tag writeBlockTicks(final List<SavedTick<Block>> ticks) {
        return BLOCK_TICK.listOf().encodeStart(NbtOps.INSTANCE, ticks).getOrThrow();
    }

    public static Tag writeFluidTicks(final List<SavedTick<Fluid>> ticks) {
        return FLUID_TICK.listOf().encodeStart(NbtOps.INSTANCE, ticks).getOrThrow();
    }

    public static LevelChunkTicks<Block> readBlockTicks(final ListTag tag, final ChunkPos chunk,
                                                       final Consumer<String> reportError) {
        return readTicks(BLOCK_TICK, tag, chunk, reportError);
    }

    public static LevelChunkTicks<Fluid> readFluidTicks(final ListTag tag, final ChunkPos chunk,
                                                       final Consumer<String> reportError) {
        return readTicks(FLUID_TICK, tag, chunk, reportError);
    }

    private static <T> LevelChunkTicks<T> readTicks(final Codec<SavedTick<T>> codec, final ListTag tag,
                                                   final ChunkPos chunk, final Consumer<String> reportError) {
        final List<SavedTick<T>> decoded = new ArrayList<>(tag.size());
        for (final Tag entry : tag) {
            // Upstream skipped ticks whose block/fluid was no longer registered.
            codec.parse(NbtOps.INSTANCE, entry).resultOrPartial(reportError).ifPresent(decoded::add);
        }
        return new LevelChunkTicks<>(SavedTick.filterTickListForChunk(decoded, chunk));
    }
}
