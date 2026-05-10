package com.ggg.create_logic.metal_modules;

import com.ggg.create_logic.blockentities.ComputerBlockEntity;
import com.ggg.create_logic.lexer.MetalLexer;
import com.ggg.create_logic.metal.Metal;
import com.ggg.create_logic.redstone_link_custom.RedstoneLinkInjector;
import com.simibubi.create.Create;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CreateModule extends Metal.MetalModule {
    public CreateModule(Metal metal) {
        super(metal);
    }
    private final Map<Integer, RedstoneLinkInjector> INJECTORS = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> STORE = new ConcurrentHashMap<>();
    public void store(Integer key, Integer value) {
        if (!hasStored(key)) STORE.put(key,new AtomicInteger(value));
        else {
            AtomicInteger integer = STORE.get(key);
            integer.set(value);
        }
    }
    public Integer getStored(Integer key) {
        if (!hasStored(key)) return null;
        return STORE.get(key).intValue();
    }
    public boolean hasStored(Integer key) {
        return STORE.containsKey(key);
    }
    public boolean hasInjector(Integer key) {
        return INJECTORS.containsKey(key);
    }
    public RedstoneLinkInjector getInjector(Integer key) {
        if (!hasInjector(key)) return null;
        return INJECTORS.get(key);
    }
    public void setInjector(Integer key,RedstoneLinkInjector injector) {
        INJECTORS.put(key,injector);
    }
    private int getItemId(String itemId) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(itemId);
        if (resourceLocation == null) {
            resourceLocation = ResourceLocation.tryParse("minecraft:"+itemId);
        }
        if (resourceLocation == null) {
            resourceLocation = ResourceLocation.withDefaultNamespace("air");
        }
        Item item = BuiltInRegistries.ITEM.get(resourceLocation);
        return Item.getId(item);
    }
    @Override
    protected boolean handleAdvanced(Metal.Script script, String cmd, Metal.Script.Bracket head) {
        if ("MAKE_ITEM_VAR".equals(cmd)) {
            if (script.getContext() == Metal.RunContext.INIT) {
                if (head.args.size() < 2) return false;
                String name = head.args.getFirst();
                String itemId = head.args.get(1).trim();
                putVariable(name, new Metal.MetalVariable(Metal.VariableType.INT, getItemId(itemId)));
                return true;
            }
        }
        return false;
    }
    static {
        MetalLexer.addKeyword("MAKE_ITEM_VAR");
    }
    @Override
    protected void onServerTick(){
        for (Integer key : INJECTORS.keySet()) {
            RedstoneLinkInjector injector = getInjector(key);
            if (!injector.isDirty()) continue;
            if (hasStored(key)) {
                if (injector.isListening()) {
                    injector.markClean();
                    continue;
                }
                Integer value = getStored(key);
                if (value == injector.getLastSend()) {
                    injector.markClean();
                    continue;
                }
                injector.notifySignalChange();
            }
        }
    }
    @Override
    protected void onStop(){
        if (metal.getOwner() instanceof ComputerBlockEntity be) {
            for (Integer id : INJECTORS.keySet()) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(be.getLevel(),getInjector(id));
            }
        }
    }
}
