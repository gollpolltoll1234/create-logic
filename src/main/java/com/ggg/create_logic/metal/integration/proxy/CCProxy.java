package com.ggg.create_logic.metal.integration.proxy;

import com.simibubi.create.compat.Mods;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import java.util.function.BiFunction;

public class CCProxy {
    public static class FallbackPeripheral extends AbstractMetalPeripheral {
        public FallbackPeripheral(SmartBlockEntity be) {
            super(be);
        }

        @Override
        public void remove() {}
    }
    public static void register() {
        fallbackFactory = (name,be) -> new FallbackPeripheral(be);
        Mods.COMPUTERCRAFT.executeIfInstalled(() -> CCProxy::registerWithDependency);
    }
    private static BiFunction<String, PeripheralSmartBlockEntity, ? extends AbstractMetalPeripheral> fallbackFactory;
    private static BiFunction<String, PeripheralSmartBlockEntity, ? extends  AbstractMetalPeripheral> computerFactory;
    private static void registerWithDependency(){
        computerFactory = AbstractMetalPeripheral::apply;
    }
    public static AbstractMetalPeripheral get(PeripheralSmartBlockEntity be){
        if (computerFactory == null) {
            return fallbackFactory.apply(be.getPeripheralName(), be);
        } else return computerFactory.apply(be.getPeripheralName(), be);
    }
}
