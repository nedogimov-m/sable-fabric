package dev.ryanhcode.sable.sublevel.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;
import java.util.function.Supplier;

/** Keeps legacy Sable data files available when 26.3 introduces namespaced paths. */
public final class SableSavedDataStorage {
    private SableSavedDataStorage() {}

    public static <T extends SavedData> T getOrLoad(final SavedDataStorage storage, final Path folder,
                                                   final String legacyName, final Supplier<T> constructor,
                                                   final Function<CompoundTag, T> load,
                                                   final Function<T, CompoundTag> save,
                                                   final DataFixTypes fixer) {
        return getOrLoad(storage, folder, Identifier.fromNamespaceAndPath("sable", legacyName), legacyName,
                constructor, load, save, fixer);
    }

    public static <T extends SavedData> T getOrLoad(final SavedDataStorage storage, final Path folder,
                                                   final Identifier id, final String legacyName, final Supplier<T> constructor,
                                                   final Function<CompoundTag, T> load,
                                                   final Function<T, CompoundTag> save,
                                                   final DataFixTypes fixer) {
        migrateLegacyFile(folder, id, legacyName);
        // 26.3 SavedDataStorage dereferences the fixer even for current-format files.
        final var type = new SavedDataType<>(id, constructor, CompoundTag.CODEC.xmap(load, save),
                fixer == null ? DataFixTypes.LEVEL : fixer);
        return storage.computeIfAbsent(type);
    }

    private static void migrateLegacyFile(final Path folder, final Identifier id, final String legacyName) {
        final Path source = folder.resolve(legacyName + ".dat");
        final Path destination = id.withSuffix(".dat").resolveAgainst(folder);
        if (Files.exists(destination) || !Files.exists(source)) return;
        Path temporary = null;
        try {
            Files.createDirectories(destination.getParent());
            temporary = Files.createTempFile(destination.getParent(), legacyName + ".", ".migrating");
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            // Never replace an existing 26.3 file or modify the original world data.
            Files.move(temporary, destination);
        } catch (final FileAlreadyExistsException error) {
            // Another load published the destination first; that existing file wins.
            if (!Files.isRegularFile(destination)) {
                throw new UncheckedIOException("Cannot migrate Sable saved data " + source, error);
            }
        } catch (final IOException error) {
            throw new UncheckedIOException("Cannot migrate Sable saved data " + source, error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (final IOException ignored) {
                    temporary.toFile().deleteOnExit();
                }
            }
        }
    }
}
