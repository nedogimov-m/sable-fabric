package dev.ryanhcode.sable.mixin.entity.entities_turn_with_sub_levels;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow @Final private Minecraft minecraft;

    @Unique
    private final Quaterniond sable$lastOrientation = new Quaterniond();

    @Unique
    private final Quaterniond sable$relativeOrientation = new Quaterniond();

    @Unique
    private UUID sable$lastSubLevel = null;

    @Unique
    private LocalPlayer sable$lastPlayer;

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void sable$updateStandingRotation(final DeltaTracker deltaTracker, final CallbackInfo ci) {
        final LocalPlayer player = this.minecraft.player;
        // Native update also runs on loading/title frames, before Camera.update.
        // Retire the previous world sample before touching the entity helper.
        if (player == null || this.minecraft.level == null || player.level() != this.minecraft.level) {
            this.sable$resetStandingRotation();
            return;
        }
        final SubLevel standingSubLevel = Sable.HELPER.getTrackingSubLevel(player);

        if (standingSubLevel instanceof final ClientSubLevel clientSubLevel && player.getVehicle() == null && !standingSubLevel.isRemoved() && !EntitySubLevelUtil.hasCustomEntityOrientation(player)) {
            final Quaterniondc current = clientSubLevel.renderPose().orientation();

            if (this.sable$lastPlayer != player || this.sable$lastSubLevel == null || !this.sable$lastSubLevel.equals(standingSubLevel.getUniqueId())) {
                this.sable$lastOrientation.set(current);
                this.sable$lastSubLevel = standingSubLevel.getUniqueId();
                this.sable$lastPlayer = player;
            }

            final Quaterniond relativeOrientation;
            final Quaterniondc customOrientation = EntitySubLevelUtil.getCustomEntityOrientation(player, 1.0f);
            if (customOrientation != null) {
                final Quaterniond inverseCustom = new Quaterniond(customOrientation).conjugate();
                final Quaterniond currentLocal = current.premul(inverseCustom, new Quaterniond());
                final Quaterniond lastLocal = this.sable$lastOrientation.premul(inverseCustom, new Quaterniond());
                relativeOrientation = currentLocal.div(lastLocal, this.sable$relativeOrientation);
            } else {
                current.div(this.sable$lastOrientation, this.sable$relativeOrientation);
                relativeOrientation = current.div(this.sable$lastOrientation, this.sable$relativeOrientation);
            }

            final double angleDiff = 2 * relativeOrientation.y / relativeOrientation.w;
            final float delta = (float) Math.toDegrees(angleDiff);

            player.yBodyRot -= delta;
            player.yBodyRotO -= delta;
            player.yHeadRot -= delta;
            player.yHeadRotO -= delta;
            player.setYRot(player.getYRot() - delta);
            player.yRotO -= delta;

            this.sable$lastOrientation.set(current);
        } else {
            this.sable$resetStandingRotation();
        }
    }

    @Inject(method = "resetData", at = @At("HEAD"))
    private void sable$resetStandingRotation(final CallbackInfo ci) {
        this.sable$resetStandingRotation();
    }

    @Unique
    private void sable$resetStandingRotation() {
        this.sable$lastSubLevel = null;
        this.sable$lastPlayer = null;
        this.sable$lastOrientation.identity();
        this.sable$relativeOrientation.identity();
    }
}
