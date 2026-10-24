package dev.ryanhcode.sable.mixinterface.entity.entity_rendering;

import dev.ryanhcode.sable.mixinhelpers.entity.entity_rendering.EntityRenderTransform;
import dev.ryanhcode.sable.mixinhelpers.entity.entity_rendering.shadows.SubLevelEntityShadowRenderer;
import java.util.List;

public interface EntityRenderStateExtension {
    EntityRenderTransform sable$getRenderTransform();
    void sable$setRenderTransform(EntityRenderTransform transform);
    float sable$getShadowStrength();
    void sable$setShadowStrength(float strength);
    List<SubLevelEntityShadowRenderer.Vertex> sable$getShadowVertices();
    void sable$setShadowVertices(List<SubLevelEntityShadowRenderer.Vertex> vertices);
}
