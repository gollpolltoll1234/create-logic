package com.ggg.create_logic.redstone_link_custom;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.createmod.catnip.data.Couple;
import net.minecraft.world.level.Level;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class RedstoneLinkInjector implements IRedstoneLinkable {
    static enum Mode {
        TRANSMIT,
        RECEIVE;

        private Mode() {
        }
    }
    private RedstoneLinkInjector(SmartBlockEntity be,RedstoneLinkNetworkHandler.Frequency frequencyFirst, RedstoneLinkNetworkHandler.Frequency frequencyLast){
        this.frequencyFirst = frequencyFirst;
        this.frequencyLast = frequencyLast;
        blockEntity = be;
    }
    RedstoneLinkNetworkHandler.Frequency frequencyFirst;
    RedstoneLinkNetworkHandler.Frequency frequencyLast;
    private Mode mode;
    private IntSupplier transmission;
    private IntConsumer signalCallback;
    private int lastSend = 0;
    private final SmartBlockEntity blockEntity;
    private boolean dirty = false;
    @Override
    public int getTransmittedStrength() {
        if (mode != Mode.TRANSMIT) {
            return 0;
        }
        lastSend = transmission.getAsInt();
        return lastSend;
    }
    public void markDirty(){
        dirty = true;
    }
    public void markClean(){
        dirty = false;
    }
    public boolean isDirty(){
        return dirty;
    }
    public int getLastSend(){
        return lastSend;
    }

    @Override
    public void setReceivedStrength(int i) {
        this.signalCallback.accept(i);
    }

    private RedstoneLinkNetworkHandler getHandler() {
        return Create.REDSTONE_LINK_NETWORK_HANDLER;
    }
    public void notifySignalChange() {
        dirty = false;
        Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(blockEntity.getLevel(), this);
    }
    @Override
    public boolean isListening() {
        return this.mode == Mode.RECEIVE;
    }

    @Override
    public boolean isAlive() {
        Level level = blockEntity.getLevel();
        BlockPos pos = blockEntity.getBlockPos();
        if (level == null) {
            return false;
        }
        if (this.blockEntity.isChunkUnloaded()) {
            return false;
        } else if (this.blockEntity.isRemoved()) {
            return false;
        } else return level.isLoaded(pos);
    }

    @Override
    public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
        return Couple.create(this.frequencyFirst, this.frequencyLast);
    }


    @Override
    public BlockPos getLocation() {
        return blockEntity.getBlockPos();
    }
    public static RedstoneLinkInjector receiver(SmartBlockEntity be,RedstoneLinkNetworkHandler.Frequency frequencyFirst, RedstoneLinkNetworkHandler.Frequency frequencyLast, IntConsumer signalCallback) {
        RedstoneLinkInjector behaviour = new RedstoneLinkInjector(be,frequencyFirst, frequencyLast);
        behaviour.signalCallback = signalCallback;
        behaviour.mode = Mode.RECEIVE;
        return behaviour;
    }

    public static RedstoneLinkInjector transmitter(SmartBlockEntity be,RedstoneLinkNetworkHandler.Frequency frequencyFirst, RedstoneLinkNetworkHandler.Frequency frequencyLast, IntSupplier transmission) {
        RedstoneLinkInjector behaviour = new RedstoneLinkInjector(be,frequencyFirst, frequencyLast);
        behaviour.transmission = transmission;
        behaviour.mode = Mode.TRANSMIT;
        return behaviour;
    }
}
