package dev.ryanhcode.sable.mixin.entity.entity_rendering;

import dev.ryanhcode.sable.mixinhelpers.entity.entity_rendering.EntityRenderTransform;
import dev.ryanhcode.sable.mixinhelpers.entity.entity_rendering.shadows.SubLevelEntityShadowRenderer;
import java.util.List;
import dev.ryanhcode.sable.mixinterface.entity.entity_rendering.EntityRenderStateExtension;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements EntityRenderStateExtension {
    @Unique
    private EntityRenderTransform sable$renderTransform = EntityRenderTransform.EMPTY;
    @Unique private float sable$shadowStrength;
    @Unique private List<SubLevelEntityShadowRenderer.Vertex> sable$shadowVertices = List.of();

    @Override
    public EntityRenderTransform sable$getRenderTransform() {
        return this.sable$renderTransform;
    }

    @Override
    public void sable$setRenderTransform(final EntityRenderTransform transform) {
        this.sable$renderTransform = transform;
    }

    @Override public float sable$getShadowStrength() { return this.sable$shadowStrength; }
    @Override public void sable$setShadowStrength(final float strength) { this.sable$shadowStrength = strength; }
    @Override public List<SubLevelEntityShadowRenderer.Vertex> sable$getShadowVertices() { return this.sable$shadowVertices; }
    @Override public void sable$setShadowVertices(final List<SubLevelEntityShadowRenderer.Vertex> vertices) { this.sable$shadowVertices = vertices; }
}
