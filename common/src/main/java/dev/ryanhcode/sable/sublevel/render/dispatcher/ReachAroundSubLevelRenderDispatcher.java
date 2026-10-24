package dev.ryanhcode.sable.sublevel.render.dispatcher;

/**
 * Sodium uses Sable-owned native CPU meshes, so plot geometry no longer depends on
 * Sodium's section compiler or the removed vanilla per-section GPU buffer API.
 */
public class ReachAroundSubLevelRenderDispatcher extends VanillaSubLevelRenderDispatcher {
}
