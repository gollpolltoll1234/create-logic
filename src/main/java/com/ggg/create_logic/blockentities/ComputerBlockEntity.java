package com.ggg.create_logic.blockentities;

import com.ggg.create_logic.ModMain;
import com.ggg.create_logic.ModRegistry;
import com.ggg.create_logic.metal.Metal;
import com.ggg.create_logic.metal.integration.proxy.AbstractMetalPeripheral;
import com.ggg.create_logic.metal.integration.proxy.PeripheralSmartBlockEntity;
import com.ggg.create_logic.metal_modules.ConsoleModule;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;


public class ComputerBlockEntity extends PeripheralSmartBlockEntity {
    private final Metal metal;
    private List<ConsoleModule.ConsoleMessage> consoleMessages = null;
    private String sourceCode = "";
    private boolean consoleDirty = false;
    private boolean isActive = false;
    private AbstractMetalPeripheral peripheral;
    private boolean isRemoved = false;
    public ComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.COMPUTER_BE.get(), pos, state);
        this.metal = new Metal(this);
    }
    public List<ConsoleModule.ConsoleMessage> getConsoleMessages(){
        return consoleMessages;
    }
    public boolean isConsoleDirty(){
        return consoleDirty;
    }
    public void markConsoleClean(){
        consoleDirty = false;
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
        consoleMessages = null;
        notifyUpdate();
        metal.run();
    }
    public void stop(){
        isActive = false;
        consoleMessages = null;
        notifyUpdate();
        metal.stop();
    }
    @Override
    public void tick(){
        super.tick();
        if (metal.isRunning()) {
            if (metal.getModule("console") instanceof ConsoleModule consoleModule) {
                if (consoleModule.isDirty()) {
                    consoleMessages = consoleModule.getConsoleContent();
                    consoleModule.markClean();
                    notifyUpdate();
                }
            }
        }
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
    public boolean isActive(){
        return isActive;
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries,boolean isPacket) {
        super.write(nbt, registries, isPacket);
        nbt.putString("SourceCode", this.sourceCode);
        nbt.putBoolean("isActive",isActive);
        if (isPacket && consoleMessages != null && metal.isRunning()) {
            ListTag tags = new ListTag();
            for (ConsoleModule.ConsoleMessage msg : consoleMessages) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("id",msg.type().getId());
                tag.putString("content",msg.content());
                tags.add(tag);
            }
            nbt.put("out",tags);
        }
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
        if (clientPacket && nbt.contains("out")) {
            if (consoleMessages == null) {
                consoleMessages = new ArrayList<>();
            }
            consoleMessages.clear();
            ListTag tags = nbt.getList("out",CompoundTag.TAG_COMPOUND);
            for (Object object : tags.toArray()) {
                if (object instanceof CompoundTag tag) {
                    consoleMessages.add(new ConsoleModule.ConsoleMessage(ConsoleModule.MessageType.fromId(tag.getInt("id")),tag.getString("content")));
                }
            }
            consoleDirty = true;
        } else consoleDirty = false;
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