package dev.ryanhcode.sable.sublevel.render.dispatcher;

/**
 * Native snapshot backend for the experimental renderer entry point. The old GL
 * instancing path cannot execute on RenderPearl; native tessellation also preserves
 * arbitrary block and fluid geometry that the old cube-only path omitted.
 */
public class FancySubLevelRenderDispatcher extends VanillaSubLevelRenderDispatcher {
}
