package dev.ryanhcode.sable;

import dev.ryanhcode.sable.config.ConfigSpec;

public final class SableServerConfig {

    public static final ConfigSpec SPEC;

    public static final ConfigSpec.IntValue SUB_LEVEL_SUBSTEPS_PER_TICK;
    public static final ConfigSpec.BooleanValue SUB_LEVEL_STORAGE_PRUNING;

    static {
        final ConfigSpec.Builder builder = new ConfigSpec.Builder();

        SUB_LEVEL_SUBSTEPS_PER_TICK = builder
                .comment("How many times the physics simulation is stepped in every second. Higher values will be significantly more performance intensive, but will have higher accuracy.")
                .defineInRange("sub_level_substeps_per_tick", 2, 1, 10);

        SUB_LEVEL_STORAGE_PRUNING = builder
                .comment("Prunes and deletes empty sub-level storage files. Experimental.")
                .define("sub_level_storage_pruning", false);

        SPEC = builder.build();
    }
}
