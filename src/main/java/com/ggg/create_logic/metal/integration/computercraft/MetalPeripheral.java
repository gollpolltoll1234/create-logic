package com.ggg.create_logic.metal.integration.computercraft;

import com.ggg.create_logic.ModMain;
import com.ggg.create_logic.blockentities.ComputerBlockEntity;
import com.ggg.create_logic.metal.Metal;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.commands.arguments.item.ItemArgument;

import java.util.ArrayList;
import java.util.List;

public class MetalPeripheral extends BaseMetalPeripheral {
    public MetalPeripheral(SmartBlockEntity blockEntity) {
        super(blockEntity);
    }
    @LuaFunction(value = "callSystem",mainThread = true)
    public final double callSystem(String methodName, IArguments args) throws LuaException {
        if (!(blockEntity instanceof ComputerBlockEntity computer))  return 0.0;
        List<Metal.MetalVariable> metalArgs = new ArrayList<>();
        if (args != null) {
            for (Object arg : args.getAll()) {
                if (arg instanceof String) continue;
                Metal.VariableType type = Metal.VariableType.DOUBLE;
                double raw = 0.0;
                if (arg instanceof Number) {
                    raw = ((Number) arg).doubleValue();
                } else if (arg instanceof Boolean) {
                    raw = ((Boolean) arg) ? 1.0 : 0.0;
                    type = Metal.VariableType.BOOL;
                }
                metalArgs.add(new Metal.MetalVariable(type, raw));
            }
        }
        Metal metal = computer.getMetal();
        if (metal == null) return 0.0;
        Metal.MetalVariable result = metal.callSystem(methodName, metalArgs);
        return result != null ? result.getAsRawDouble() : 0.0;
    }
}
