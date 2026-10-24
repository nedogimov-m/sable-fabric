package dev.ryanhcode.sable.mixin.debug_render;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.ArrayList;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {

    @ModifyVariable(method = "extractLines", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    public List<String> sable$addDebugInfo(final List<String> original, @Local(argsOnly = true) final boolean left) {
        if (left) return original;
        final List<String> value = new ArrayList<>(original);
        final SubLevelContainer container = SubLevelContainer.getContainer(Minecraft.getInstance().level);

        value.add("");
        value.add(ChatFormatting.UNDERLINE + "Sable");
        if (container instanceof final ClientSubLevelContainer clientContainer) {
            clientContainer.addDebugInfo(value::add);
        }
        SubLevelRenderDispatcher.get().addDebugInfo(value::add);

        return value;
    }
}
