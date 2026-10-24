package dev.ryanhcode.sable.sublevel.storage;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SableSavedDataStorageTest {
    @TempDir Path folder;
    private static final String NAME = "sable_saved_data_test";
    private static final long FIRST = ChunkPos.pack(-7, 9);
    private static final long SECOND = ChunkPos.pack(3, -2);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void legacyFileLoadsCachesAndSurvivesActualSavedDataStorageReopen() throws Exception {
        final Path legacy = folder.resolve(NAME + ".dat");
        writeNativeData(legacy, withTicket(FIRST));
        final byte[] original = Files.readAllBytes(legacy);
        try (final SavedDataStorage storage = storage()) {
            final TicketStorage loaded = load(storage);
            assertEquals(1, loaded.getTickets(FIRST).size());
            assertSame(loaded, load(storage), "SavedDataType identity must remain stable between access calls");
            loaded.addTicket(SECOND, new Ticket(TicketType.FORCED, 31));
            storage.saveAndJoin();
        }
        assertArrayEquals(original, Files.readAllBytes(legacy));
        assertTrue(Files.exists(folder.resolve("sable/" + NAME + ".dat")));
        try (final SavedDataStorage storage = storage()) {
            assertEquals(1, load(storage).getTickets(FIRST).size());
            assertEquals(1, load(storage).getTickets(SECOND).size());
        }
    }

    @Test
    void existingNamespacedDataWinsOverOldSource() throws Exception {
        writeNativeData(folder.resolve(NAME + ".dat"), withTicket(FIRST));
        final Path current = folder.resolve("sable/" + NAME + ".dat");
        writeNativeData(current, withTicket(SECOND));
        final byte[] currentBytes = Files.readAllBytes(current);
        try (final SavedDataStorage storage = storage()) {
            assertTrue(load(storage).getTickets(FIRST).isEmpty());
            assertEquals(1, load(storage).getTickets(SECOND).size());
        }
        assertArrayEquals(currentBytes, Files.readAllBytes(current));
    }

    @Test
    void failedMigrationThrowsInsteadOfStartingWithEmptyData() throws Exception {
        final Path legacy = folder.resolve(NAME + ".dat");
        writeNativeData(legacy, withTicket(FIRST));
        final byte[] original = Files.readAllBytes(legacy);
        Files.writeString(folder.resolve("sable"), "This is a file, so migration cannot create its directory");
        try (final SavedDataStorage storage = storage()) {
            assertThrows(UncheckedIOException.class, () -> load(storage));
        }
        assertArrayEquals(original, Files.readAllBytes(legacy));
    }

    private SavedDataStorage storage() {
        return new SavedDataStorage(folder, DataFixers.getDataFixer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private TicketStorage load(final SavedDataStorage storage) {
        return SableSavedDataStorage.getOrLoad(storage, folder, NAME, TicketStorage::new,
                tag -> {
                    final TicketStorage loaded = TicketStorage.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
                    loaded.activateAllDeactivatedTickets();
                    return loaded;
                },
                value -> (CompoundTag) TicketStorage.CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow(), null);
    }

    private static TicketStorage withTicket(final long chunk) {
        final TicketStorage storage = new TicketStorage();
        storage.addTicket(chunk, new Ticket(TicketType.FORCED, 31));
        return storage;
    }

    private static void writeNativeData(final Path path, final TicketStorage value) throws Exception {
        Files.createDirectories(path.getParent());
        final CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        root.put("data", TicketStorage.CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow());
        NbtIo.writeCompressed(root, path);
    }
}
