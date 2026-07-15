package com.ggg.create_logic.metal_modules;

import com.ggg.create_logic.blockentities.ComputerBlockEntity;
import com.ggg.create_logic.lexer.MetalLexer;
import com.ggg.create_logic.metal.Metal;
import com.ggg.create_logic.redstone_link_custom.RedstoneLinkInjector;
import com.ggg.create_logic.metal.RedstoneLinker;
import com.simibubi.create.Create;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CreateModule extends Metal.MetalModule {
    public CreateModule(Metal metal) {
        super(metal);
    }
    private static final int MAX_LINKS = 64;
    private final Map<Integer, RedstoneLinkInjector> INJECTORS = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> STORE = new ConcurrentHashMap<>();
    private final List<RedstoneLinker> linkers = new ArrayList<>();
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
    private ItemStack getStack(String itemId){
        ResourceLocation resourceLocation = ResourceLocation.tryParse(itemId);
        if (resourceLocation == null) {
            resourceLocation = ResourceLocation.tryParse("minecraft:"+itemId);
        }
        if (resourceLocation == null) {
            resourceLocation = ResourceLocation.withDefaultNamespace("air");
        }
        Item item = BuiltInRegistries.ITEM.get(resourceLocation);
        return new ItemStack(item);
    }
    public boolean canMakeLink(){
        return INJECTORS.keySet().size() + linkers.size() < MAX_LINKS;
    }
    @Override
    protected boolean handleAdvanced(Metal.Script script, String cmd, Metal.Script.Bracket head) {
        if ("MAKE_ITEM_VAR".equals(cmd)) {
            if (script.getContext() == Metal.RunContext.INIT) {
                if (head.args.size() < 2) return true;
                String name = head.args.getFirst();
                String itemId = head.args.get(1).trim();
                putVariable(name, new Metal.MetalVariable(Metal.VariableType.INT, getItemId(itemId)));
            }
            return true;
        }
        if ("SENDER".equals(cmd)){
            if (script.getContext() == Metal.RunContext.INIT){
                if (head.args.size() < 3) return true;
                if (!canMakeLink()) {
                    AdditionalModule.hardError(script,"Connections limit exceeded!", false);
                    return true;
                }
                String name = head.args.getFirst();
                String s1 = head.args.get(1).trim();
                String s2 = head.args.get(2).trim();
                evaluate(script,s1).onReceive(i -> {
                    evaluate(script,s2).onReceive(j -> {
                        RedstoneLinker linker = new RedstoneLinker(metal, getStack(i.asString()), getStack(j.asString()), false);
                        linker.connect();
                        linkers.add(linker);
                        putVariable(name, new Metal.MetalVariable(linker));
                    });
                });
            }
            return true;
        }
        if ("RECEIVER".equals(cmd)){
            if (script.getContext() == Metal.RunContext.INIT){
                if (head.args.size() < 3) return true;
                if (!canMakeLink()) {
                    AdditionalModule.hardError(script,"Connections limit exceeded!", false);
                    return true;
                }
                String name = head.args.getFirst();
                String s1 = head.args.get(1).trim();
                String s2 = head.args.get(2).trim();
                evaluate(script,s1).onReceive(i -> {
                    evaluate(script,s2).onReceive(j -> {
                        RedstoneLinker linker = new RedstoneLinker(metal, getStack(i.asString()), getStack(j.asString()), true);
                        linker.connect();
                        linkers.add(linker);
                        putVariable(name, new Metal.MetalVariable(linker));
                    });
                });
            }
            return true;
        }
        return false;
    }
    public static void register() {
        MetalLexer.addKeyword("MAKE_ITEM_VAR");
        MetalLexer.addKeyword("SENDER");
        MetalLexer.addKeyword("RECEIVER");
        Metal.registerModule("create_module",CreateModule.class);
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
        for (RedstoneLinker linker : linkers) {
            linker.update();
        }
    }
    @Override
    protected void onStop(){
        if (metal.getOwner() instanceof ComputerBlockEntity be) {
            for (Integer id : INJECTORS.keySet()) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(be.getLevel(),getInjector(id));
            }
        }
        for (RedstoneLinker linker : linkers) {
            linker.destroy();
        }
    }
}
