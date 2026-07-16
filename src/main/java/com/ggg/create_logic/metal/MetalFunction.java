package com.ggg.create_logic.metal;

import com.ggg.create_logic.metal_modules.AdditionalModule;
import com.ggg.create_logic.metal.Metal.ErrorType;
import com.ggg.create_logic.metal.Metal.Script;
import com.ggg.create_logic.metal.Metal.MetalVariable;
import com.ggg.create_logic.metal.Metal.VariableType;
import com.ggg.create_logic.metal.Metal.Script.CodeBlock;
import com.ggg.create_logic.metal.Metal.Script.Bracket;

import java.util.*;

public class MetalFunction implements MetalObject {
    private List<AbstractMap.SimpleEntry<String, VariableType>> requiredArguments = new ArrayList<>();
    private CodeBlock body;
    private Map<String, MetalVariable> parentLocals = new HashMap<>();
    public MetalFunction(Script.Bracket args, String body, Script parentScript, Script.CodeBlockType typeBlock){
        if (!args.content.trim().isEmpty()) for (int index = 0; index < args.args.size(); index++) {
            String arg = args.args.get(index);
            int splitPos = arg.indexOf(":");
            if (splitPos == -1) {
                requiredArguments.add(new AbstractMap.SimpleEntry<>(arg,VariableType.DOUBLE));
            } else if(splitPos + 1 < arg.length()) {
                String varStr = arg.substring(0,splitPos).trim();
                String typeStr = arg.substring(splitPos + 1).trim();
                VariableType type = VariableType.fromStr(typeStr);
                if (type == null) {
                    type = VariableType.DOUBLE;
                }
                requiredArguments.add(new AbstractMap.SimpleEntry<>(varStr,type));
            }
        }
        this.body = new CodeBlock(typeBlock, body, 0);
        this.body.setSystemName("anonymousFunction");
        AdditionalModule module = AdditionalModule.getAdditional(parentScript);
        if (module == null) {
            parentLocals = Map.of();
        } else {
            parentLocals = new HashMap(parentScript.getLocalVariables());
            Map<String, MetalVariable> parentDynamicLocals = parentScript.getDynamicLocalVariables();
            for (String key : parentDynamicLocals.keySet()){
                if (parentLocals.containsKey(key)) continue;
                parentLocals.put(key, parentDynamicLocals.get(key));
            }
        }
    }
    public boolean isStopped(){
        return body.isStopped();
    }
    public int getArgumentsCount() {
        return requiredArguments.size();
    }
    public List<String> getArgumentTypes(){
        List<String> args = new ArrayList<>();
        for (int i = 0; i < requiredArguments.size(); i++){
            args.add(requiredArguments.get(i).getValue().getStr());
        }
        return args;
    }
    @CallableMetalFunction
    public MetalVariable getTypes(List<MetalVariable> args,Script script){
        List<String> list = getArgumentTypes();
        MetalList array = new MetalList(VariableType.STRING, list.size());
        for (int i = 0; i < list.size(); i++){
            array.add(MetalVariable.fromString(list.get(i)), script);
        }
        return MetalVariable.fromObject(array);
    }
    @CallableMetalFunction(arguments=-1)
    public MetalFuture invoke(List<MetalVariable> args,Script script){
        if (args.size() < requiredArguments.size()) {
            AdditionalModule.hardError(script, "Invalid arguments size, expected " + requiredArguments.size() + ", got " + args.size(), true);
            return MetalFuture.completed(MetalVariable.fromBool(false));
        }
        AdditionalModule a = AdditionalModule.getAdditional(script);
        Map<String, MetalVariable> arguments = new HashMap<>();
        if (!args.isEmpty()) for (int i = 0; i < requiredArguments.size(); i++){
            AbstractMap.SimpleEntry<String, VariableType> req = requiredArguments.get(i);
            VariableType reqType = req.getValue();
            String name = req.getKey();
            MetalVariable var = args.get(i);
            if (var.isA(reqType)) arguments.put(name, var);
            else arguments.put(name, new MetalVariable(reqType, var.getAsRawDouble()));
        }
        for (String key : parentLocals.keySet()){
            arguments.put(key,parentLocals.get(key));
        }
        MetalFuture f = script.jumpIntoCustom(body, arguments, a.getActualAction());
        script.skipActionInc();
        return f;
    }
    public MetalFuture controlledInvoke(List<MetalVariable> args,Script script){
        if (args.size() != requiredArguments.size()) {
            AdditionalModule.hardError(script, "Invalid arguments size, expected " + requiredArguments.size() + ", got " + args.size(), true);
            return MetalFuture.completed(MetalVariable.fromBool(false));
        }
        Map<String, MetalVariable> arguments = new HashMap<>();
        if (!requiredArguments.isEmpty()) for (int i = 0; i < requiredArguments.size(); i++){
            AbstractMap.SimpleEntry<String, VariableType> req = requiredArguments.get(i);
            VariableType reqType = req.getValue();
            String name = req.getKey();
            MetalVariable var = args.get(i);
            if (var.isA(reqType)) arguments.put(name, var);
            else arguments.put(name, new MetalVariable(reqType, var.getAsRawDouble()));
        }
        for (String key : parentLocals.keySet()){
            arguments.put(key,parentLocals.get(key));
        }
        return script.jumpIntoCustom(body, arguments, script.getCurrentAction());
    }
}