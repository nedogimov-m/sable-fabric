package dev.ryanhcode.sable.mixin.stop_rain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.mixinhelpers.stop_rain.SubLevelWeatherHeight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @WrapOperation(method = "tickWeatherEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos sable$stopSplashParticles(final ClientLevel level, final Heightmap.Types types,
                                              final BlockPos pos, final Operation<BlockPos> original) {
        final int height = Math.max(original.call(level, types, pos).getY(),
                SubLevelWeatherHeight.getSubLevelHeight(level, pos.getX(), 2, pos.getZ()));
        return new BlockPos(pos.getX(), height, pos.getZ());
    }
}
