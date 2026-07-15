package com.ggg.create_logic.metal_modules;

import com.ggg.create_logic.metal.*;
import com.ggg.create_logic.metal.Metal.ErrorType;
import com.ggg.create_logic.metal.Metal.Script;
import com.ggg.create_logic.metal.Metal.MetalVariable;
import com.ggg.create_logic.metal.Metal.VariableType;
import com.ggg.create_logic.metal.Metal.Script.CodeBlock;
import com.ggg.create_logic.metal.Metal.Script.Bracket;
import com.ggg.create_logic.lexer.MetalLexer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AdditionalModule extends Metal.MetalModule {
    private boolean catchErrors = false;
    private int act = 0;
    public static AdditionalModule getAdditional(Script script) {
        Metal metal = script.getHost();
        Metal.MetalModule m = metal.getModule("additional_module");
        if (m instanceof AdditionalModule a) {
            return a;
        }
        return null;
    }
    public int getActualAction() {
        return act;
    }
    
    public AdditionalModule(Metal metal){
        super(metal);
    }
        
    private ConsoleModule getConsole() {
        Metal.MetalModule module = metal.getModule("console");
        if (module instanceof ConsoleModule console) return console;
        return null;
    }
    public void causeHardError(Metal.Script script, String message, boolean canBeCatched){
        if (script.end) return;
        ConsoleModule console = getConsole();
        if (!(canBeCatched && catchErrors)) {
            script.end = true;
            if (script.getContext() == Metal.RunContext.INIT) metal.endAllScripts();
            if (console != null) console.error("Hard error caused at metal!");
        } else if (console != null) {
            console.info("Error catched!");
        }
        if (console != null){
            console.error("Message: " + message);
            console.error("Context: " + script.getContext());
            console.error("Stack Trace:");
            String stackTrace = script.getStackTrace();
            if (stackTrace != null && !stackTrace.isEmpty()) {
                String[] lines = stackTrace.split("\n");
                for (String line : lines) {
                    console.error(line);
                }
            } else {
                console.error("(empty)");
            }
        }
    }
    public static void hardError(Script script, String message, boolean canBeCatched){
        if (script.getHost().getModule("additional_module") instanceof AdditionalModule additional){
            additional.causeHardError(script,message,canBeCatched);
        }
    }
    private void recursiveFor(MetalFunction function, int it, int targetIt, int at, Script script) {
        if (it >= targetIt) {
            return;
        }
        boolean last = it + 1 >= targetIt;
        MetalFuture result = function.controlledInvoke(List.of(MetalVariable.fromInt(it)), script);
        if (result == null) return;
        result.onReceive(v -> {
            if (v == null) return;
            if (function.isStopped()) return;
            if (script.end) return;
            if (!last) script.jumpAt(at);
            recursiveFor(function, it + 1, targetIt, at, script);
        });
    }
    private void recursiveWhile(MetalFunction function, String condition, Script script){
        evaluate(script,condition).onReceive(v -> {
            if(v.asBool() && !function.isStopped() && !script.end) {
                script.skipActionInc();
                MetalFuture inv = function.controlledInvoke(List.of(),script);
                inv.onReceive(r -> {
                    recursiveWhile(function, condition, script);
                });
            }
        });
    }
    private int getInt(MetalVariable var) {
        if (var.isA(VariableType.OBJECT)) {
            if (var.asObject() instanceof MetalList list) return list.size();
            else if (var.asObject() instanceof MetalFunction func) return func.getArgumentsCount();
        } 
        return (int) var.getAsRawDouble();
    }
    @Override
    protected void consumeRealAction(Script script,int action){
        act = action;
    }
    @Override
    protected boolean handleAdvanced(Script script,String cmd, Script.Bracket head){
        if (cmd.equals("SET_CATCH_MODE")){
            if (script.getContext() != Metal.RunContext.INIT) {
                causeHardError(script,"Wrong context: Expected INIT", false);
                return true;
            }
            catchErrors = true;
            return true;
        }
        if (cmd.equals("LIST") && !head.args.isEmpty()) {
            String typeStr = head.args.getFirst();
            VariableType type = VariableType.fromStr(typeStr);
            if (type == null) {
                causeHardError(script, "Expected type, got " + typeStr,true);
                return true;
            }
            MetalFuture returnValue = new MetalFuture();
            int limitSize = Metal.getConstant("List.MAX_ARRAY_ELEMENTS").asInt();
            MetalObject object = new MetalList(type, limitSize);
            MetalVariable var = MetalVariable.fromObject(object);
            returnValue.complete(var);
            script.setReturnValue(returnValue);
            return true;
        }
        if (cmd.equals("MAKE_PROCESS") && !head.args.isEmpty()){
            if (script.getContext() != Metal.RunContext.INIT) causeHardError(script, "Wrong context: Expected INIT", false);
            else if (metal.hasChildScript()){
                causeHardError(script, "There is already subprocess!", false);
            }
            else if (!metal.makeChildScript(head.args.getFirst())){
                causeHardError(script, "There is no system with name: " + head.args.getFirst() + ", or that system is reserved!", false);
            }
            return true;
        }
        if (cmd.equals("CAUSE_HARD_ERROR")){
            ConsoleModule.buildAsync(script,head).onReceive(str -> {
                causeHardError(script,str.asString(), true);
            });
            return true;
        }
        if (cmd.equals("FUNC") && head.args.size() >= 2){
            Script.Bracket to;
            String body = head.rawArgs.get(1).trim();
            body = body.substring(1,body.length() - 1);
            if (!head.subBrackets.isEmpty()) {
                to = head.subBrackets.getFirst();
            } else {
                to = new Script.Bracket();
                to.args.add(head.args.getFirst());
                to.content = head.args.getFirst();
            }
            MetalFunction func = new MetalFunction(to,body,script, Script.CodeBlockType.SYSTEM);
            script.setReturnValue(MetalFuture.completed(MetalVariable.fromObject(func)));
            return true;
        }
        if (cmd.equals("FOR") && head.args.size() >= 4) {
            String varName = head.args.get(0);
            String start = head.headArgs.get(1);
            String end = head.headArgs.get(2);
            String body = head.rawArgs.get(3).trim();
            if (body.length() < 2) return true;
            body = body.substring(1,body.length() - 1);
            Bracket args = new Bracket();
            args.args.add(varName + ":INT");
            args.content = varName + ":INT";
            MetalFunction func = new MetalFunction(args,body,script, Script.CodeBlockType.LOOP);
            evaluate(script, start).onReceive(v1 -> {
                evaluate(script, end).onReceive(v2 -> {
                    if (v1 == null || v2 == null) return;
                    script.skipActionInc();
                    recursiveFor(func, getInt(v1), getInt(v2), script.getCurrentAction(), script);
                });
            });
            return true;
        }
        if (cmd.equals("WHILE")) {
            if (head.args.size() < 2) return true;
            String condition = head.headArgs.getFirst();
            String body = head.rawArgs.get(1).trim();
            if (body.length() < 2) return true;
            body = body.substring(1,body.length() - 1);
            MetalFunction func = new MetalFunction(new Script.Bracket(),body,script, Script.CodeBlockType.LOOP);
            script.skipActionInc();
            recursiveWhile(func, condition, script);
            return true;
        }
        if (cmd.equals("EVAL")) {
            if (head.headContent.isEmpty()) return true;
            String code = head.headContent;
            MetalFuture future = new MetalFuture();
            evaluate(script, code).onReceive(var -> {
                evaluate(script,head.headContent).onReceive(str -> {
                        Metal.Script.CodeBlock block = new Metal.Script.CodeBlock(Metal.Script.CodeBlockType.SYSTEM,Metal.varToStr(str),0);
                        script.jumpIntoCustom(block, Map.of(), act).onReceive(r -> {
                        future.complete(r);
                    });
                });
            });
            script.setReturnValue(future);
            return true;
        }
        if (cmd.equals("EVALUATE")) {
            if (head.headContent.isEmpty()) {
                script.setReturnValue(MetalFuture.completed(MetalVariable.fromInt(0)));
                return true;
            }
            MetalFuture result = new MetalFuture(); 
            evaluate(script,head.headContent).onReceive(code -> {
                evaluate(script,Metal.varToStr(code)).onReceive(v -> {
                    result.complete(v);
                });
            });
            script.setReturnValue(result);
            return true;
        }
        /*if (cmd.equals("FOR_EACH") && head.args.size() >= 2){
            StringBuilder builder = new StringBuilder();
            String first = head.args.get(0);
            int second = (int) evaluate(script,head.headArgs.get(1)).getAsRawDouble();
            if (second < 0) {
                causeHardError(script, "Count cannot be negative!", true);
                return false;
            }
            if (!metal.hasSystem(first)){
                causeHardError(script, "There is no system, named: "+first+"!", true);
                return false;
            }
            if (first.equals("INIT") || first.equals("TICK")){
                causeHardError(script, "system, named "+first+" is entrypoint, and cannot be called!", true);
                return true;
            }
            for (int i = 0; i < Math.min(second,10000); i++){
                builder.append("CALL_SYSTEM(");
                builder.append(first);
                builder.append(",");
                builder.append(i);
                builder.append(");");
            }
            Metal.Script.CodeBlock block = new Metal.Script.CodeBlock(Metal.Script.CodeBlockType.SYSTEM,builder.toString(),0);
            block.setSystemName("FOR_EACH_EXECUTION_FOR_"+first);
            script.jumpIntoCustom(block,Map.of());
            return true;
        }
        if (cmd.equals("ARRAY_FOR_EACH") && head.args.size() >= 2){
            StringBuilder builder = new StringBuilder();
            String first = head.args.get(0);
            MetalVariable second = evaluate(script,head.headArgs.get(1));
            if (second == null) {
                causeHardError(script,"Second argument is null",true);
                return true;
            }
            if (second.isNotA(VariableType.OBJECT)) {
                causeHardError(script,"Second argument must be an OBJECT, not a " + second.getType().getStr(),true);
                return true;
            }
            if (!(second.asObject() instanceof MetalArray array)){
                causeHardError(script,"OBJECT MUST be MetalArray",true);
                return true;
            }
            if (!metal.hasSystem(first)){
                causeHardError(script, "There is no system, named: "+first+"!", true);
                return false;
            }
            if (first.equals("INIT") || first.equals("TICK")){
                causeHardError(script, "system, named "+first+" is entrypoint, and cannot be called!", true);
                return true;
            }
            for (int i = 0; i < array.size(); i++){
                builder.append("CALL_SYSTEM(");
                builder.append(first);
                builder.append(",");
                builder.append(i);
                builder.append(",#o");
                builder.append(");");
            }
            Metal.Script.CodeBlock block = new Metal.Script.CodeBlock(Metal.Script.CodeBlockType.SYSTEM,builder.toString(),0);
            block.setSystemName("ARRAY_FOR_EACH_EXECUTION_FOR_"+first);
            script.jumpIntoCustom(block,Map.of("#o",second));
            return true;
        }
        
        if (cmd.equals("WAIT_FOR")){
            if (script.getContext() != Metal.RunContext.RUN) {
                causeHardError(script, "Wrong contexy: Expected RUN", false);
                return true;
            }
            StringBuilder builder = new StringBuilder();
            builder.append("IF(").append(head.headContent).append(");RETURN();END_IF;CONTINUE();");
            Metal.Script.CodeBlock block = new Metal.Script.CodeBlock(Metal.Script.CodeBlockType.SYSTEM,builder.toString(),0);
            block.setSystemName("WAIT_FOR_EXECUTION");
            script.jumpIntoCustom(block,Map.of());
            return true;
        }
        if (cmd.equals("ISA") && head.headArgs.size() >= 2) {
            Metal.MetalVariable var = evaluate(script,head.headArgs.getFirst());
            Metal.VariableType type = Metal.VariableType.fromStr(head.args.get(1));
            if (var == null || type == null) return true;
            script.setReturnValue(new Metal.MetalVariable(var.isA(type)));
            return true;
        }
        if (cmd.equals("ASSERT") && head.args.size() >= 2) {
            Metal.MetalVariable condition = evaluate(script, head.headArgs.getFirst());
                
            if (!condition.asBool()) {
                String message = build(script, head);
                causeHardError(script, "Assertion failed: " + message, false);
            }
            return true;
        }

        
        */
        
        return false;
    }
    public static void register() {
        MetalLexer.addKeyword("FUNC");
        MetalLexer.addKeyword("FOR");
        MetalLexer.addKeyword("LIST");
        MetalLexer.addKeyword("MAKE_PROCESS");
        MetalLexer.addKeyword("CAUSE_HARD_ERROR");
        MetalLexer.addKeyword("SET_CATCH_MODE");
        MetalLexer.addKeyword("EVAL");
        MetalLexer.addKeyword("EVALUATE");
        MetalLexer.addKeyword("WHILE");
        Metal.registerModule("additional_module",AdditionalModule.class);
    }
    @Override
    protected void onError(Script script, String message, ErrorType errorType) {
        if (errorType == ErrorType.OPERATIONS_LIMIT_EXCEEDED) {
            causeHardError(script, message, true);
        } else {
            causeHardError(script, message, false);
        }
    }
}