package dev.ryanhcode.sable.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Validated flat TOML settings shared by the Fabric client and server lifecycles. */
public final class ConfigSpec {
    private final Map<String, Value<?>> values;
    private Path path;
    private FileTime modified;
    private long size = -1;

    private ConfigSpec(Map<String, Value<?>> values) { this.values = new LinkedHashMap<>(values); }

    public synchronized Path path() { return this.path; }

    public synchronized void load(Path file) {
        this.path = file.toAbsolutePath().normalize();
        var parsed = new LinkedHashMap<String, String>();
        boolean correct = !Files.isRegularFile(this.path);
        try {
            if (!correct) {
                for (String line : Files.readAllLines(this.path, StandardCharsets.UTF_8)) {
                    String text = line.strip();
                    if (text.isEmpty() || text.startsWith("#")) continue;
                    int equals = text.indexOf('=');
                    if (equals < 1) continue;
                    String raw = text.substring(equals + 1).strip();
                    int comment = raw.indexOf('#');
                    if (comment >= 0) raw = raw.substring(0, comment).strip();
                    parsed.put(text.substring(0, equals).strip(), raw);
                }
            }
            for (var entry : this.values.entrySet()) {
                Value<?> value = entry.getValue();
                String raw = parsed.get(entry.getKey());
                if (raw == null || !value.read(raw)) { value.reset(); correct = true; }
            }
            if (correct) this.save(); else this.rememberFile();
        } catch (IOException failure) { throw new UncheckedIOException("Could not read Sable configuration " + this.path, failure); }
    }

    public synchronized boolean reloadIfChanged() {
        if (this.path == null) return false;
        try {
            if (!Files.isRegularFile(this.path) || !Files.getLastModifiedTime(this.path).equals(this.modified)
                    || Files.size(this.path) != this.size) {
                this.load(this.path);
                return true;
            }
            return false;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    public synchronized void save() {
        if (this.path == null) throw new IllegalStateException("Configuration is not bound to a file");
        Path temporary = null;
        try {
            Files.createDirectories(this.path.getParent());
            var text = new StringBuilder("# Sable Fabric configuration\n\n");
            this.values.forEach((key, value) -> {
                for (String comment : value.comments) text.append("# ").append(comment).append('\n');
                text.append(key).append(" = ").append(value.encode()).append("\n\n");
            });
            temporary = Files.createTempFile(this.path.getParent(), ".sable-config-", ".tmp");
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try { Files.move(temporary, this.path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING); }
            this.rememberFile();
        } catch (IOException failure) { throw new UncheckedIOException("Could not save Sable configuration " + this.path, failure); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) {} }
    }

    /** Release a stopped world's settings without carrying its values into the next world. */
    public synchronized void reset() {
        this.values.values().forEach(Value::reset);
        this.path = null; this.modified = null; this.size = -1;
    }

    private void rememberFile() throws IOException {
        this.modified = Files.getLastModifiedTime(this.path); this.size = Files.size(this.path);
    }

    public record Range<T extends Number>(T min, T max) {
        public T getMin() { return min; }
        public T getMax() { return max; }
    }

    public abstract static class Value<T> implements Supplier<T> {
        private final T defaultValue;
        private final List<String> comments;
        protected volatile T value;
        private Value(T defaultValue, List<String> comments) {
            this.defaultValue = defaultValue; this.value = defaultValue; this.comments = List.copyOf(comments);
        }
        @Override public final T get() { return this.value; }
        public final void set(T value) {
            if (value == null || !this.accepts(value)) throw new IllegalArgumentException("Invalid setting value: " + value);
            this.value = value;
        }
        private void reset() { this.value = this.defaultValue; }
        private boolean read(String raw) {
            try { this.set(this.decode(raw)); return true; }
            catch (IllegalArgumentException ignored) { return false; }
        }
        protected abstract T decode(String raw);
        protected abstract boolean accepts(T value);
        protected String encode() { return this.value.toString(); }
    }

    public static final class BooleanValue extends Value<Boolean> implements BooleanSupplier {
        private BooleanValue(boolean value, List<String> comments) { super(value, comments); }
        @Override public boolean getAsBoolean() { return this.value; }
        @Override protected Boolean decode(String raw) {
            if (!raw.equals("true") && !raw.equals("false")) throw new IllegalArgumentException(raw);
            return Boolean.valueOf(raw);
        }
        @Override protected boolean accepts(Boolean value) { return true; }
    }

    public static final class IntValue extends Value<Integer> implements IntSupplier {
        private final Range<Integer> range;
        private IntValue(int value, int min, int max, List<String> comments) { super(value, comments); this.range = new Range<>(min, max); }
        @Override public int getAsInt() { return this.value; }
        public IntValue getSpec() { return this; }
        public Range<Integer> getRange() { return this.range; }
        @Override protected Integer decode(String raw) { return Integer.valueOf(raw.replace("_", "")); }
        @Override protected boolean accepts(Integer value) { return value >= range.min && value <= range.max; }
    }

    public static final class DoubleValue extends Value<Double> implements DoubleSupplier {
        private final Range<Double> range;
        private DoubleValue(double value, double min, double max, List<String> comments) { super(value, comments); this.range = new Range<>(min, max); }
        @Override public double getAsDouble() { return this.value; }
        @Override protected Double decode(String raw) {
            return switch (raw) { case "inf", "+inf" -> Double.POSITIVE_INFINITY; case "-inf" -> Double.NEGATIVE_INFINITY;
                default -> Double.valueOf(raw.replace("_", "")); };
        }
        @Override protected boolean accepts(Double value) { return !Double.isNaN(value) && value >= range.min && value <= range.max; }
        @Override protected String encode() { return this.value == Double.POSITIVE_INFINITY ? "+inf" : this.value == Double.NEGATIVE_INFINITY ? "-inf" : super.encode(); }
    }

    public static final class EnumValue<E extends Enum<E>> extends Value<E> {
        private final Class<E> type;
        private final Set<E> allowed;
        private EnumValue(E value, E[] allowed, List<String> comments) { super(value, comments); this.type = value.getDeclaringClass(); this.allowed = Set.copyOf(Arrays.asList(allowed)); }
        @Override protected E decode(String raw) {
            if (raw.length() >= 2 && ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'")))) raw = raw.substring(1, raw.length() - 1);
            return Enum.valueOf(type, raw);
        }
        @Override protected boolean accepts(E value) { return allowed.contains(value); }
        @Override protected String encode() { return "\"" + this.value.name() + "\""; }
    }

    public static final class Builder {
        private final Map<String, Value<?>> values = new LinkedHashMap<>();
        private final List<String> comments = new ArrayList<>();
        public Builder comment(String... comments) { this.comments.addAll(Arrays.asList(comments)); return this; }
        public BooleanValue define(String name, boolean value) { return this.add(name, new BooleanValue(value, this.comments)); }
        public IntValue defineInRange(String name, int value, int min, int max) {
            if (min > max || value < min || value > max) throw new IllegalArgumentException(name);
            return this.add(name, new IntValue(value, min, max, this.comments));
        }
        public DoubleValue defineInRange(String name, double value, double min, double max) {
            if (Double.isNaN(value) || min > max || value < min || value > max) throw new IllegalArgumentException(name);
            return this.add(name, new DoubleValue(value, min, max, this.comments));
        }
        public <E extends Enum<E>> EnumValue<E> defineEnum(String name, E value, E[] allowed) {
            if (!Arrays.asList(allowed).contains(value)) throw new IllegalArgumentException(name);
            return this.add(name, new EnumValue<>(value, allowed, this.comments));
        }
        private <T extends Value<?>> T add(String name, T value) {
            if (this.values.putIfAbsent(name, value) != null) throw new IllegalArgumentException("Duplicate setting " + name);
            this.comments.clear(); return value;
        }
        public ConfigSpec build() { return new ConfigSpec(this.values); }
    }
}
