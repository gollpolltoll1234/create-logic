package com.ggg.create_logic.metal.integration.computercraft;

import com.ggg.create_logic.ModMain;
import com.ggg.create_logic.ModRegistry;
import com.ggg.create_logic.metal.integration.proxy.AbstractMetalPeripheral;
import com.ggg.create_logic.metal.integration.proxy.CCProxy;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class ComputerCraftIntegration {
    public static void init(IEventBus bus) {
        CCProxy.register();
        AbstractMetalPeripheral.addFabric(ModMain.computerPeripheralName,MetalPeripheral::new);
        bus.addListener(ComputerCraftIntegration::registerCapabilities);
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (ModList.get().isLoaded("computercraft")) event.registerBlockEntity(
                dan200.computercraft.api.peripheral.PeripheralCapability.get(),
                ModRegistry.COMPUTER_BE.get(),
                (blockEntity, side) -> (com.ggg.create_logic.metal.integration.computercraft.BaseMetalPeripheral) AbstractMetalPeripheral.addBlockCapability(blockEntity)
        );
    }
}
