package com.ggg.create_logic.metal;

import com.ggg.create_logic.metal.Metal.Script;
import com.ggg.create_logic.metal.Metal.MetalVariable;
import com.ggg.create_logic.redstone_link_custom.RedstoneLinkInjector;
import com.ggg.create_logic.metal_modules.AdditionalModule;
import com.ggg.create_logic.blockentities.ComputerBlockEntity;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

public class RedstoneLinker implements MetalObject {
    private RedstoneLinkInjector injector = null;
    private AtomicInteger power = new AtomicInteger(0);
    private boolean working = false;
    private final Metal metal;
    public RedstoneLinker(Metal metal, ItemStack s1, ItemStack s2, boolean isReceiver){
        this.metal = metal;
        RedstoneLinkNetworkHandler.Frequency f1 = RedstoneLinkNetworkHandler.Frequency.of(s1);
        RedstoneLinkNetworkHandler.Frequency f2 = RedstoneLinkNetworkHandler.Frequency.of(s2);
        if (metal.getOwner() instanceof ComputerBlockEntity computer) this.injector = (isReceiver ? RedstoneLinkInjector.receiver(computer,f1,f2,i -> {
            power.set(i);
        }) : RedstoneLinkInjector.transmitter(computer, f1,f2,() -> {
            return power.get();
        }));
    }
    public void connect(){
        if (working || injector == null) return;
        if (metal.getOwner() instanceof ComputerBlockEntity computer) 
        {
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(computer.getLevel(),injector);
            working = true;
        }
    }
    @CallableMetalFunction
    public MetalVariable receive(List<MetalVariable> args, Script script){
        if (!working) return MetalVariable.fromPower(0);
        if (injector.isListening()) return MetalVariable.fromPower(power.get());
        AdditionalModule.hardError(script,"This is a Transmitter, not a Receiver!", true);
        return MetalVariable.fromBool(false);
    }
    @CallableMetalFunction(arguments=1)
    public MetalVariable send(List<MetalVariable> args, Script script){
        if (!working) return MetalVariable.fromBool(false);
        if (!injector.isListening()) {
            power.set((int)args.getFirst().getAsRawDouble() % 16);
            injector.markDirty();
            return MetalVariable.fromBool(true);
        }
        AdditionalModule.hardError(script,"This is a Receiver, not a Transmitter!", true);
        return MetalVariable.fromBool(false);
    }
    @CallableMetalFunction
    public MetalVariable isListening(List<MetalVariable> args, Script script){
        return MetalVariable.fromBool(working && injector.isListening());
    }
    @CallableMetalFunction
    public MetalVariable isTransmitting(List<MetalVariable> args, Script script){
        return MetalVariable.fromBool(working && !injector.isListening());
    }
    @CallableMetalFunction
    public MetalVariable isWorking(List<MetalVariable> args, Script script){
        return MetalVariable.fromBool(working);
    }
    @CallableMetalFunction 
    public MetalVariable isActive(List<MetalVariable> args, Script script) {
        if (injector != null && injector.isListening() && working) return MetalVariable.fromBool(power.get() > 0);
        else return MetalVariable.fromBool(false);
    }
    @CallableMetalFunction(arguments=1)
    public MetalVariable toggle(List<MetalVariable> args, Script script){
        if(!working) return MetalVariable.fromBool(false);
        if (injector.isListening()) return MetalVariable.fromBool(false);
        if (args.getFirst().asBool()) power.set(15);
        else power.set(0);
        injector.markDirty();
        return MetalVariable.fromBool(true);
    }
    public void update() {
        if (!working) return;
        if (injector.isDirty() && !injector.isListening()) injector.notifySignalChange();
    }
    public void destroy(){
        working = false;
        if (metal.getOwner() instanceof ComputerBlockEntity be) Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(be.getLevel(),injector);
    }
}