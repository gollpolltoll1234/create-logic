package com.ggg.create_logic.metal.integration.proxy;

import com.ggg.create_logic.ModMain;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public abstract class AbstractMetalPeripheral {
    protected BlockEntity blockEntity;
    private final static Map<String, Function<PeripheralSmartBlockEntity, ? extends AbstractMetalPeripheral>> FABRICS = new HashMap<>();
    public static void addFabric(String name, Function<PeripheralSmartBlockEntity, ? extends AbstractMetalPeripheral> fabric) {
        if (!FABRICS.containsKey(name))
            FABRICS.put(name, fabric);
    }
    public static AbstractMetalPeripheral apply(String name, PeripheralSmartBlockEntity be) {
        if (!FABRICS.containsKey(name)) return null;
        return FABRICS.get(name).apply(be);
    }
    public static AbstractMetalPeripheral addBlockCapability(SmartBlockEntity be) {
        if (!ModList.get().isLoaded("computercraft")) {
            ModMain.LOGGER.info("Mod is not loaded");
            return null;
        }
        if (!(be instanceof PeripheralSmartBlockEntity peripheralSmartBlock)) {
            ModMain.LOGGER.info("Not a Peripheral block");
            return null;
        }
        AbstractMetalPeripheral peripheral = CCProxy.get(peripheralSmartBlock);
        if (peripheral instanceof com.ggg.create_logic.metal.integration.computercraft.BaseMetalPeripheral) return peripheral;
        ModMain.LOGGER.info("Not found: " + peripheralSmartBlock.getPeripheralName());
        return null;
    }
    public AbstractMetalPeripheral(SmartBlockEntity be) {
        blockEntity = be;
    }
    public abstract void remove();
}
