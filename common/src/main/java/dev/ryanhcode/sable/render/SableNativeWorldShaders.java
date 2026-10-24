package dev.ryanhcode.sable.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import dev.ryanhcode.sable.render.sky_light_shadow.SableDynamicSkyLightShadowPreProcessor;
import dev.ryanhcode.sable.render.sky_light_shadow.SableSkyLightShadows;
import dev.ryanhcode.sable.render.water_occlusion.SableWaterOcclusionPreProcessor;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import foundry.veil.api.client.render.shader.NativePipelineBridge;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.function.Supplier;

/** Typed augmentation of vanilla's actual immutable terrain/block pipelines. */
public final class SableNativeWorldShaders {
    private static final Set<Identifier> SOURCES = Set.of(Identifier.parse("minecraft:core/terrain"), Identifier.parse("minecraft:core/block"));
    private static final Set<Identifier> WATER_PIPELINES;
    static {
        Set<Identifier> pipelines = new HashSet<>();
        for (String mesh : List.of("terrain", "terrain_multidraw", "block")) pipelines.add(Identifier.parse("minecraft:pipeline/translucent_" + mesh));
        for (String phase : List.of("depth_bounds", "transmittance", "accumulate"))
            for (String mesh : List.of("terrain", "terrain_multidraw", "translucent_block")) pipelines.add(Identifier.parse("minecraft:pipeline/oit_" + phase + "_" + mesh));
        WATER_PIPELINES = Set.copyOf(pipelines);
    }
    private static final ThreadLocal<PreviewScope> PREVIEW = new ThreadLocal<>();
    /** Disable only world-only shader resources for the current render-thread preview. */
    public static PreviewScope disableWorldEffects() { return new PreviewScope(); }
    public static boolean worldEffectsDisabled() { return PREVIEW.get() != null; }
    public static final class PreviewScope implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final PreviewScope previous = PREVIEW.get();
        private boolean closed;
        private PreviewScope() { PREVIEW.set(this); }
        @Override public void close() {
            if (closed) return;
            if (Thread.currentThread()!=owner || PREVIEW.get()!=this) throw new IllegalStateException("Sable preview scopes must close in LIFO order on their owner thread");
            closed=true;
            if(previous==null)PREVIEW.remove();else PREVIEW.set(previous);
        }
    }
    private SableNativeWorldShaders() { }
    public static boolean isTerrainSource(Identifier source) { return SOURCES.contains(source); }
    public static boolean isWaterPipeline(RenderPipeline pipeline) { return WATER_PIPELINES.contains(pipeline.getLocation()); }
    public static void register(Supplier<WaterOcclusionRenderer.Bindings> water, Supplier<SableSkyLightShadows.Bindings> shadows) {
        NativePipelineBridge.register(Identifier.parse("sable:water_occlusion"), SableNativeWorldShaders::isWaterPipeline,
                Map.of(WaterOcclusionRenderer.PARAMETERS, UniformType.UNIFORM_BUFFER,
                        SableWaterOcclusionPreProcessor.CLOSE_SAMPLER_NAME, UniformType.COMBINED_IMAGE_SAMPLER,
                        SableWaterOcclusionPreProcessor.FAR_SAMPLER_NAME, UniformType.COMBINED_IMAGE_SAMPLER),
                SableWaterOcclusionPreProcessor::new, pipeline -> {
                    var value = Objects.requireNonNull(water.get(), "Prepare Sable water resources before opening native passes");
                    return new NativePipelineBridge.Bindings(Map.of(WaterOcclusionRenderer.PARAMETERS, value.parameters()),
                            Map.of(SableWaterOcclusionPreProcessor.CLOSE_SAMPLER_NAME, new NativePipelineBridge.Texture(value.entryDepth(), value.sampler()),
                                    SableWaterOcclusionPreProcessor.FAR_SAMPLER_NAME, new NativePipelineBridge.Texture(value.exitDepth(), value.sampler())));
                });
        NativePipelineBridge.register(Identifier.parse("sable:sky_light_shadows"), pipeline -> pipeline.getShaders().values().stream().anyMatch(SableNativeWorldShaders::isTerrainSource),
                Map.of(SableSkyLightShadows.PARAMETERS, UniformType.UNIFORM_BUFFER,
                        SableDynamicSkyLightShadowPreProcessor.SAMPLER_NAME, UniformType.COMBINED_IMAGE_SAMPLER),
                SableDynamicSkyLightShadowPreProcessor::new, pipeline -> {
                    var value = Objects.requireNonNull(shadows.get(), "Prepare Sable shadow resources before opening native passes");
                    return new NativePipelineBridge.Bindings(Map.of(SableSkyLightShadows.PARAMETERS, value.parameters()),
                            Map.of(SableDynamicSkyLightShadowPreProcessor.SAMPLER_NAME, new NativePipelineBridge.Texture(value.depth(), value.sampler())));
                });
    }
}
