package dev.ryanhcode.sable.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import static org.junit.jupiter.api.Assertions.*;

class ConfigSpecTest {
    @TempDir Path directory;
    enum Renderer { VANILLA, FANCY }

    private static final class Settings {
        final ConfigSpec.Builder builder = new ConfigSpec.Builder();
        final ConfigSpec.BooleanValue enabled = builder.define("enabled", true);
        final ConfigSpec.IntValue steps = builder.defineInRange("steps", 2, 1, 10);
        final ConfigSpec.DoubleValue delay = builder.defineInRange("delay", 1.5, 0.0, 100.0);
        final ConfigSpec.EnumValue<Renderer> renderer = builder.defineEnum("renderer", Renderer.VANILLA, Renderer.values());
        final ConfigSpec spec = builder.build();
    }

    @Test void keepsExistingFlatTomlValuesAndPersistsChanges() throws Exception {
        Path file = directory.resolve("sable-server.toml");
        Files.writeString(file, "# Existing configuration\nenabled = false\nsteps = 3 # physics\ndelay = 0.5\nrenderer = \"FANCY\"\n");
        var settings = new Settings(); settings.spec.load(file);
        assertFalse(settings.enabled.getAsBoolean()); assertEquals(3, settings.steps.getAsInt());
        assertEquals(0.5, settings.delay.getAsDouble()); assertEquals(Renderer.FANCY, settings.renderer.get());
        settings.steps.set(4); settings.spec.save();
        var restored = new Settings(); restored.spec.load(file);
        assertEquals(4, restored.steps.getAsInt()); assertFalse(restored.enabled.get());
        assertEquals(Renderer.FANCY, restored.renderer.get());
        assertEquals(1, settings.steps.getSpec().getRange().getMin()); assertEquals(10, settings.steps.getRange().getMax());
        try (var files = Files.list(directory)) { assertEquals(1, files.count(), "Atomic save left temporary files"); }
    }

    @Test void correctsInvalidValuesWithoutDroppingValidSettings() throws Exception {
        Path file = directory.resolve("sable-client.toml");
        Files.writeString(file, "enabled = false\nsteps = 99\ndelay = nan\nrenderer = \"UNKNOWN\"\n");
        var settings = new Settings(); settings.spec.load(file);
        assertFalse(settings.enabled.get()); assertEquals(2, settings.steps.getAsInt());
        assertEquals(1.5, settings.delay.getAsDouble()); assertEquals(Renderer.VANILLA, settings.renderer.get());
        assertFalse(Files.readString(file).contains("99")); assertFalse(Files.readString(file).contains("nan"));
        assertThrows(IllegalArgumentException.class, () -> settings.steps.set(0));
        assertThrows(IllegalArgumentException.class, () -> settings.delay.set(Double.NaN));
        assertEquals(2, settings.steps.getAsInt());
    }

    @Test void reloadsDiskEditsAndDoesNotLeakValuesBetweenWorlds() throws Exception {
        var settings = new Settings(); Path first = directory.resolve("first/serverconfig/sable-server.toml");
        settings.spec.load(first); settings.steps.set(8); settings.spec.save();
        Files.writeString(first, Files.readString(first).replace("steps = 8", "steps = 3"));
        Files.setLastModifiedTime(first, FileTime.fromMillis(System.currentTimeMillis() + 2000));
        assertTrue(settings.spec.reloadIfChanged()); assertEquals(3, settings.steps.getAsInt());
        assertFalse(settings.spec.reloadIfChanged());
        settings.spec.reset(); assertNull(settings.spec.path()); assertEquals(2, settings.steps.getAsInt());
        settings.spec.load(directory.resolve("second/serverconfig/sable-server.toml"));
        assertEquals(2, settings.steps.getAsInt());
        var oldWorld = new Settings(); oldWorld.spec.load(first); assertEquals(3, oldWorld.steps.getAsInt());
    }

    @Test void supportsUnboundedRemovalLimitsAndRejectsDuplicateKeys() throws Exception {
        var builder = new ConfigSpec.Builder();
        var limit = builder.defineInRange("limit", 0.0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        var spec = builder.build(); var file = directory.resolve("sable-common.toml");
        spec.load(file); limit.set(Double.NEGATIVE_INFINITY); spec.save();
        limit.set(2.0); spec.load(file); assertEquals(Double.NEGATIVE_INFINITY, limit.getAsDouble());
        assertThrows(IllegalArgumentException.class, () -> builder.define("limit", true));
    }
}
