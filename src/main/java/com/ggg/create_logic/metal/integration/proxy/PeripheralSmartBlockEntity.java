package com.ggg.create_logic.metal.integration.proxy;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class PeripheralSmartBlockEntity extends SmartBlockEntity {
    public PeripheralSmartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
    public abstract String getPeripheralName();
    public abstract void onReceivePeripheral(AbstractMetalPeripheral peripheral);
}
