package ru.davidlitestudio.portabletent.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import ru.davidlitestudio.portabletent.PortableTentMod;

public class PortableTentClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WorldRenderEvents.LAST.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            PlayerEntity player = client.player;
            if (player == null) return;

            // Проверяем, держит ли игрок переносную палатку
            boolean holdingTent = player.getStackInHand(Hand.MAIN_HAND).getItem() == PortableTentMod.PORTABLE_TENT
                    || player.getStackInHand(Hand.OFF_HAND).getItem() == PortableTentMod.PORTABLE_TENT;

            if (!holdingTent) return;

            HitResult hitResult = client.crosshairTarget;
            if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                BlockPos targetPos = blockHitResult.getBlockPos();
                Direction side = blockHitResult.getSide();
                
                // Рассчитываем позицию спавна палатки
                BlockPos spawnPos;
                net.minecraft.block.BlockState targetState = player.world.getBlockState(targetPos);
                if (ru.davidlitestudio.portabletent.item.PortableTentItem.isReplaceable(targetState)) {
                    spawnPos = targetPos;
                } else {
                    spawnPos = targetPos.offset(side);
                }

                // Размеры палатки из файла spawn_tent-neww.nbt: 10 (ширина X), 5 (высота Y), 9 (глубина Z)
                double sizeX = 10.0;
                double sizeY = 5.0;
                double sizeZ = 9.0;

                double x1 = spawnPos.getX();
                double y1 = spawnPos.getY();
                double z1 = spawnPos.getZ();
                double x2 = x1 + sizeX;
                double y2 = y1 + sizeY;
                double z2 = z1 + sizeZ;

                MatrixStack matrices = context.matrixStack();
                Vec3d cameraPos = context.camera().getPos();

                matrices.push();
                // Перемещаем матрицу относительно камеры для рендеринга в мировых координатах
                matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

                VertexConsumer vertexConsumer = context.consumers().getBuffer(RenderLayer.getLines());

                // Рисуем серую полупрозрачную рамку (R=0.5, G=0.5, B=0.5, A=0.6)
                WorldRenderer.drawBox(matrices, vertexConsumer, x1, y1, z1, x2, y2, z2, 0.5f, 0.5f, 0.5f, 0.6f);

                matrices.pop();
            }
        });
    }
}
