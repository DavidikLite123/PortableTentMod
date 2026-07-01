package ru.davidlitestudio.portabletent.item;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.Structure;
import net.minecraft.structure.StructureManager;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.davidlitestudio.portabletent.PortableTentMod;

import java.util.Random;

public class PortableTentItem extends Item {
    public PortableTentItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (!world.isClient) {
            ServerWorld serverWorld = (ServerWorld) world;
            
            // Находим позицию спавна
            BlockPos clickedPos = context.getBlockPos();
            BlockState clickedState = world.getBlockState(clickedPos);
            BlockPos spawnPos;

            // Если кликнули по заменяемому блоку (снег, трава, цветы, ковры), ставим палатку прямо на его место
            if (isReplaceable(clickedState)) {
                spawnPos = clickedPos;
            } else {
                spawnPos = clickedPos.offset(context.getSide());
            }

            StructureManager structureManager = serverWorld.getStructureManager();
            Structure structure = structureManager.getStructure(new Identifier(PortableTentMod.MOD_ID, "spawn_tent-neww"));

            if (structure != null) {
                PlayerEntity player = context.getPlayer();
                
                // ОГРАНИЧЕНИЕ: Одна активная палатка на игрока (Авто-сворачивание старой палатки)
                if (player != null) {
                    PortableTentMod.PortableTentState state = PortableTentMod.getOrCreateState(serverWorld);
                    BlockPos oldTent = state.getPlayerTent(player.getUuid());
                    if (oldTent != null) {
                        PortableTentMod.LOGGER.info("Auto-folding previous tent for player: " + player.getName().getString());
                        PortableTentMod.disassembleTent(serverWorld, oldTent, player);
                    }
                }

                StructurePlacementData placementData = new StructurePlacementData()
                        .setRotation(BlockRotation.NONE)
                        .setMirror(BlockMirror.NONE)
                        .setIgnoreEntities(false);

                // Спавним структуру в мире
                structure.place(serverWorld, spawnPos, new BlockPos(0, 0, 0), placementData, new Random(), 3);

                // Регистрируем координаты новой палатки для этого игрока
                if (player != null) {
                    PortableTentMod.getOrCreateState(serverWorld).setPlayerTent(player.getUuid(), spawnPos);
                }

                // Удаляем технические структурные блоки из палатки
                BlockPos size = structure.getSize();
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                            BlockPos currentPos = spawnPos.add(x, y, z);
                            if (serverWorld.getBlockState(currentPos).isOf(net.minecraft.block.Blocks.STRUCTURE_BLOCK)) {
                                serverWorld.setBlockState(currentPos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                            }
                        }
                    }
                }

                // Уменьшаем стек предмета в руке игрока, если он не в креативе
                if (player != null && !player.isCreative()) {
                    context.getStack().decrement(1);
                }

                return ActionResult.SUCCESS;
            } else {
                PortableTentMod.LOGGER.error("Could not find structure file: data/{}/structures/spawn_tent-neww.nbt", PortableTentMod.MOD_ID);
                return ActionResult.FAIL;
            }
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Возвращает true, если блок является заменяемым (снег, трава, папоротники, цветы, ковры).
     */
    public static boolean isReplaceable(BlockState state) {
        net.minecraft.block.Block block = state.getBlock();
        return state.getMaterial().isReplaceable()
                || block instanceof net.minecraft.block.CarpetBlock
                || block instanceof net.minecraft.block.FlowerBlock
                || block instanceof net.minecraft.block.FernBlock
                || block instanceof net.minecraft.block.TallFlowerBlock
                || block == net.minecraft.block.Blocks.SNOW
                || block == net.minecraft.block.Blocks.GRASS
                || block == net.minecraft.block.Blocks.TALL_GRASS;
    }
}
