package com.ggg.create_logic;

import com.ggg.create_logic.blockentities.ComputerBlockEntity;
import com.ggg.create_logic.metal.Metal;
import com.ggg.create_logic.metal_modules.ConsoleModule;
import com.ggg.create_logic.metal_modules.CreateModule;
import com.ggg.create_logic.redstone_link_custom.RedstoneLinkInjector;
import com.mojang.logging.LogUtils;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Random;

@Mod(ModMain.MOD_ID)
public class ModMain {
    public static final String MOD_ID = "ggg_create_logic";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final Random random = new Random();
    public static final String computerPeripheralName = MOD_ID + ":metal_computer";
    private static ItemStack itemFromId(int id) {
        Item item = Item.byId(id);
        return new ItemStack(item);
    }
    static {
        Metal.register("CONNECT_LINK_SENDER",((script, args) -> {
            if (args.size() < 3 || script.getContext() != Metal.RunContext.INIT) return new Metal.MetalVariable(false);
            int id = (int)args.getFirst().getAsRawDouble();
            int s1 = (int)args.get(1).getAsRawDouble();
            int s2 = (int)args.get(2).getAsRawDouble();
            Metal metal = script.getHost();
            Object o = metal.getOwner();
            if (o instanceof ComputerBlockEntity computer) {
                Metal.MetalModule module = metal.getModule("create_module");
                if (!(module instanceof CreateModule createModule)) return new Metal.MetalVariable(false);
                if (!createModule.hasStored(id) && !createModule.hasInjector(id)) {
                    createModule.store(id, 0);
                    RedstoneLinkInjector injector = RedstoneLinkInjector.transmitter(computer, RedstoneLinkNetworkHandler.Frequency.of(itemFromId(s1)), RedstoneLinkNetworkHandler.Frequency.of(itemFromId(s2)),() -> {
                        if (!createModule.hasStored(id)) return 0;
                        return createModule.getStored(id);
                    });
                    Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(computer.getLevel(),injector);
                    createModule.setInjector(id,injector);
                    return new Metal.MetalVariable(true);
                }
            }
            return new Metal.MetalVariable(false);
        }));
        Metal.register("CONNECT_LINK_RECEIVER",((script, args) -> {
            if (args.size() < 3 || script.getContext() != Metal.RunContext.INIT) return new Metal.MetalVariable(false);
            int id = (int)args.getFirst().getAsRawDouble();
            if (id == 0) return new Metal.MetalVariable(false);
            int s1 = (int)args.get(1).getAsRawDouble();
            int s2 = (int)args.get(2).getAsRawDouble();
            Metal metal = script.getHost();
            Object o = metal.getOwner();
            if (o instanceof ComputerBlockEntity computer) {
                Metal.MetalModule module = metal.getModule("create_module");
                if (!(module instanceof CreateModule createModule)) return new Metal.MetalVariable(false);
                if (!createModule.hasStored(id) && !createModule.hasInjector(id)) {
                    createModule.store(id,0);
                    RedstoneLinkInjector injector = RedstoneLinkInjector.receiver(computer, RedstoneLinkNetworkHandler.Frequency.of(itemFromId(s1)), RedstoneLinkNetworkHandler.Frequency.of(itemFromId(s2)),(i) -> createModule.store(id,i));
                    Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(computer.getLevel(),injector);
                    createModule.setInjector(id,injector);
                    return new Metal.MetalVariable(true);
                }
            }
            return new Metal.MetalVariable(false);
        }));
        Metal.register("SEND_LINK", (script, args) -> {
            if (script.getHost().getOwner() instanceof ComputerBlockEntity) {
                if (args.size() < 2) return new Metal.MetalVariable(false);
                int id = (int)args.getFirst().getAsRawDouble();
                int p = Math.max(((int)args.get(1).getAsRawDouble()) % 16,0);
                Metal metal = script.getHost();
                Metal.MetalModule module = metal.getModule("create_module");
                if (!(module instanceof CreateModule createModule)) return new Metal.MetalVariable(false);
                if (createModule.hasStored(id) && createModule.hasInjector(id)) {
                    RedstoneLinkInjector injector = createModule.getInjector(id);
                    if (!injector.isListening() && createModule.getStored(id) != p) {
                        createModule.store(id, p);
                        injector.markDirty();
                        return new Metal.MetalVariable(true);
                    }
                }
            }
            return new Metal.MetalVariable(false);
        });
        Metal.register("RECEIVE_LINK", (script, args) -> {
            if (script.getHost().getOwner() instanceof ComputerBlockEntity) {
                if (args.isEmpty()) {
                    return Metal.MetalVariable.fromPower(0);
                }
                int id = (int)args.getFirst().getAsRawDouble();
                Metal metal = script.getHost();
                Metal.MetalModule module = metal.getModule("create_module");
                if (!(module instanceof CreateModule createModule)) return Metal.MetalVariable.fromPower(0);
                if (createModule.hasStored(id) && createModule.hasInjector(id)) {
                    return Metal.MetalVariable.fromPower(createModule.getStored(id));
                }
            }
            return Metal.MetalVariable.fromPower(0);
        });
        Metal.register("RANDOM",((script, args) -> {
            if (args.size() < 2) return new Metal.MetalVariable(0);
            Metal.MetalVariable f = args.getFirst();
            Metal.MetalVariable s = args.get(1);
            Metal.MetalVariable result = Metal.MetalVariable.fromInt(0);
            try {
                if (f.getAsRawDouble() == s.getAsRawDouble()) {
                    result.setRawDouble(f.getAsRawDouble());
                } else if (f.getAsRawDouble() < s.getAsRawDouble()) {
                    result.setRawDouble(random.nextInt((int)s.getAsRawDouble(),(int)f.getAsRawDouble()));
                } else {
                    result.setRawDouble(random.nextInt((int)f.getAsRawDouble(),(int)s.getAsRawDouble()));
                }
            } catch (Exception ignored) {}
            return result;
        }));
        Metal.register("RAND",(script, args) -> new Metal.MetalVariable(random.nextDouble()));
        Metal.register("SIN",(script, args) -> {
            if (args.isEmpty()) return new Metal.MetalVariable(false);
            return new Metal.MetalVariable(Math.sin(args.getFirst().getAsRawDouble()));
        });
        Metal.register("COS",(script, args) -> {
            if (args.isEmpty()) return new Metal.MetalVariable(false);
            return new Metal.MetalVariable(Math.cos(args.getFirst().getAsRawDouble()));
        });
        Metal.register("TAN",(script, args) -> {
            if (args.isEmpty()) return new Metal.MetalVariable(false);
            return new Metal.MetalVariable(Math.tan(args.getFirst().getAsRawDouble()));
        });
        Metal.register("DTR",(script, args) -> {
            if (args.isEmpty()) return new Metal.MetalVariable(false);
            return new Metal.MetalVariable(Math.PI / 180.0 * args.getFirst().getAsRawDouble());
        });
        Metal.register("RTD",(script, args) -> {
            if (args.isEmpty()) return new Metal.MetalVariable(false);
            return new Metal.MetalVariable(args.getFirst().getAsRawDouble() / (Math.PI / 180.0));
        });
        Metal.register("GET_PI",(script, args) -> new Metal.MetalVariable(Math.PI));
        Metal.register("SQRT",((script, args) -> {
            if (args.isEmpty() || args.getFirst().getAsRawDouble() <= 0) return new Metal.MetalVariable(0);
            return new Metal.MetalVariable(Math.sqrt(args.getFirst().getAsRawDouble()));
        }));
        Metal.register("MIN",((script, args) -> {
            if (args.isEmpty()) return new Metal.MetalVariable(false);
            if (args.size() < 2) return args.getFirst();
            Metal.MetalVariable var = args.getFirst();
            for (int i = 1; i < args.size(); i++) {
                Metal.MetalVariable variable = args.get(i);
                if (variable.getAsRawDouble() < var.getAsRawDouble()) var = variable;
            }
            return var;
        }));
        Metal.register("MAX",((script, args) -> {
            if (args.isEmpty()) return new Metal.MetalVariable(false);
            if (args.size() < 2) return args.getFirst();
            Metal.MetalVariable var = args.getFirst();
            for (int i = 1; i < args.size(); i++) {
                Metal.MetalVariable variable = args.get(i);
                if (variable.getAsRawDouble() > var.getAsRawDouble()) var = variable;
            }
            return var;
        }));
        Metal.register("CLAMP",((script, args) -> {
            if(args.size() < 3) return new Metal.MetalVariable(false);
            Metal.MetalVariable value = args.getFirst();
            Metal.MetalVariable min = args.get(1);
            Metal.MetalVariable max = args.get(2);
            return new Metal.MetalVariable(value.getType(),Math.clamp(value.getAsRawDouble(), min.getAsRawDouble(), max.getAsRawDouble()));
        }));
        Metal.register("ABS",(script, args) -> {
            if (args.isEmpty()) return new Metal.MetalVariable(false);
            return new Metal.MetalVariable(Math.abs(args.getFirst().getAsRawDouble()));
        });
        Metal.register("POW",((script, args) -> {
            if(args.size() < 2) return new Metal.MetalVariable(false);
            Metal.MetalVariable value = args.getFirst();
            Metal.MetalVariable pow = args.get(1);
            return new Metal.MetalVariable(value.getType(),Math.pow(value.getAsRawDouble(),pow.getAsRawDouble()));
        }));
        Metal.register("GET_OPS", (script, args) -> new Metal.MetalVariable(script.getOperations()));
        Metal.registerModule("create_module", CreateModule.class);
        Metal.registerModule("console", ConsoleModule.class);
    }
    public ModMain(IEventBus modEventBus) {
        ModRegistry.register(modEventBus);
        if (ModList.get().isLoaded("computercraft")) {
            try {
                Class<?> clazz = Class.forName("com.ggg.create_logic.metal.integration.computercraft.ComputerCraftIntegration");
                clazz.getMethod("init", IEventBus.class).invoke(null, modEventBus);
            } catch (Exception e) {
                LOGGER.error(e.getLocalizedMessage());
            }
        }
    }
}
