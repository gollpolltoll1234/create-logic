package com.ggg.create_logic.metal;

import com.ggg.create_logic.metal.Metal.ErrorType;
import com.ggg.create_logic.metal.Metal.Script;
import com.ggg.create_logic.metal.Metal.MetalVariable;
import com.ggg.create_logic.metal.Metal.VariableType;
import com.ggg.create_logic.metal.Metal.Script.CodeBlock;
import com.ggg.create_logic.metal.Metal.Script.Bracket;
import com.ggg.create_logic.metal_modules.AdditionalModule;

import java.util.*;

public class MetalList implements MetalObject {
    private List<MetalVariable> list = new ArrayList<>();
    private final int maxSize;
    private final VariableType arrayType;
    public MetalList(VariableType type, int size){
        arrayType = type;
        maxSize = size;
    }
    public int size() {
        return list.size();
    }
    public int maxSize() {
        return maxSize;
    }
    public void add(MetalVariable variable, Script script) {
        if (variable.getName() == "temp" && variable.isA("DOUBLE") && (arrayType == VariableType.INT || arrayType == VariableType.POWER)){
            if (Math.floor(variable.getAsRawDouble()) == variable.getAsRawDouble()){
                if (arrayType == VariableType.INT) variable = new MetalVariable(VariableType.INT,variable.getAsRawDouble());
                else if (arrayType == VariableType.POWER && variable.getAsRawDouble() >= 0 && variable.getAsRawDouble() <= 15) variable = new MetalVariable(VariableType.POWER,variable.getAsRawDouble());
            }
        }
        if (variable.isNotA(arrayType)) {
            AdditionalModule.hardError(script,"Wrong type. Expected " + arrayType.getStr() + ", got " + variable.getType().getStr(), true);
            return;
        }   
        if (list.size() >= maxSize) {
            AdditionalModule.hardError(script,"Array size (" + maxSize + ") exceeded", true);
            return;
        }
        list.add(variable);
    }
    public void set(int index, MetalVariable variable, Script script){
        if (variable.getName() == "temp" && variable.isA("DOUBLE") && (arrayType == VariableType.INT || arrayType == VariableType.POWER)){
            if (Math.floor(variable.getAsRawDouble()) == variable.getAsRawDouble()){
                if (arrayType == VariableType.INT) variable = new MetalVariable(VariableType.INT,variable.getAsRawDouble());
                else if (arrayType == VariableType.POWER && variable.getAsRawDouble() >= 0 && variable.getAsRawDouble() <= 15) variable = new MetalVariable(VariableType.POWER,variable.getAsRawDouble());
            }
        }
        if (variable.isNotA(arrayType)) {
            AdditionalModule.hardError(script,"Wrong type. Expected " + arrayType.getStr() + ", got " + variable.getType().getStr(), true);
        return;
    }
        if (size() <= index) {
            AdditionalModule.hardError(script, "Out of bounds, current size: " + size() + ", index " + index,true);
            return;
        }
        list.set(index,variable);
    }
    public void remove(int index) {
        list.remove(index);
    }
    public void removeLast() {
        if (!list.isEmpty()) list.removeLast();
    }
    public void removeFirst() {
        if (!list.isEmpty()) list.removeFirst();
    }
    public boolean isEmpty() {
        return list.isEmpty();
    }
    public void clear(){
        list.clear();
    }
    public MetalVariable get(int index){
        if (index < 0) return null;
        MetalVariable variable = list.get(index);
        if (variable == null) variable = new MetalVariable(false);
        return variable;
    }
    public boolean hasIndex(int index) {
        return list.get(index) != null;
    }
    public MetalVariable getFirst(){
        return get(0);
    }
    public MetalVariable getLast(){
        return get(size() - 1);
    }
    @CallableMetalFunction(arguments=1)
    public MetalVariable add(List<MetalVariable> args, Script script) {
        this.add(args.getFirst(),script);
        return new MetalVariable(true);
    }
    @CallableMetalFunction(arguments=2)
    public MetalVariable set(List<MetalVariable> args, Script script) {
        this.set((int)args.getFirst().getAsRawDouble(), args.get(1),script);
        return new MetalVariable(true);
    }
    @CallableMetalFunction(arguments=1)
    public MetalVariable get(List<MetalVariable> args, Script script) {
        return this.get((int)args.getFirst().getAsRawDouble());
    }
    @CallableMetalFunction(arguments=1)
    public MetalVariable hasIndex(List<MetalVariable> args, Script script) {
        return new MetalVariable(this.hasIndex((int)args.getFirst().getAsRawDouble()));
    }
    @CallableMetalFunction
    public MetalVariable clear(List<MetalVariable> args, Script script) {
        this.clear();
        return new MetalVariable(true);
    }
    @CallableMetalFunction
    public MetalVariable removeFirst(List<MetalVariable> args, Script script) {
        this.removeFirst();
        return new MetalVariable(true);
    }
    @CallableMetalFunction
    public MetalVariable removeLast(List<MetalVariable> args, Script script) {
        this.removeLast();
        return new MetalVariable(true);
    }
    @CallableMetalFunction
    public MetalVariable size(List<MetalVariable> args, Script script){
        return MetalVariable.fromInt(size());
    }
    @CallableMetalFunction
    public MetalVariable maxSize(List<MetalVariable> args, Script script){
        return MetalVariable.fromInt(maxSize());
    }
    @CallableMetalFunction
    public MetalVariable isEmpty(List<MetalVariable> args, Script script){
        return new MetalVariable(isEmpty());
    }
    @CallableMetalFunction
    public MetalVariable getFirst(List<MetalVariable> args, Script script){
        return this.getFirst();
    }
    @CallableMetalFunction
    public MetalVariable getLast(List<MetalVariable> args, Script script){
        return this.getLast();
    }
    @CallableMetalFunction(arguments=1)
    public MetalVariable forEach(List<MetalVariable> args, Script script){
        MetalVariable functionVariable = args.getFirst();
        if (functionVariable.isNotA(VariableType.OBJECT) || !(functionVariable.asObject() instanceof MetalFunction)){
            AdditionalModule.hardError(script,"Expected MetalFunction",false);
            return MetalVariable.fromBool(false);
        }
        script.skipActionInc();
        MetalFunction function = (MetalFunction) functionVariable.asObject();
        recursiveCallFunction(function,0, size(), script.getCurrentAction(), script);
        return MetalVariable.fromBool(true);
    }
    private void recursiveCallFunction(MetalFunction function, int it, int targetIt, int at, Script script) {
        if (it >= targetIt) {
            return;
        }
        boolean last = it + 1 >= targetIt;
        MetalFuture result = null;
        switch (function.getArgumentsCount()) {
            case 1 -> {
                result = function.controlledInvoke(List.of(get(it)), script);
            }
            case 2 -> {
                result = function.controlledInvoke(List.of(MetalVariable.fromInt(it),get(it)), script);
            }
            default -> {
                break;
            }
        }
        if (result == null) return;
        result.onReceive(v -> {
            if (v == null) return;
            if (script.end) return;
            if (!last) script.jumpAt(at);
            recursiveCallFunction(function, it + 1, targetIt, at, script);
        });
    }
}