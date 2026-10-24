package dev.ryanhcode.sable.fabric.platform;

import dev.ryanhcode.sable.platform.SablePlotPlatform;
import dev.ryanhcode.sable.Sable;
import net.fabricmc.fabric.impl.attachment.AttachmentTargetImpl;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

@SuppressWarnings("UnstableApiUsage")
public class SablePlotPlatformImpl implements SablePlotPlatform {

    @Override
    public void readLightData(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        // no-op
    }

    @Override
    public void readChunkAttachments(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        try (var reporter = new ProblemReporter.ScopedCollector(chunk.problemPath(), Sable.LOGGER)) {
            ((AttachmentTargetImpl) chunk).fabric_readAttachmentsFromNbt(TagValueInput.create(reporter, registryAccess, tag));
        }
    }

    @Override
    public void postLoad(final CompoundTag tag, final LevelChunk chunk) {
        // no-op
    }

    @Override
    public void writeLightData(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        // no-op
    }

    @Override
    public void writeChunkAttachments(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        try (var reporter = new ProblemReporter.ScopedCollector(chunk.problemPath(), Sable.LOGGER)) {
            final TagValueOutput output = TagValueOutput.createWithContext(reporter, registryAccess);
            ((AttachmentTargetImpl) chunk).fabric_writeAttachmentsToNbt(output);
            tag.merge(output.buildResult());
        }
    }
}
