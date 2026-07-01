package ru.davidlitestudio.portabletent.mixin;

import net.minecraft.block.piston.PistonHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.davidlitestudio.portabletent.PortableTentMod;

import java.util.List;

@Mixin(PistonHandler.class)
public class PistonHandlerMixin {
    @Shadow @Final private World world;
    @Shadow @Final private List<BlockPos> movedBlocks;
    @Shadow @Final private List<BlockPos> brokenBlocks;

    @Inject(method = "calculatePush", at = @At("RETURN"), cancellable = true)
    private void onCalculatePush(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            // Если поршень пытается сдвинуть или сломать блок палатки, отменяем движение
            for (BlockPos pos : this.movedBlocks) {
                if (PortableTentMod.isProtectedBlock(this.world, pos)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
            for (BlockPos pos : this.brokenBlocks) {
                if (PortableTentMod.isProtectedBlock(this.world, pos)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }
}
