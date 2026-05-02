package com.ggg.create_logic.blockentities;

import com.ggg.create_logic.ModMain;
import com.ggg.create_logic.ModRegistry;
import com.ggg.create_logic.metal.Metal;
import com.ggg.create_logic.metal.integration.proxy.AbstractMetalPeripheral;
import com.ggg.create_logic.metal.integration.proxy.PeripheralSmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;


public class ComputerBlockEntity extends PeripheralSmartBlockEntity {
    private final Metal metal;
    private String sourceCode = "";
    private boolean isActive = false;
    private AbstractMetalPeripheral peripheral;
    private boolean isRemoved = false;
    public ComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.COMPUTER_BE.get(), pos, state);
        this.metal = new Metal(this);
    }
    public String getSourceCode(){
        return sourceCode;
    }
    public void setSourceCode(String sourceCode){
        this.sourceCode = sourceCode;
        setChanged();
        if (level == null || level.isClientSide) return;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        boolean wasRunning = metal.isRunning();
        if (wasRunning) {
            metal.stop();
        }
        metal.setCode(sourceCode);
        if (wasRunning) {
            metal.run();
        }
    }
    public void run(){
        isActive = true;
        metal.run();
    }
    public void stop(){
        isActive = false;
        metal.stop();
    }
    @Override
    public void tick(){
        super.tick();
        if (level == null || level.isClientSide || isRemoved) return;
        if (!metal.isRunning() && isActive) {
            metal.run();
        }
        if (metal.isRunning() && !isActive) {
            metal.stop();
        }
    }
    public Metal getMetal(){
        return metal;
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries,boolean isPacket) {
        super.write(nbt, registries, isPacket);
        nbt.putString("SourceCode", this.sourceCode);
        nbt.putBoolean("isActive",isActive);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> list) {
    }
    @Override
    public void invalidate() {
        isRemoved = true;
        metal.stop();
        if (peripheral != null) peripheral.remove();
        super.invalidate();
    }


    @Override
    protected void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        this.sourceCode = nbt.getString("SourceCode");
        this.isActive = nbt.getBoolean("isActive");
        if (!this.sourceCode.isEmpty()) {
            this.metal.setCode(this.sourceCode);
        }
    }

    public static void tickAll(Level level, BlockPos blockPos, BlockState blockState, ComputerBlockEntity computerBlockEntity) {
        if (level.isClientSide)return;
        computerBlockEntity.tick();
    }

    @Override
    public String getPeripheralName() {
        return ModMain.MOD_ID + ":metal_computer";
    }

    @Override
    public void onReceivePeripheral(AbstractMetalPeripheral peripheral) {
        this.peripheral = peripheral;
    }
}