package ru.davidlitestudio.portabletent;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.davidlitestudio.portabletent.item.PortableTentItem;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PortableTentMod implements ModInitializer {
    public static final String MOD_ID = "portabletentmod";
    public static final Logger LOGGER = LogManager.getLogger("Portable Tent Mod");

    // Единственный предмет мода во вкладке ItemGroup.MISC
    public static final Item PORTABLE_TENT = new PortableTentItem(new Item.Settings().group(ItemGroup.MISC));

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Portable Tent Mod...");

        // Регистрация предмета
        Registry.register(Registry.ITEM, new Identifier(MOD_ID, "portable_tent"), PORTABLE_TENT);

        LOGGER.info("Portable Tent Mod item registered successfully!");

        // Регистрация коллбека взаимодействия с блоками для механики разборки палатки (Shift + ПКМ)
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) {
                return ActionResult.PASS;
            }

            // Обрабатываем клик только основной руки, чтобы избежать двойного вызова
            if (hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }

            // Игрок должен приседать (Shift)
            if (!player.isSneaking()) {
                return ActionResult.PASS;
            }

            BlockPos clickedPos = hitResult.getBlockPos();
            net.minecraft.block.BlockState state = world.getBlockState(clickedPos);

            // Если кликнули по лампе (фонарю)
            if (state.getBlock() == net.minecraft.block.Blocks.LANTERN) {
                BlockPos origin = findTentOrigin(world, clickedPos);
                if (origin != null) {
                    // Проверяем, является ли игрок владельцем этой палатки
                    PortableTentState tentState = getOrCreateState((ServerWorld) world);
                    UUID ownerUuid = tentState.getOwnerOf(origin);
                    if (ownerUuid != null && ownerUuid.equals(player.getUuid())) {
                        disassembleTent(world, origin, player);
                        return ActionResult.SUCCESS;
                    }
                }
            }

            return ActionResult.PASS;
        });

        // Защита от разрушения блоков палатки игроками в режиме выживания
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player.isCreative()) {
                return true;
            }
            if (isProtectedBlock(world, pos)) {
                return false; // Отменяем попытку разрушить блок
            }
            return true;
        });
    }

    /**
     * Возвращает true, если переданные координаты блока находятся внутри одной из активных палаток.
     */
    public static boolean isProtectedBlock(World world, BlockPos pos) {
        if (world.isClient) {
            return false;
        }
        if (!(world instanceof ServerWorld)) {
            return false;
        }
        ServerWorld serverWorld = (ServerWorld) world;
        PortableTentState state = getOrCreateState(serverWorld);
        
        for (BlockPos origin : state.getOrigins()) {
            if (pos.getX() >= origin.getX() && pos.getX() < origin.getX() + 10
                    && pos.getY() >= origin.getY() && pos.getY() < origin.getY() + 5
                    && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + 9) {
                return true;
            }
        }
        return false;
    }

    /**
     * Получает или создает состояние активных палаток в текущем мире.
     */
    public static PortableTentState getOrCreateState(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
            () -> new PortableTentState("portabletent_origins"),
            "portabletent_origins"
        );
    }

    /**
     * Вычисляет координаты начала структуры (origin) на основе кликнутого фонаря.
     */
    private static BlockPos findTentOrigin(World world, BlockPos lanternPos) {
        BlockPos test1 = lanternPos.add(-1, -2, -4);
        BlockPos test2 = lanternPos.add(-6, -2, -4);

        int count1 = countTentBlocks(world, test1);
        int count2 = countTentBlocks(world, test2);

        if (count1 >= 5 && count1 >= count2) {
            return test1;
        } else if (count2 >= 5 && count2 > count1) {
            return test2;
        }
        return null;
    }

    /**
     * Считает количество блоков палатки в предполагаемой области 10x5x9.
     */
    private static int countTentBlocks(World world, BlockPos origin) {
        int matches = 0;
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 9; z++) {
                    BlockPos p = origin.add(x, y, z);
                    net.minecraft.block.Block block = world.getBlockState(p).getBlock();
                    if (block == net.minecraft.block.Blocks.WHITE_WOOL 
                            || block == net.minecraft.block.Blocks.OAK_FENCE
                            || block == net.minecraft.block.Blocks.LANTERN
                            || block == net.minecraft.block.Blocks.CAMPFIRE
                            || block == net.minecraft.block.Blocks.BARREL
                            || block == net.minecraft.block.Blocks.CHEST
                            || block instanceof net.minecraft.block.BedBlock) {
                        matches++;
                    }
                }
            }
        }
        return matches;
    }

    /**
     * Разбирает палатку: удаляет ее блоки, высыпает содержимое контейнеров и возвращает предмет палатки.
     */
    public static void disassembleTent(World world, BlockPos origin, PlayerEntity player) {
        LOGGER.info("Disassembling portable tent at origin: " + origin);

        // Удаляем из списка защищенных зон перед началом разрушения блоков
        UUID ownerUuid = getOrCreateState((ServerWorld) world).getOwnerOf(origin);
        if (ownerUuid != null) {
            getOrCreateState((ServerWorld) world).removePlayerTent(ownerUuid);
        }

        // Очищаем блоки палатки в области 10x5x9
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 9; z++) {
                    BlockPos p = origin.add(x, y, z);
                    net.minecraft.block.BlockState state = world.getBlockState(p);
                    net.minecraft.block.Block block = state.getBlock();

                    // Если блок является кроватью (BedBlock)
                    if (block instanceof net.minecraft.block.BedBlock) {
                        net.minecraft.state.property.Property<net.minecraft.block.enums.BedPart> partProperty = net.minecraft.block.BedBlock.PART;
                        net.minecraft.block.enums.BedPart part = state.get(partProperty);
                        net.minecraft.util.math.Direction facing = state.get(net.minecraft.block.BedBlock.FACING);

                        BlockPos otherPos = (part == net.minecraft.block.enums.BedPart.FOOT) 
                                ? p.offset(facing) 
                                : p.offset(facing.getOpposite());

                        world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), 2 | 16);
                        world.setBlockState(otherPos, net.minecraft.block.Blocks.AIR.getDefaultState(), 2 | 16);
                        continue;
                    }

                    // Удаляем только характерные для палатки блоки
                    if (block == net.minecraft.block.Blocks.WHITE_WOOL
                            || block == net.minecraft.block.Blocks.OAK_FENCE
                            || block == net.minecraft.block.Blocks.LANTERN
                            || block == net.minecraft.block.Blocks.CAMPFIRE
                            || block == net.minecraft.block.Blocks.CHEST
                            || block == net.minecraft.block.Blocks.BARREL) {

                        net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(p);
                        if (be instanceof net.minecraft.inventory.Inventory) {
                            ItemScatterer.spawn(world, p, (net.minecraft.inventory.Inventory) be);
                        }

                        world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
        }

        // Возвращаем предмет переносной палатки игроку
        ItemStack tentStack = new ItemStack(PORTABLE_TENT);
        if (!player.inventory.insertStack(tentStack)) {
            player.dropItem(tentStack, false);
        }
    }

    /**
     * Класс для персистентного хранения координат всех активных палаток в мире с привязкой к UUID игроков.
     */
    public static class PortableTentState extends PersistentState {
        private final Map<UUID, BlockPos> playerTents = new HashMap<>();

        public PortableTentState(String key) {
            super(key);
        }

        @Override
        public void fromTag(NbtCompound tag) {
            playerTents.clear();
            if (tag.contains("player_tents", 9)) {
                NbtList list = tag.getList("player_tents", 10);
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound entry = list.getCompound(i);
                    UUID uuid = UUID.fromString(entry.getString("uuid"));
                    BlockPos pos = NbtHelper.toBlockPos(entry.getCompound("pos"));
                    playerTents.put(uuid, pos);
                }
            }
        }

        @Override
        public NbtCompound writeNbt(NbtCompound tag) {
            NbtList list = new NbtList();
            for (Map.Entry<UUID, BlockPos> entry : playerTents.entrySet()) {
                NbtCompound entryTag = new NbtCompound();
                entryTag.putString("uuid", entry.getKey().toString());
                entryTag.put("pos", NbtHelper.fromBlockPos(entry.getValue()));
                list.add(entryTag);
            }
            tag.put("player_tents", list);
            return tag;
        }

        public java.util.Collection<BlockPos> getOrigins() {
            return playerTents.values();
        }

        public BlockPos getPlayerTent(UUID uuid) {
            return playerTents.get(uuid);
        }

        public void setPlayerTent(UUID uuid, BlockPos pos) {
            playerTents.put(uuid, pos);
            markDirty();
        }

        public void removePlayerTent(UUID uuid) {
            playerTents.remove(uuid);
            markDirty();
        }

        public UUID getOwnerOf(BlockPos pos) {
            for (Map.Entry<UUID, BlockPos> entry : playerTents.entrySet()) {
                if (entry.getValue().equals(pos)) {
                    return entry.getKey();
                }
            }
            return null;
        }
    }
}
