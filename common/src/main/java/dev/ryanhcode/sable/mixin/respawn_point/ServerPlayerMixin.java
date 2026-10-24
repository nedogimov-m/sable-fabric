package dev.ryanhcode.sable.mixin.respawn_point;

import dev.ryanhcode.sable.Sable;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.mixinterface.respawn_point.ServerPlayerRespawnExtension;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements ServerPlayerRespawnExtension {

    @Shadow
    @Final
    public MinecraftServer server;
    @Shadow
    public ServerGamePacketListenerImpl connection;
    @Shadow
    private @Nullable ServerPlayer.RespawnConfig respawnConfig;
    @Unique
    private boolean sable$copyingRespawnPoint;
    @Unique
    @Nullable
    private UUID sable$respawnPoint = null;
    @Unique
    private Pair<UUID, Vector3d> sable$queuedFreeze = null;

    @Shadow
    public static Optional<ServerPlayer.RespawnPosAngle> findRespawnAndUseSpawnBlock(
            final ServerLevel level, final ServerPlayer.RespawnConfig config, final boolean consumeSpawnBlock) {
        throw new AssertionError("Mixin shadow method was not transformed");
    }

    @Shadow
    public abstract ServerLevel level();

    @Shadow
    public abstract void sendSystemMessage(Component component);

    @Override
    public @Nullable UUID sable$getRespawnPoint() {
        return this.sable$respawnPoint;
    }

    @Inject(method = "setRespawnPosition", at = @At("HEAD"))
    private void sable$setRespawnPosition(@Nullable final ServerPlayer.RespawnConfig config,
                                          final boolean sendMessage, final CallbackInfo ci) {
        if (this.sable$copyingRespawnPoint) return;
        if (this.sable$respawnPoint != null) {
            final ServerLevel oldLevel = this.respawnConfig == null ? this.level()
                    : this.server.getLevel(this.respawnConfig.respawnData().dimension());
            if (oldLevel != null) {
                SubLevelTrackingPointSavedData.getOrLoad(oldLevel).removeTrackingPoint(this.sable$respawnPoint);
            }
            this.sable$respawnPoint = null;
        }
        if (config == null) return;
        final ServerLevel level = this.server.getLevel(config.respawnData().dimension());
        if (level == null) return;
        final BlockPos position = config.respawnData().pos();
        final SubLevel subLevel = Sable.HELPER.getContaining(level, position);
        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            this.sable$respawnPoint = SubLevelTrackingPointSavedData.getOrLoad(level)
                    .generateTrackingPoint(Vec3.atCenterOf(position), serverSubLevel);
        }
        // Vanilla stores RespawnConfig and sends the spawn-set message itself.
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void sable$addRespawnPoint(final ValueOutput compoundTag, final CallbackInfo ci) {
        if (this.sable$respawnPoint != null) {
            compoundTag.store("RespawnPoint", net.minecraft.core.UUIDUtil.CODEC, this.sable$respawnPoint);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void sable$readRespawnPoint(final ValueInput compoundTag, final CallbackInfo ci) {
        this.sable$respawnPoint = compoundTag.read("RespawnPoint", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
    }

    @WrapMethod(method = "copyRespawnPosition")
    private void sable$copyRespawnPosition(final ServerPlayer oldPlayer, final Operation<Void> original) {
        this.sable$copyingRespawnPoint = true;
        try {
            original.call(oldPlayer);
        } finally {
            this.sable$copyingRespawnPoint = false;
        }
        this.sable$respawnPoint = oldPlayer.getRespawnConfig() == null ? null
                : ((ServerPlayerRespawnExtension) oldPlayer).sable$getRespawnPoint();
    }

    @Override
    public void sable$takeQueuedFreezeFrom(final ServerPlayer oldPlayer) {
        final ServerPlayerRespawnExtension extension = (ServerPlayerRespawnExtension) oldPlayer;
        final Pair<UUID, Vector3d> queuedFreeze = extension.sable$getQueuedFreeze();

        if (queuedFreeze != null) {
            ((PlayerFreezeExtension) this).sable$freezeTo(queuedFreeze.first(), queuedFreeze.second());
            this.connection.send(new ClientboundCustomPayloadPacket(new ClientboundFreezePlayerPacket(queuedFreeze.first(), queuedFreeze.second())));
        }
    }

    @Override
    public @Nullable Pair<UUID, Vector3d> sable$getQueuedFreeze() {
        return this.sable$queuedFreeze;
    }

    /**
     * @author RyanH
     * @reason Respawning on sub-levels
     */
    @Redirect(method = "findRespawnPositionAndUseSpawnBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;findRespawnAndUseSpawnBlock(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer$RespawnConfig;Z)Ljava/util/Optional;"))
    private Optional<ServerPlayer.RespawnPosAngle> sable$findRespawnPosition(final ServerLevel level,
            final ServerPlayer.RespawnConfig config, final boolean consumeSpawnBlock) {
        this.sable$queuedFreeze = null;
        final SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(level);

        if (this.sable$respawnPoint != null) {
            final SubLevelTrackingPointSavedData.TakenLoginPoint point = data.take(this.sable$respawnPoint, false);

            if (point == null) {
                this.sable$respawnPoint = null;
                return Optional.empty();
            }

            // TODO: do validation here

            if (point.subLevelId() != null) {
                this.sable$queuedFreeze = Pair.of(point.subLevelId(), point.localAnchor());
            }

            return Optional.of(new ServerPlayer.RespawnPosAngle(JOMLConversion.toMojang(point.position()),
                    config.respawnData().yaw(), config.respawnData().pitch()));
        }

        return findRespawnAndUseSpawnBlock(level, config, consumeSpawnBlock);
    }
}
