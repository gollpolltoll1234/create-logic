package com.ggg.create_logic.metal.integration.computercraft;

import com.ggg.create_logic.metal.integration.proxy.AbstractMetalPeripheral;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BaseMetalPeripheral extends AbstractMetalPeripheral implements IPeripheral {
    private final List<@NotNull IComputerAccess> computers = new ArrayList<>();
    public BaseMetalPeripheral(SmartBlockEntity blockEntity) {
        super(blockEntity);
    }

    @Override
    public void remove() {
        if (blockEntity.getLevel() == null) return;
        blockEntity.getLevel().invalidateCapabilities(blockEntity.getBlockPos());
    }

    @Override
    public @NotNull String getType() {
        return "metal";
    }

    @Override
    public @Nullable Object getTarget() {
        return blockEntity;
    }
    @Override
    public void attach(@NotNull IComputerAccess computer) {
        synchronized (computers) {
            computers.add(computer);
        }
    }

    @Override
    public void detach(@NotNull IComputerAccess computer) {
        synchronized (computers) {
            computers.remove(computer);
        }
    }

    @Override
    public boolean equals(@Nullable IPeripheral iPeripheral) {
        if (iPeripheral instanceof BaseMetalPeripheral mp) {
            String otherType = iPeripheral.getType();
            if (!Objects.equals(otherType,getType())) return false;
            return getTarget() == iPeripheral.getTarget();
        } else return false;
    }
}
