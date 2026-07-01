package ru.davidlitestudio.portabletent.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.davidlitestudio.portabletent.PortableTentMod;

import java.util.List;

@Mixin(Explosion.class)
public class ExplosionMixin {
    @Shadow @Final private World world;
    @Shadow @Final private List<BlockPos> affectedBlocks;

    @Inject(method = "affectWorld", at = @At("HEAD"))
    private void onAffectWorld(boolean particles, CallbackInfo ci) {
        // Удаляем защищенные блоки палатки из списка разрушаемых взрывом
        affectedBlocks.removeIf(pos -> PortableTentMod.isProtectedBlock(world, pos));
    }
}
