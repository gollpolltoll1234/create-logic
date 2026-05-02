package com.ggg.create_logic;

import com.ggg.create_logic.blockentities.ComputerBlockEntity;
import com.ggg.create_logic.blocks.ComputerBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ModMain.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModMain.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, ModMain.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModMain.MOD_ID);

    public static final DeferredBlock<ComputerBlock> COMPUTER_BLOCK = BLOCKS.register("computer",
            () -> new ComputerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f)));

    public static final DeferredItem<BlockItem> COMPUTER_ITEM = ITEMS.register("computer",
            () -> new BlockItem(COMPUTER_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ComputerBlockEntity>> COMPUTER_BE = BLOCK_ENTITIES.register("computer_be",
            () -> BlockEntityType.Builder.of(ComputerBlockEntity::new, COMPUTER_BLOCK.get()).build(null));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RSM_TAB = TABS.register(ModMain.MOD_ID + "_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + ModMain.MOD_ID))
            .icon(() -> COMPUTER_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(COMPUTER_ITEM.get());
            }).build());

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
    }
}
