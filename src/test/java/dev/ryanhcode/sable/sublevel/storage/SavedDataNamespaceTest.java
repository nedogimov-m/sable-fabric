package dev.ryanhcode.sable.sublevel.storage;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SavedDataNamespaceTest {
    @TempDir Path folder;

    @Test
    void explicitAddonNamespaceMigratesLegacyDiskDataAndReopensIndependently() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        final String legacyName = "aeronautics_crystallization";
        final Identifier id = Identifier.fromNamespaceAndPath("aeronautics", "crystallization");
        final Path legacy = folder.resolve(legacyName + ".dat");
        final long chunk = ChunkPos.pack(-17, 22);
        final TicketStorage old = new TicketStorage();
        old.addTicket(chunk, new Ticket(TicketType.FORCED, 31));
        final CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        root.put("data", TicketStorage.CODEC.encodeStart(NbtOps.INSTANCE, old).getOrThrow());
        NbtIo.writeCompressed(root, legacy);
        final byte[] original = Files.readAllBytes(legacy);
        try (final SavedDataStorage storage = storage()) {
            final TicketStorage loaded = load(storage, id, legacyName);
            assertEquals(1, loaded.getTickets(chunk).size());
            assertSame(loaded, load(storage, id, legacyName));
            loaded.setDirty();
            storage.saveAndJoin();
        }
        assertArrayEquals(original, Files.readAllBytes(legacy));
        assertTrue(Files.isRegularFile(folder.resolve("aeronautics/crystallization.dat")));
        assertFalse(Files.exists(folder.resolve("sable")));
        try (final SavedDataStorage storage = storage()) {
            assertEquals(1, load(storage, id, legacyName).getTickets(chunk).size());
        }
    }

    private SavedDataStorage storage() {
        return new SavedDataStorage(folder, DataFixers.getDataFixer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private TicketStorage load(final SavedDataStorage storage, final Identifier id, final String legacyName) {
        return SableSavedDataStorage.getOrLoad(storage, folder, id, legacyName, TicketStorage::new,
                tag -> {
                    final TicketStorage loaded = TicketStorage.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
                    loaded.activateAllDeactivatedTickets();
                    return loaded;
                }, value -> (CompoundTag) TicketStorage.CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow(), null);
    }
}
