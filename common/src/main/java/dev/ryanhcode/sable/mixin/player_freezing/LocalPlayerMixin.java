package dev.ryanhcode.sable.mixin.player_freezing;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends Player implements PlayerFreezeExtension {

    public LocalPlayerMixin(final Level level, final BlockPos blockPos, final float f, final GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V"), cancellable = true)
    private void sable$freezeTicking(final CallbackInfo ci) {
        this.sable$tickStopFreezing();

        final UUID uuid = this.sable$getFrozenToSubLevel();

        if (uuid != null) {
            final SubLevelContainer container = SubLevelContainer.getContainer(this.level());
            assert container != null;
            final ClientSubLevel subLevel = (ClientSubLevel) container.getSubLevel(uuid);

            if (subLevel == null || !subLevel.isFinalized()) {
                ci.cancel();
                return;
            }

            this.sable$teleport();
            this.sable$freezeTo(null, null);
        }
    }
}
