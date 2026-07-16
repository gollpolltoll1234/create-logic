package com.ggg.create_logic.metal;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class Metal {
    private static final List<Script> scriptList = new ArrayList<>();
    private static final List<Script> scriptsToRemove = new ArrayList<>();
    private static final List<Script> scriptsToAdd = new ArrayList<>();
    private static final List<Metal> tickRegistered = new ArrayList<>();
    public static final int STACK_SIZE_LIMIT = 32;
    private static Thread runThread = null;
    private static boolean isStarted = false;
    private boolean acceptInvokes = false;
    private Map<String,MetalModule> modules = new ConcurrentHashMap<>();
    private static Map<String,Class<? extends MetalModule>> classMap = new HashMap<>();
    private static final Map<String, MetalJavaFunction> JAVA_FUNCTIONS = new HashMap<>();
    private static final Map<String, Supplier<MetalVariable>> CONSTANTS = new HashMap<>();
    private final Map<String, MetalVariable> VARIABLES = new ConcurrentHashMap<>();
    private Script.CodeBlock INIT_BLOCK = null;
    private Script.CodeBlock TICK_BLOCK = null;
    private Script.CodeBlock ROOT_BLOCK = null;
    private Script TICK_SCRIPT = null;
    private Script CHILD_SCRIPT = null;
    private final Map<String,List<AbstractMap.SimpleEntry<String, VariableType>>> ARGUMENT_MAP = new HashMap<>();
    private boolean isRunning = false;
    private final Object owner;
    public Metal(Object owner){
        this.owner = owner;
    }
    public Metal(){
        this.owner = null;
    }
    public boolean hasOwner(){
        return owner != null;
    }
    public Object getOwner(){
        return owner;
    }
    public enum ErrorType {
        UNKNOWN_FUNCTION,
        OPERATIONS_LIMIT_EXCEEDED,
        SYNTAX
    }
    public enum RunContext {
        GLOBAL,
        INIT,
        RUN
    }
    public enum VariableType {
        OBJECT("OBJECT"),
        POWER("POWER"),
        INT("INT"),
        DOUBLE("DOUBLE"),
        BOOL("BOOL"),
        STRING("STRING");
        private final String str;
        public String getStr(){
            return str;
        }
        private static final Map<String,VariableType> STR_TO_TYPE = new HashMap<>();
        static {
            for (VariableType type : values()) {
                STR_TO_TYPE.put(type.str,type);
            }
        }
        public static VariableType fromStr(String str) {
            return STR_TO_TYPE.getOrDefault(str.toUpperCase(), null);
        }
        VariableType(String str) {
            this.str = str;
        }
    }
    public static class MetalVariable {
        private String name;
        private VariableType type;
        private boolean boolValue = false;
        private int intValue = 0;
        private double doubleValue = 0;
        private int powerValue = 0;
        private MetalObject objectValue = null;
        private String stringValue = "";

        public String getName() {
            return name;
        }

        public VariableType getType() {
            return type;
        }

        public boolean asBool(){
            if (type != VariableType.BOOL) return false;
            return boolValue;
        }
        
        public MetalObject asObject(){
            if (type != VariableType.OBJECT) return null;
            return objectValue;
        }

        public int asInt(){
            if (type != VariableType.INT) return 0;
            return intValue;
        }

        public double asDouble(){
            if (type != VariableType.DOUBLE) return 0;
            return doubleValue;
        }

        public int asPower(){
            if (type != VariableType.POWER) return 0;
            return Math.max(powerValue % 16,0);
        }
        public String asString(){
            if (type != VariableType.STRING) return "";
            return stringValue;
        }

        public double getAsRawDouble() {
            return switch (type) {
                case BOOL -> boolValue ? 1.0 : 0.0;
                case INT -> (double) intValue;
                case DOUBLE -> doubleValue;
                case POWER -> (double) asPower();
                case OBJECT -> objectValue != null ? 1.0 : 0.0;
                case STRING -> stringValue.length();
            };
        }
        public void setRawDouble(double rawDouble) {
            switch (type) {
                case DOUBLE -> doubleValue = rawDouble;
                case POWER -> powerValue = Math.max(((int)rawDouble) % 16,0);
                case BOOL -> boolValue = rawDouble > 0;
                case INT -> intValue = (int)rawDouble;
                case OBJECT -> {}
            }
        }
        public void setStringValue(String string){
            stringValue = string;
        }
        public void setBoolValue(boolean boolValue) {
            this.boolValue = boolValue;
        }

        public void setDoubleValue(double doubleValue) {
            this.doubleValue = doubleValue;
        }

        public void setIntValue(int intValue) {
            this.intValue = intValue;
        }

        public void setPowerValue(int powerValue) {
            this.powerValue = powerValue;
        }
        public void setObjectValue(MetalObject object){
            objectValue = object;
        }
        public boolean isA(VariableType type) {
            if (type == null) return false;
            return this.type == type;
        }
        public boolean isA(String type) {
            return isA(VariableType.fromStr(type));
        }
        public boolean isNotA(VariableType type) {
            return !isA(type);
        }
        public boolean isNotA(String type) {
            return !isA(type);
        }
        private void set(MetalVariable other){
            this.type = other.getType();
            if (isA(VariableType.OBJECT)) objectValue = other.asObject();
            else if (isA(VariableType.STRING)) stringValue = other.asString();
            else setRawDouble(other.getAsRawDouble());
        }
        public MetalVariable(VariableType type, double value) {
            this.type = type;
            this.name = "temp";
            setRawDouble(value);
        }
        public MetalVariable(double value) {
            this.type = VariableType.DOUBLE;
            this.name = "temp";
            doubleValue = value;
        }
        public MetalVariable(boolean value) {
            this.type = VariableType.BOOL;
            this.name = "temp";
            this.boolValue = value;
        }
        public MetalVariable(MetalVariable variable) {
            this.type = variable.getType();
            this.name = "temp";
            if (isA(VariableType.OBJECT)) objectValue = variable.asObject();
            else if (isA(VariableType.STRING)) stringValue = variable.asString();
            else setRawDouble(variable.getAsRawDouble());
        }
        public MetalVariable(MetalObject object){
            this.type = VariableType.OBJECT;
            this.name = "temp";
            objectValue = object;
        }
        public MetalVariable(String string){
            this.type = VariableType.STRING;
            this.name = "temp";
            stringValue = string;
        }
        public static MetalVariable fromInt(int value) {
            return new MetalVariable(VariableType.INT,value);
        }
        public static MetalVariable fromDouble(double value) {
            return new MetalVariable(VariableType.DOUBLE,value);
        }
        public static MetalVariable fromBool(boolean value) {
            return new MetalVariable(value);
        }
        public static MetalVariable fromObject(MetalObject value) {
            return new MetalVariable(value);
        }
        public static MetalVariable fromPower(int value) {
            return new MetalVariable(VariableType.POWER,value);
        }
        public static MetalVariable fromString(String string){
            return new MetalVariable(string);
        }
        private void setName(String name) {
            this.name = name;
        }
    }
    public static String varToStr(MetalVariable variable) {
        switch (variable.getType()) {
            case INT -> {
                return String.valueOf(variable.asInt());
            }
            case BOOL -> {
                return variable.asBool() ? "TRUE" : "FALSE";
            }
            case POWER -> {
                return String.valueOf(variable.asPower());
            }
            case DOUBLE -> {
                return String.valueOf(variable.asDouble());
            }
            case OBJECT -> {
                return variable.asObject() != null ? "Object of " + variable.asObject().getClass().getSimpleName() : "NULL";
            }
            case STRING -> {
                return variable.asString();
            }
            case null -> {
                return "NULL";
            }
        }
    }
    public static void waitForAll(List<MetalFuture> futures, Consumer<List<MetalVariable>> callback) {
        if (futures.isEmpty()) {
            callback.accept(new ArrayList<>());
            return;
            }
            
            List<MetalVariable> results = new ArrayList<>(Collections.nCopies(futures.size(), null));
            AtomicInteger completed = new AtomicInteger(0);
            AtomicBoolean done = new AtomicBoolean(false);
            
            for (int i = 0; i < futures.size(); i++) {
                int idx = i;
                futures.get(i).onReceive(value -> {
                    if (done.get()) return;
                    results.set(idx, value);
                    if (completed.incrementAndGet() == futures.size()) {
                        if (done.compareAndSet(false, true)) {
                            callback.accept(results);
                        }
                    }
                });
            }
        }
    @FunctionalInterface
    public interface MetalJavaFunction {
        MetalVariable execute(Script script,List<MetalVariable> args);
    }
    private void putVariable(String name, MetalVariable variable) {
        variable.setName(name);
        VARIABLES.put(name,variable);
    }
    public static void register(String name, MetalJavaFunction func) {
        if (!JAVA_FUNCTIONS.containsKey(name))
            JAVA_FUNCTIONS.put(name, func);
    }
    public static void registerConstant(String name,Supplier<MetalVariable> supplier){
        if (!CONSTANTS.containsKey(name)){
            CONSTANTS.put(name,supplier);
        }
    }
    public static MetalVariable getConstant(String name) {
        Supplier<MetalVariable> supplier = CONSTANTS.get(name);
        return supplier != null ? supplier.get() : null;
    }
    public boolean isRunning(){
        return isRunning;
    }
    private Script.CodeBlock findSystemBlock(Script.CodeBlock rootBlock, String name) {
        for (Script.CodeBlock sub : rootBlock.getCodeBlocks()) {
            if (sub.type == Script.CodeBlockType.SYSTEM && sub.getSystemName().equals(name)) {
                return sub;
            }
        }
        return null;
    }
    public boolean hasSystem(String name){
        if (!isRunning || ROOT_BLOCK == null) return false;
        return findSystemBlock(ROOT_BLOCK,name) != null;
    }
    public void setCode(String code) {
        if (isRunning) return;
        ROOT_BLOCK = new Script.CodeBlock(Script.CodeBlockType.SYSTEM,code,0);
    }
    public void run(){
        if (isRunning) return;
        if (ROOT_BLOCK == null) return;
        isRunning = true;
        ROOT_BLOCK.end();
        for (String key : classMap.keySet()) {
            try {
                Class<? extends MetalModule> clazz = classMap.get(key);
                Constructor<? extends MetalModule> constructor = clazz.getDeclaredConstructor(Metal.class);
                modules.put(key,constructor.newInstance(this));
            } catch (Exception e) {
                System.out.println(e.getLocalizedMessage());
            }
        }
        Script rootScript = new Script(RunContext.GLOBAL,ROOT_BLOCK,this,false);
        while (rootScript.hasNext()) rootScript.next();
        INIT_BLOCK = findSystemBlock(ROOT_BLOCK,"INIT");
        TICK_BLOCK = findSystemBlock(ROOT_BLOCK,"TICK");
        if (TICK_BLOCK != null) {
            TICK_SCRIPT = new Script(RunContext.RUN,TICK_BLOCK,this, false);
        }
        if (INIT_BLOCK != null) {
            INIT_BLOCK.end();
            Script initScript = new Script(RunContext.INIT,INIT_BLOCK,this,false);
            try {
                while (initScript.hasNext() && initScript.getOperations() < 4000) initScript.next();
                if (initScript.getOperations() >= 4000) {
                    System.out.println("Too many operations in INIT!");
                    for (MetalModule module : modules.values()){
                        module.onError(initScript,"The operations limit has been exceeded", ErrorType.OPERATIONS_LIMIT_EXCEEDED);
                    }
                }
            } catch (Exception e) {
                System.out.println("Exception in Metal at Server Thread: " + e.getMessage());
                e.printStackTrace();
            }
        }
        if (TICK_SCRIPT != null) {
            scriptsToAdd.add(TICK_SCRIPT);
        }
        tickRegistered.add(this);
        for (MetalModule module : modules.values()){
            module.onStart();
        }
    }
    public void stop(){
        if (!isRunning) return;
        isRunning = false;
        endAllScripts();
        for (MetalModule module : modules.values()){
            module.onStop();
        }
        VARIABLES.clear();
        acceptInvokes = false;
        ARGUMENT_MAP.clear();
        modules.clear();
        if (!tickRegistered.remove(this)) System.out.println("Cannot remove Metal object from tick listeners");
    }
    
    public boolean makeChildScript(String blockName){
        if (CHILD_SCRIPT != null) return false;
        if (!isRunning) return false;
        if (blockName == "TICK" || blockName == "INIT") return false;
        Script.CodeBlock target = findSystemBlock(ROOT_BLOCK,blockName);
        if (target == null) return false;
        CHILD_SCRIPT = new Script(RunContext.RUN, target, this, false);
        scriptsToAdd.add(CHILD_SCRIPT);
        return true;
    }
    public boolean hasChildScript(){
        return CHILD_SCRIPT != null;
    }
    public void endAllScripts(){
        if (TICK_SCRIPT != null) TICK_SCRIPT.end = true;
        if (CHILD_SCRIPT != null) CHILD_SCRIPT.end = true;
    }
    public boolean isAcceptInvokes(){
        return acceptInvokes;
    }
    private void tick(){
        for (MetalModule module : modules.values()) {
            module.onServerTick();
        }
    }
    public static void tickAll(){
        if(isStarted) for (Metal metal : tickRegistered) {
            metal.tick();
        }
    }
    public static void launch(){
        if (isStarted) return;
        isStarted = true;
        runThread = new Thread(() -> {
            System.out.println("Metal Thread was been started!");
            while (isStarted) {
                for (Script script : scriptList) {
                    if (script.end) {
                        scriptsToRemove.add(script);
                    } else if(script.hasNext()) {
                        try {
                            script.next();
                        } catch (Exception e) {
                            System.out.println("Error in Metal Thread: " + e.getMessage());
                            script.skip();
                        }
                    } else {
                        script.jumpStart();
                    }
                }
                if (!scriptsToAdd.isEmpty()) {
                    synchronized(scriptsToAdd) {
                        scriptList.addAll(scriptsToAdd);
                        scriptsToAdd.clear();
                    }
                }

                if (!scriptsToRemove.isEmpty()) {
                    synchronized(scriptsToRemove) {
                        scriptList.removeAll(scriptsToRemove);
                        scriptsToRemove.clear();
                    }
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    System.out.println(e.toString());
                }
            }
            scriptList.clear();
            scriptsToAdd.clear();
            scriptsToRemove.clear();
            System.out.println("Metal Thread was been terminated!");
        });
        runThread.setName("Metal-Thread");
        runThread.start();
    }
    public static void terminate(){
        isStarted = false;
        tickRegistered.clear();
    }
    public static Set<String> getJavaFunctions(){
        return JAVA_FUNCTIONS.keySet();
    }
    public MetalVariable callSystem(String name, List<MetalVariable> args) {
        if (!acceptInvokes) return null;
        if (ROOT_BLOCK == null || Objects.equals(name, "TICK") || name.equals("INIT")) return null;
        Script.CodeBlock target = findSystemBlock(ROOT_BLOCK,name);
        if (target == null) return null;
        Script script = new Script(RunContext.RUN,target,this,true);
        Map<String, MetalVariable> arguments = new HashMap<>();
        if(!args.isEmpty() && ARGUMENT_MAP.containsKey(name)) {
            List<AbstractMap.SimpleEntry<String,VariableType>> requires = ARGUMENT_MAP.get(name);
            for (int index = 0; index < requires.size(); index++) {
                MetalVariable variable;
                VariableType regType = requires.get(index).getValue();
                if (index < args.size()) {
                    MetalVariable provided = args.get(index);
                    if (provided.isA(regType)) variable = provided;
                    else variable = new MetalVariable(regType,provided.getAsRawDouble());
                } else variable = new MetalVariable(regType,0);
                arguments.put(requires.get(index).getKey(),variable);
            }
        }
        return script.execute(arguments,1500);
    }
    public static <T extends MetalModule> void registerModule(String name, Class<T> clazz) {
        if (classMap.containsKey(name)) return;
        classMap.put(name, clazz);
    }
    public MetalModule getModule(String name) {
        return modules.getOrDefault(name,null);
    }
    public static class MetalModule {
        public MetalModule(Metal metal) {
            this.metal = metal;
        }
        protected Metal metal;
        protected void onNext(Script script,String cmd, Script.Bracket head){}
        protected boolean handleAdvanced(Script script,String cmd, Script.Bracket head) {
            return false;
        }
        protected void onTickScript(Script script){}
        protected void consumeRealAction(Script script, int action){}
        protected void onServerTick(){}
        protected void onStop(){}
        protected void onStart(){}
        protected void onError(Script script, String message, ErrorType errorType){}
        protected void onExitStack(Script script){}
        protected final Script.CodeBlock getSystemBlock(String name){
            if(metal.ROOT_BLOCK == null) return null;
            return metal.findSystemBlock(metal.ROOT_BLOCK,name);
        }
        protected final MetalFuture evaluate(Script script,String expr) {
            return script.evaluate(expr);
        }
        protected final Map<String,MetalVariable> getGlobalVariables(){
            return metal.VARIABLES;
        }
        protected final Map<String,MetalVariable> getLocalVariables(Script script){
            return script.LOCAL_VARIABLES;
        }
        protected final Map<String,MetalVariable> getDynamicLocalVariables(Script script){
            return script.DYNAMIC_LOCAL_VARIABLES;
        }
        protected final MetalVariable getVariable(Script script,String name) {
            return script.getVariable(name);
        }
        protected final void putVariable(String name, MetalVariable var) {
            metal.putVariable(name,var);
        }
    }
    public static class Script {
        private final boolean isInvoke;
        private MetalFuture returnValue = null;
        private MetalVariable invokeResult = null;
        private final Metal host;
        public boolean end = false;
        private long operations = 0;
        private CodeBlock current;
        private Map<String, MetalVariable> LOCAL_VARIABLES;
        private Map<String, MetalVariable> DYNAMIC_LOCAL_VARIABLES;
        private final CodeBlock starter;
        private final RunContext context;
        private int currentAction = 0;
        private final int blockLength;
        private int waitTicks = 0;
        private final List<StackPoint> stack = new ArrayList<>();
        private final Map<Class<?>, Map<String, Method>> methodCache = new ConcurrentHashMap<>();
        private boolean frozen = false;
        private boolean skipActionInc = false;
        private List<Runnable> onUnfreeze = new ArrayList<>();
        private Method findMethod(Class<?> clazz, String methodName, int argCount) {
            Map<String, Method> methodMap = methodCache.computeIfAbsent(clazz, c -> {
                Map<String, Method> map = new HashMap<>();
                for (Method m : c.getDeclaredMethods()) {
                    CallableMetalFunction ann = m.getAnnotation(CallableMetalFunction.class);
                    if (ann != null) {
                        map.put(m.getName() + "#" + ann.arguments(), m);
                        if (ann.arguments() == -1) {
                            map.put(m.getName() + "#varargs", m);
                        }
                    }
                }
                return map;
            });
            Method method = methodMap.get(methodName + "#" + argCount);
            if (method != null) {
                return method;
            }
            return methodMap.get(methodName + "#varargs");
        }
        Map<String, MetalVariable> getLocalVariables() {
            return LOCAL_VARIABLES;
        }
        Map<String, MetalVariable> getDynamicLocalVariables() {
            return DYNAMIC_LOCAL_VARIABLES;
        }
        public long getOperations(){
            return operations;
        }
        public int getCurrentAction() {
            return currentAction;
        }
        public void jumpAt(int action) {
            currentAction = action;
        }
        public void jump(int step) {
            currentAction += step;
        }
        public MetalFuture getReturnValue(){
            return returnValue;
        }
        public void clearReturnValue(){
            returnValue = null;
        }
        public boolean hasReturnValue(){
            return returnValue != null;
        }
        public void setReturnValue(MetalFuture value){
            returnValue = value;
        }
        public boolean isInvoke(){
            return isInvoke;
        }
        public boolean isFrozen() {
            return frozen;
        }
        public void freeze(){
            if (canFreeze()) frozen = true;
        }
        public void unfreeze() {
            if (!frozen) return;
            frozen = false;
            for (Runnable task : onUnfreeze){
                task.run();
            }
            onUnfreeze.clear();
        }
        public boolean canFreeze() {
            return context == RunContext.RUN && !isInvoke;
        }
        public void waitForUnfreeze(Runnable task){
            if (!canFreeze()) {
                task.run();
                return;
            }
            freeze();
            onUnfreeze.add(task);
        }
        public MetalVariable getInvokeResult(){
            return invokeResult;
        }
        private Script getThis() {
            return this;
        }
        public String getStackTrace(){
            StringBuilder builder = new StringBuilder();
            builder.append("CURRENT: \n")
                .append("Function: ")
                .append(getLast())
                .append(", Action: ")
                .append(currentAction)
                .append(", Name: ")
                .append(current.name);
            builder.append("\nSTACK:");
            if (stack.isEmpty()){
                builder.append("EMPTY");
                return builder.toString();
            }
            for(int i = stack.size() - 1; i >= 0; i--){
                StackPoint point = stack.get(i);
                builder.append("\nDepth: ")
                .append(i)
                .append(", Action: ")
                .append(point.startAction)
                .append(", Name: ")
                .append(point.block.name);
            }
            return builder.toString();
        }
        public void setArguments(Map<String, MetalVariable> args) {
            if (!isInvoke) return;
            LOCAL_VARIABLES = args;
        }
        private MetalFuture evaluate(String expr) {
            String finalExpr = expr.trim();
            if (finalExpr.equalsIgnoreCase("TRUE")) return MetalFuture.completed(new MetalVariable(true));
            if (finalExpr.equalsIgnoreCase("FALSE")) return MetalFuture.completed(new MetalVariable(false));
            return new Object() {
                int pos = -1, ch;
                int startAction;
                void nextChar() {
                    ch = (++pos < finalExpr.length()) ? finalExpr.charAt(pos) : -1;
                }

                boolean eat(String stringToEat) {
                    while (ch == ' ') nextChar();
                    if (finalExpr.startsWith(stringToEat, pos)) {
                        pos += stringToEat.length() - 1;
                        nextChar();
                        return true;
                    }
                    return false;
                }

                MetalFuture parse() {
                    startAction = currentAction;
                    nextChar();
                    MetalFuture result = null;
                    try {
                        result = parseLogicalOr();
                    } catch (Exception ignored){}
                    if (result == null) result = MetalFuture.completed(new MetalVariable(false));
                    return result;
                }
                                
                private void processLogicalOr(MetalVariable var, MetalFuture result) {
                    if (eat("||")) {
                        parseLogicalAnd().onReceive(right -> {
                            var.set(new MetalVariable(var.asBool() || right.asBool()));
                            processLogicalOr(var, result);
                        });
                    } else {
                        result.complete(var);
                    }
                }
                
                MetalFuture parseLogicalOrSync(MetalVariable x) {
                    MetalVariable var = new MetalVariable(x);
                    MetalFuture result = new MetalFuture();
                    processLogicalOr(var, result);
                    return result;
                }
                
                private void processLogicalAnd(MetalVariable var, MetalFuture result) {
                    if (eat("&&")) {
                        parseComparison().onReceive(right -> {
                            var.set(new MetalVariable(var.asBool() && right.asBool()));
                            processLogicalAnd(var, result);
                        });
                    } else {
                        result.complete(var);
                    }
                }
                
                MetalFuture parseLogicalAndSync(MetalVariable x) {
                    MetalVariable var = new MetalVariable(x);
                    MetalFuture result = new MetalFuture();
                    processLogicalAnd(var, result);
                    return result;
                }
                private void processComparison(MetalVariable var, MetalFuture result) {
                    if (eat("==")) {
                        parseExpression().onReceive(right -> {
                            if (var.isA(VariableType.STRING) || right.isA(VariableType.STRING)) {
                                var.set(new MetalVariable(Metal.varToStr(var).equals(Metal.varToStr(right))));
                            } else {
                                var.set(new MetalVariable(var.getAsRawDouble() == right.getAsRawDouble()));
                            }
                            processComparison(var, result);
                        });
                    } else if (eat("!=")) {
                        parseExpression().onReceive(right -> {
                            if (var.isA(VariableType.STRING) || right.isA(VariableType.STRING)) {
                                var.set(new MetalVariable(!Metal.varToStr(var).equals(Metal.varToStr(right))));
                            } else {
                                var.set(new MetalVariable(var.getAsRawDouble() != right.getAsRawDouble()));
                            }
                            processComparison(var, result);
                        });
                    } else if (eat(">=")) {
                        parseExpression().onReceive(right -> {
                            var.set(new MetalVariable(var.getAsRawDouble() >= right.getAsRawDouble()));
                            processComparison(var, result);
                        });
                    } else if (eat("<=")) {
                        parseExpression().onReceive(right -> {
                            var.set(new MetalVariable(var.getAsRawDouble() <= right.getAsRawDouble()));
                            processComparison(var, result);
                        });
                    } else if (eat(">")) {
                        parseExpression().onReceive(right -> {
                            var.set(new MetalVariable(var.getAsRawDouble() > right.getAsRawDouble()));
                            processComparison(var, result);
                        });
                    } else if (eat("<")) {
                        parseExpression().onReceive(right -> {
                            var.set(new MetalVariable(var.getAsRawDouble() < right.getAsRawDouble()));
                            processComparison(var, result);
                        });
                    } else {
                        result.complete(var);
                    }
                }
                
                MetalFuture parseComparisonSync(MetalVariable x) {
                    MetalVariable var = new MetalVariable(x);
                    MetalFuture result = new MetalFuture();
                    processComparison(var, result);
                    return result;
                }
                private void processExpression(MetalVariable var, MetalFuture result) {
                    if (eat("+")) {
                        parseTerm().onReceive(right -> {
                            if (var.isA(VariableType.STRING) || right.isA(VariableType.STRING)) {
                                var.set(new MetalVariable(Metal.varToStr(var) + Metal.varToStr(right)));
                            } else {
                                var.set(new MetalVariable(var.getAsRawDouble() + right.getAsRawDouble()));
                            }
                            processExpression(var, result);
                        });
                    } else if (eat("-")) {
                        parseTerm().onReceive(right -> {
                            var.set(new MetalVariable(var.getAsRawDouble() - right.getAsRawDouble()));
                            processExpression(var, result);
                        });
                    } else {
                        result.complete(var);
                    }
                }
                
                MetalFuture parseExpressionSync(MetalVariable x) {
                    MetalVariable var = new MetalVariable(x);
                    MetalFuture result = new MetalFuture();
                    processExpression(var, result);
                    return result;
                }
                private void processTerm(MetalVariable var, MetalFuture result) {
                    if (eat("**")) {
                        parseFactor().onReceive(right -> {
                            var.set(new MetalVariable(Math.pow(var.getAsRawDouble(), right.getAsRawDouble())));
                            processTerm(var, result);
                        });
                    } else if (eat("*")) {
                        parseFactor().onReceive(right -> {
                            var.set(new MetalVariable(var.getAsRawDouble() * right.getAsRawDouble()));
                            processTerm(var, result);
                        });
                    } else if (eat("/")) {
                        parseFactor().onReceive(right -> {
                            var.set(new MetalVariable(var.getAsRawDouble() / right.getAsRawDouble()));
                            processTerm(var, result);
                        });
                    } else if (eat("%")) {
                        parseFactor().onReceive(right -> {
                            var.set(new MetalVariable(var.getAsRawDouble() % right.getAsRawDouble()));
                            processTerm(var, result);
                        });
                    } else {
                        result.complete(var);
                    }
                }
                
                MetalFuture parseTermSync(MetalVariable x) {
                    MetalVariable var = new MetalVariable(x);
                    MetalFuture result = new MetalFuture();
                    if (x.isA(VariableType.STRING)) {
                        result.complete(x);
                        return result;
                    }
                    processTerm(var, result);
                    return result;
                }
                
                MetalFuture parseComparison() {
                    MetalFuture x = parseExpression();
                    MetalFuture y = new MetalFuture();
                    x.onReceive(v -> {
                        parseComparisonSync(v).onReceive(var -> y.complete(var));
                    });
                    return y;
                }
                MetalFuture parseExpression() {
                    MetalFuture x = parseTerm();
                    MetalFuture y = new MetalFuture();
                    x.onReceive(v -> {
                        parseExpressionSync(v).onReceive(var -> {
                            y.complete(var);
                        });
                    });
                    return y;
                }
                MetalFuture parseTerm() {
                    MetalFuture x = parseFactor();
                    MetalFuture y = new MetalFuture();
                    x.onReceive(v -> {
                        parseTermSync(v).onReceive(value -> {
                            y.complete(value);
                        });
                    });
                    return y;
                }
                MetalFuture parseLogicalAnd() {
                    MetalFuture x = parseComparison();
                    MetalFuture y = new MetalFuture();
                    x.onReceive(v -> {
                        parseLogicalAndSync(v).onReceive(var -> {
                            y.complete(var);
                        });
                    });
                    return y;
                }
                MetalFuture parseLogicalOr() {
                    MetalFuture x = parseLogicalAnd();
                    MetalFuture y = new MetalFuture();
                    x.onReceive(v -> {
                        parseLogicalOrSync(v).onReceive(var -> {
                            y.complete(var);
                        });
                    });
                    return y;
                }
                
                
                MetalFuture parseFactor() {
                    if (eat("!")){
                        MetalFuture f = new MetalFuture();
                        parseFactor().onReceive(x -> {
                            final MetalVariable var = x;
                            f.complete(new MetalVariable(!var.asBool()));
                        });
                        return f;
                    } 
                    if (eat("+")) return parseFactor();
                    if (eat("-"))  {
                        MetalFuture f = new MetalFuture();
                        parseFactor().onReceive(x -> {
                            final MetalVariable var = x;
                            f.complete(new MetalVariable(-var.getAsRawDouble()));
                        });
                        return f;
                    } 
                    if (eat("TRUE")) return MetalFuture.completed(MetalVariable.fromBool(true));
                    if (eat("FALSE")) return MetalFuture.completed(MetalVariable.fromBool(false));
                    MetalFuture x;
                if(eat("\"")){
                    StringBuilder builder = new StringBuilder();
                boolean isBackslash = false;
                boolean exitWithEnd = false;
                for (;;){
                    if (finalExpr.length() <= pos) {
                        break;
                    }
                    if (ch == '\"' && !isBackslash) {
                        nextChar();
                        exitWithEnd = true;
                        break;
                    } else if (ch == '\\' && !isBackslash) {
                        isBackslash = true;
                        nextChar();
                        continue;
                    } else if (ch == 'n' && isBackslash) {
                        builder.append("\n");
                    } else builder.append((char)ch);
                    isBackslash = false;
                    nextChar();
                }
                if (!exitWithEnd) {
                    for (MetalModule module : host.modules.values()){
                        module.onError(getThis(), "String without ending!", ErrorType.SYNTAX);
                    }
                    x = MetalFuture.completed(MetalVariable.fromBool(false));
                } else x = MetalFuture.completed(MetalVariable.fromString(builder.toString()));
                } else
                    if (eat("(")) {
                        x = parseLogicalOr();
                        eat(")");
                    } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                        int startPos = this.pos;
                        while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                        x = MetalFuture.completed(new MetalVariable(Double.parseDouble(finalExpr.substring(startPos, this.pos))));
                    } else if (ch == '$' || ch == '@' || ch == ':' || ch == '.' || ch == '#' || Character.isLetter(ch)) {
                        boolean isGlobalVar = ch == '$';
                        int startPos = this.pos;
                        while ((Character.isLetterOrDigit(ch) || ch == '@' || ch == ':' || ch == '.' || ch == '$' || ch == '_' || ch == ' ' || ch == '#') && ch != '(' && ch != ')') {
                            nextChar();
                        }
                        String name = finalExpr.substring(startPos, this.pos).trim();
                        if (ch == '(') {
                            int bracketStart = this.pos;
                            int balance = 0;
                            int figureBalance = 0;
                            boolean isPoint = false;
                            while (this.pos < finalExpr.length()) {
                                if (ch == '{') figureBalance++;
                                if (ch == '}' && figureBalance > 0) figureBalance--;
                                if (figureBalance <= 0) {
                                    if (ch == '(') {
                                        balance++;
                                        if (balance == 1) isPoint = false;
                                    }
                                    if (ch == ')') {
                                        balance--;
                                    }
                                    nextChar();
                                    if (balance == 0 && ch != '.' && !isPoint) {
                                        break;
                                    } else if (balance == 0 && !isPoint) {
                                        isPoint = true;
                                    }
                                } else nextChar();
                            }
                            if (isPoint) {
                                for (MetalModule module : host.modules.values()){
                                    module.onError(getThis(), "Unexpected '.'", ErrorType.SYNTAX);
                                }
                                return null;
                            }
                            String funcFullBody = finalExpr.substring(bracketStart, this.pos);
                            Bracket bracket = parseBrackets(funcFullBody);
                            for (MetalModule module : host.modules.values()){
                                module.consumeRealAction(getThis(),startAction);
                            }
                            boolean moduleHandled = false;
                            clearReturnValue();
                            for (MetalModule module : host.modules.values()){
                                if (module.handleAdvanced(getThis(),name,bracket)) {
                                    moduleHandled = true;
                                    break;
                                }
                            }
                            if (!moduleHandled) {
                                x = runObject(name,bracket);
                                if (x == null) {
                                    if (name.equals("CALL_SYSTEM")) {
                                        x = callSystem(bracket,startAction);
                                    } else {
                                        List<MetalFuture> evalArgs = new ArrayList<>();
                                        for (String arg : bracket.headArgs) {
                                            MetalFuture var = evaluate(arg);
                                            evalArgs.add(var);
                                        }
                                        x = evaluateCompleteJFunction(name, evalArgs);
                                    }
                                    x = processObject(name + funcFullBody,removePointsAtBrackets(name + funcFullBody,true),x,1);
                            
                                } else 
                                    x = processObject(name + funcFullBody,removePointsAtBrackets(name + funcFullBody,true),x,2);
                            } else {
                                x = getReturnValue();
                                x = processObject(name + funcFullBody,removePointsAtBrackets(name + funcFullBody,true),x,1);
                            }
                            if (x == null) x = MetalFuture.completed(new MetalVariable(VariableType.BOOL, 0));
                        } else {
                            if (isGlobalVar) x = MetalFuture.completed(host.VARIABLES.getOrDefault(name, null));
                            else {
                                x = MetalFuture.completed(LOCAL_VARIABLES.getOrDefault(name, null));
                                if (x.get() == null) x = MetalFuture.completed(DYNAMIC_LOCAL_VARIABLES.getOrDefault(name, null));
                            }
                            if (x == null) {
                                for (String key : CONSTANTS.keySet()){
                                    if (name.equals(key)) {
                                        Supplier<MetalVariable> supplier = CONSTANTS.get(key);
                                        if (supplier != null){
                                            x = MetalFuture.completed(supplier.get());
                                            break;
                                        }
                                    }
                                }
                            }
                            if (x == null) x = MetalFuture.completed(new MetalVariable(0));
                        }
                    } else {
                        return MetalFuture.completed(new MetalVariable(false));
                    }
                    return x;
                }
            }.parse();
        }

        private static int getSemicolonIndex(String text,int c) {
            int index = -1;
            int count = c - 1;
            int bracketBalance = 0;
            boolean atStr = false;
            boolean atBackslash = false;
            for (int i = 0; i < text.length(); i++) {
                char at = text.charAt(i);
                if (!atStr) {
                    if (at == '"') atStr = true;
                    else if (at == ';' && bracketBalance <= 0) {
                        if (count == 0) {
                            index = i;
                            break;
                        } else count--;
                    } else if (at == '{') bracketBalance++;
                    else if(at == '}' && bracketBalance > 0) bracketBalance--;
                    atBackslash = false;
                } else {
                    if (at == '"' && !atBackslash) atStr = false;
                    else {
                        if (at == '\\' && !atBackslash) atBackslash = true;
                        else atBackslash = false;
                    }
                }
            }
            return index;
        }
        public static Bracket parseBrackets(String result) {
            int bracketLayer = -1;
            boolean isBracketStarted = false;
            Bracket head = new Bracket();
            Bracket current = head;
            for (int index = 0; index < result.length(); index++) {
                char at = result.charAt(index);
                head.writeHead(at,bracketLayer);
                if (isBracketStarted) {
                    current.write(at);
                }
                if (head.isOnString() || head.isOnFigureBracket()) continue;
                if (at == '(') {
                    boolean isBracketHasStarted = isBracketStarted;
                    isBracketStarted = true;
                    if (isBracketHasStarted) {
                        head.pos = index;
                    }
                    bracketLayer++;
                    if (bracketLayer > 0) {
                        Bracket bracket = new Bracket();
                        bracket.pos = index;
                        bracket.layer = bracketLayer;
                        current.append(bracket);
                        current = bracket;
                    }
                    head.updateLayer(bracketLayer);
                } else if (at == ')' && isBracketStarted && bracketLayer >= 0) {
                    bracketLayer--;
                    if (current != head) {
                        current = current.getParent();
                    }
                    head.updateLayer(bracketLayer);
                }
            }
            return head;
        }
        public RunContext getContext(){
            return context;
        }
        public enum CodeBlockType {
            SYSTEM("SYSTEM"),
            LOOP("LOOP");
            private final String text;
            CodeBlockType(String text) {
                this.text = text;
            }

            public String getText() {
                return text;
            }
        }
        public static class CodeBlock {
            private CodeBlock parent;
            public final CodeBlockType type;
            private final int layer;
            private String content;
            private final List<CodeBlock> codeBlocks = new ArrayList<>();
            private boolean ended = false;
            private String name = "";
            private boolean stopped = false;
            public boolean isStopped(){
                return stopped;
            }
            public String getSystemName(){
                return name;
            }
            public int size(){
                int len = 0;
                for (int i = 0; i < content.length(); i++) {
                    char ch = content.charAt(i);
                    if (ch == ';') len++;
                }
                return len;
            }
            public void setSystemName(String name) {
                this.name = name;
            }
            
            public CodeBlock(CodeBlockType type, String content,int layer) {
                this.type = type;
                this.content = content;
                this.layer = layer;
            }
            public CodeBlock getParent(){
                return parent;
            }
            public void addCodeBlock(CodeBlock block) {
                codeBlocks.add(block);
                block.parent = this;
            }
            public List<CodeBlock> getCodeBlocks(){
                return codeBlocks;
            }
            public String getContent(){
                return content;
            }
            public void append(String content,int layer) {
                if (ended || layer != this.layer) return;
                this.content += content;
            }
            public void end(){
                ended = true;
            }

            public boolean isEnded() {
                return ended;
            }
        }
        
        public static class Bracket {
            private boolean onString = false;
            private boolean isBackslash = false;
            private int figureBracketBalance = 0;
            public String content = "";
            public String rawContent = "";
            public int layer = 0;
            public int pos = 0;
            private boolean closed = false;
            private Bracket parent = null;
            public final List<Bracket> subBrackets = new ArrayList<>();
            public List<String> args = new ArrayList<>();
            public List<String> rawArgs = new ArrayList<>();
            private StringBuilder currentArg = new StringBuilder();
            private StringBuilder currentRawArg = new StringBuilder();
            public List<String> headArgs = new ArrayList<>();
            public StringBuilder currentHeadArg = new StringBuilder();
            public String headContent = "";
            private boolean isOnString(){
                if (parent == null) return onString;
                return parent.isOnString();
            }
            private boolean isOnFigureBracket() {
                if (parent == null) return figureBracketBalance > 0;
                else return parent.isOnFigureBracket();
            }
            private void processArgs(char symbol,int argType,int bracketLayer) {
                if (symbol == ',' && bracketLayer == layer && !isOnString() && !isOnFigureBracket()) {
                    if (argType == 0) {
                        headArgs.add(currentHeadArg.toString().trim());
                        currentHeadArg = new StringBuilder();
                    } else if (argType == 1) {
                        rawArgs.add(currentArg.toString().trim());
                        currentRawArg = new StringBuilder();
                    } else if (argType == 2) {
                        args.add(currentArg.toString().trim());
                        currentArg = new StringBuilder();
                    }
                } else {
                    if (argType == 0) {
                        currentHeadArg.append(symbol);
                    }
                    else if (argType == 1) {
                        currentRawArg.append(symbol);
                    }
                    else currentArg.append(symbol);
                }
            }
            public void write(char symbol) {
                if (closed) return;
                rawContent += symbol;
                processArgs(symbol,1,layer);
                if (symbol == '(' || symbol == ')') {
                    return;
                }
                processArgs(symbol,2,layer);
                content += symbol;
            }
            public void writeHead(char symbol,int bracketLayer) {
                if (closed) return;
                if (symbol == '\"') {
                    if (onString) {
                        if (!isBackslash) onString = false;
                    } else onString = true;
                }
                if (onString) {
                    if (symbol == '\\' && !isBackslash) isBackslash = true;
                    else isBackslash = false;
                } else isBackslash = false;
                if (!onString) {
                    if (symbol == '{') figureBracketBalance++;
                    else if (symbol == '}' && figureBracketBalance > 0) figureBracketBalance--;
                }
                processArgs(symbol,0,bracketLayer);
                headContent += symbol;
            }
            public void updateLayer(int layer) {
                if (layer < this.layer) {
                    closed = true;
                    finish();
                }
                for (Bracket bracket : subBrackets) bracket.updateLayer(layer);
            }
            public void append(Bracket bracket) {
                subBrackets.add(bracket);
                bracket.parent = this;
            }
            public Bracket getParent() {
                return parent;
            }
            public String collapse() {
                StringBuilder builder = new StringBuilder();
                builder.append(this.content);
                for (Bracket sub : subBrackets) {
                    builder.append("(").append(sub.collapse()).append(")");
                }
                return builder.toString();
            }
            public void finish() {
                if (!currentArg.isEmpty()) {
                    args.add(currentArg.toString().trim());
                    currentArg.setLength(0);
                }
                if (!currentRawArg.isEmpty()) {
                    rawArgs.add(currentRawArg.toString());
                    currentRawArg.setLength(0);
                }
                if (!currentHeadArg.isEmpty()) {
                    headArgs.add(currentHeadArg.toString());
                    currentHeadArg.setLength(0);
                }
            }
            public Bracket(){}
        }
        
        private record StackPoint(int startAction, CodeBlock block, Map<String,MetalVariable> LOCAL_VARIABLES, Map<String,MetalVariable> DYNAMIC_LOCAL_VARIABLES, MetalFuture returnable) { }
        public Script(RunContext context, CodeBlock block, Metal host, boolean isInvoke) {
            this.context = context;
            this.host = host;
            this.isInvoke = isInvoke;
            current = block;
            starter = block;
            blockLength = block.size();
            LOCAL_VARIABLES = new HashMap<>();
            DYNAMIC_LOCAL_VARIABLES = new HashMap<>();
        }
        public MetalVariable execute(Map<String,MetalVariable> args,long operationsLimit){
            if (!isInvoke) return null;
            setArguments(args);
            while (hasNext() && operations < operationsLimit) next();
            return getInvokeResult();
        }
        public Metal getHost(){
            return host;
        }
        private void skipToBlockIfEnd() {
            int depth = 1;
            while (depth > 0) {
                currentAction++;
                int i = getSemicolonIndex(current.content, currentAction + 1);
                int li = getSemicolonIndex(current.content, currentAction);
                if (i == -1) break;
                String line = (li == -1) ? current.content.substring(0, i)
                        : current.content.substring(li + 1, i);
                line = line.trim().toUpperCase();
                if (line.startsWith("IF")) depth++;
                if (line.startsWith("END_IF")) depth--;
            }
        }
        private void skipToBlockIfEndOrElse(){
            int depth = 1;
            while (depth > 0) {
                currentAction++;
                int i = getSemicolonIndex(current.content, currentAction + 1);
                int li = getSemicolonIndex(current.content, currentAction);
                if (i == -1) break;
                String line = (li == -1) ? current.content.substring(0, i)
                        : current.content.substring(li + 1, i);
                line = line.trim().toUpperCase();
                if (line.startsWith("IF")) depth++;
                if (line.startsWith("END_IF")) depth--;
                if (line.startsWith("ELSE") && depth == 1) {
                    currentAction++;
                    return;
                }
            }
        }
        private void skipToBlockSystemEnd() {
            int depth = 1;
            while (depth > 0) {
                currentAction++;
                int i = getSemicolonIndex(current.content, currentAction + 1);
                int li = getSemicolonIndex(current.content, currentAction);
                if (i == -1) break;
                String line = (li == -1) ? current.content.substring(0, i)
                        : current.content.substring(li + 1, i);
                line = line.trim().toUpperCase();
                if (line.startsWith("SYSTEM")) depth++;
                if (line.startsWith("END_SYSTEM")) depth--;
            }
        }
        private MetalFuture evaluateJFunction(String name,List<String> args) {
            List<MetalFuture> evalArgs = new ArrayList<>();
            for (String arg : args) evalArgs.add(evaluate(arg));
            return evaluateCompleteJFunction(name,evalArgs);
        }
        private MetalVariable evaluateCompleteJFunctionSync(String name, List<MetalVariable> args) {
            if (JAVA_FUNCTIONS.containsKey(name)) {
                return JAVA_FUNCTIONS.get(name).execute(this, args);
            } else {
                for (MetalModule module : host.modules.values()) module.onError(this,"Unknown function: " + name, ErrorType.UNKNOWN_FUNCTION);
            }
            return null;
        }
        private MetalFuture evaluateCompleteJFunction(String name, List<MetalFuture> args) {
            MetalFuture resultFuture = new MetalFuture();
            if (args.isEmpty()) {
                resultFuture.complete(evaluateCompleteJFunctionSync(name, new ArrayList<>()));
                return resultFuture;
            }
            
            List<MetalVariable> vars = new ArrayList<>(Collections.nCopies(args.size(), null));
            AtomicInteger completed = new AtomicInteger(0);
            AtomicBoolean done = new AtomicBoolean(false); // защита от двойного завершения
            
            for (int i = 0; i < args.size(); i++) {
                int index = i;
                MetalFuture f = args.get(i);
                f.onReceive(value -> {
                    vars.set(index, value);
                    int doneCount = completed.incrementAndGet();
                    if (doneCount == args.size()) {
                        if (!done.getAndSet(true)) {
                            MetalVariable result = evaluateCompleteJFunctionSync(name, vars);
                            resultFuture.complete(result);
                        }
                    }
                });
            }
            return resultFuture;
        }
        private void applyValue(MetalVariable target, MetalVariable source) {
            double val = source.getAsRawDouble();
            target.setRawDouble(val);
        }
        public MetalFuture jumpInto(String name, List<MetalVariable> args, int startAction){
            MetalFuture returnable = new MetalFuture();
            if (stack.size() > STACK_SIZE_LIMIT) return MetalFuture.completed(null);
            Map<String, MetalVariable> arguments = new HashMap<>();
            if (host.ROOT_BLOCK == null) return MetalFuture.completed(null);
            CodeBlock block = host.findSystemBlock(host.ROOT_BLOCK,name);
            if (block == null || isReservedSystem(name)) return MetalFuture.completed(null);
            if(!args.isEmpty() && host.ARGUMENT_MAP.containsKey(name)) {
                List<AbstractMap.SimpleEntry<String,VariableType>> requires = host.ARGUMENT_MAP.get(name);
                for (int index = 0; index < requires.size(); index++) {
                    MetalVariable variable;
                    VariableType regType = requires.get(index).getValue();
                    if (index < args.size()) {
                        MetalVariable provided = args.get(index);
                        if (provided.isA(regType)) variable = provided;
                        else variable = new MetalVariable(regType,provided.getAsRawDouble());
                    } else variable = new MetalVariable(regType,0);
                    arguments.put(requires.get(index).getKey(),variable);
                }
                stack.add(new StackPoint(startAction,current, LOCAL_VARIABLES, DYNAMIC_LOCAL_VARIABLES, returnable));
                current = block;
                DYNAMIC_LOCAL_VARIABLES = new HashMap<>();
                LOCAL_VARIABLES = arguments;
                currentAction = 0;
            }
            return returnable;
        }
        public MetalFuture jumpIntoCustom(CodeBlock block, Map<String,MetalVariable> arguments, int startAction){
            MetalFuture returnable = new MetalFuture();
            if (stack.size() > STACK_SIZE_LIMIT) return MetalFuture.completed(null);
            stack.add(new StackPoint(startAction,current, LOCAL_VARIABLES, DYNAMIC_LOCAL_VARIABLES, returnable));
            current = block;
            DYNAMIC_LOCAL_VARIABLES = new HashMap<>();
            LOCAL_VARIABLES = arguments;
            currentAction = 0;
            return returnable;
        }
        private String extractBlockIfContent() {
            int startAction = currentAction + 1;
            skipToBlockIfEnd();
            int endAction = currentAction;
            StringBuilder builder = new StringBuilder();
            for (int a = startAction; a < endAction; a++) {
                int i = getSemicolonIndex(current.content, a + 1);
                int li = getSemicolonIndex(current.content, a);
                String line = (li == -1) ? current.content.substring(0, i)
                        : current.content.substring(li + 1, i);
                builder.append(line).append(";");
            }
            currentAction = startAction - 1;
            return builder.toString();
        }
        private int getBlockIfEndActionIndex() {
            int depth = 1;
            int tempAction = currentAction;
            while (depth > 0) {
                tempAction++;
                int i = getSemicolonIndex(current.content, tempAction);
                if (i == -1) break;

                String line = getLineAt(tempAction).toUpperCase().trim();
                if (line.startsWith("IF")) depth++;
                if (line.startsWith("END_IF")) depth--;
            }
            return tempAction;
        }
        private int getBlockIfEndOrElseActionIndex() {
            int depth = 1;
            int tempAction = currentAction;
            while (depth > 0) {
                tempAction++;
                int i = getSemicolonIndex(current.content, tempAction);
                if (i == -1) break;

                String line = getLineAt(tempAction).toUpperCase().trim();
                if (line.startsWith("IF")) depth++;
                if (line.startsWith("END_IF")) depth--;
                if (line.startsWith("ELSE") && depth == 1) {
                    return tempAction;
                }
            }
            return tempAction;
        }
        private int getBlockSystemEndActionIndex() {
            int depth = 1;
            int tempAction = currentAction;
            while (depth > 0) {
                tempAction++;
                int i = getSemicolonIndex(current.content, tempAction + 1);
                if (i == -1) break;

                String line = getLineAt(tempAction).toUpperCase().trim();
                if (line.startsWith("SYSTEM")) depth++;
                if (line.startsWith("END_SYSTEM")) depth--;
            }
            return tempAction;
        }
        private String extractTextBetween(int startAction, int endAction) {
            StringBuilder sb = new StringBuilder();
            for (int a = startAction; a < endAction; a++) {
                sb.append(getLineAt(a)).append(";");
            }
            return sb.toString();
        }

        private String getLineAt(int actionIdx) {
            int i = getSemicolonIndex(current.content, actionIdx + 1);
            int li = getSemicolonIndex(current.content, actionIdx);
            return (li == -1) ? current.content.substring(0, i)
                    : current.content.substring(li + 1, i);
        }
        private void makeLocalVariable(String name, MetalVariable variable, boolean isDynamic) {
            variable.setName(name);
            if (isDynamic && !DYNAMIC_LOCAL_VARIABLES.containsKey(name)) {
                DYNAMIC_LOCAL_VARIABLES.put(name,variable);
            } else if (!isDynamic && !LOCAL_VARIABLES.containsKey(name)) {
                LOCAL_VARIABLES.put(name,variable);
            }
        }
        public void skip(){
            if (end) return;
            if (!hasNext()) {
                tryReturnByStack();
                return;
            }
            currentAction++;
        }
        private MetalVariable getVariable(String name) {
            if (name.startsWith("$")) {
                return host.VARIABLES.get(name);
            } else if (name.startsWith("#")) {
                MetalVariable x;
                x = LOCAL_VARIABLES.get(name);
                if (x == null) x = DYNAMIC_LOCAL_VARIABLES.get(name);
                return x;
            }
            return null;
        }
        private boolean isReservedSystem(String name){
            return switch (name) {
                case "INIT", "TICK" -> true;
                default -> false;
            };
        }
        private void bindArgsToSystem(Bracket head) {
            if (head.args.size() < 2) return;
            String name = head.args.getFirst();
            if (host.ARGUMENT_MAP.containsKey(name) || isReservedSystem(name) || host.findSystemBlock(host.ROOT_BLOCK,name) == null) return;
            List<AbstractMap.SimpleEntry<String,VariableType>> args = new ArrayList<>();
            for (int index = 1; index < head.args.size(); index++) {
                String arg = head.args.get(index);
                int splitPos = arg.indexOf(":");
                if (splitPos == -1) {
                    args.add(new AbstractMap.SimpleEntry<>(arg,VariableType.DOUBLE));
                } else if(splitPos + 1 < arg.length()) {
                    String varStr = arg.substring(0,splitPos).trim();
                    String typeStr = arg.substring(splitPos + 1).trim();
                    VariableType type = VariableType.fromStr(typeStr);
                    if (type == null) {
                        type = VariableType.DOUBLE;
                    }
                    args.add(new AbstractMap.SimpleEntry<>(varStr,type));
                }
            }
            host.ARGUMENT_MAP.put(name,args);
        }
        public MetalFuture callSystem(Bracket head, int startAction){
            MetalFuture returnable = new MetalFuture();
            if (head.args.isEmpty()) return MetalFuture.completed(null);
            if (stack.size() > STACK_SIZE_LIMIT) return MetalFuture.completed(null);
            String name = head.args.getFirst();
            if (isReservedSystem(name)) return MetalFuture.completed(null);
            if (host.ROOT_BLOCK == null) return MetalFuture.completed(null);
            CodeBlock target = host.findSystemBlock(host.ROOT_BLOCK,name);
            if (target == null) return MetalFuture.completed(null);
            List<MetalFuture> futureArguments = new ArrayList<>();
            if(host.ARGUMENT_MAP.containsKey(name)) {
                List<AbstractMap.SimpleEntry<String,VariableType>> requires = host.ARGUMENT_MAP.get(name);
                for (int index = 0; index < requires.size(); index++) {
                    if (index + 1 < head.args.size()) {
                        MetalFuture provided = evaluate(head.headArgs.get(index + 1));
                        futureArguments.add(provided);
                    } else futureArguments.add(MetalFuture.completed(new MetalVariable(VariableType.DOUBLE,0)));
                }
            }
            waitForAll(futureArguments, args -> {
                Map<String, MetalVariable> arguments = new HashMap<>();
                if(host.ARGUMENT_MAP.containsKey(name)) {
                    List<AbstractMap.SimpleEntry<String,VariableType>> requires = host.ARGUMENT_MAP.get(name);
                    for (int index = 0; index < requires.size(); index++) {
                        MetalVariable variable;
                        VariableType regType = requires.get(index).getValue();
                        MetalVariable provided = args.get(index);
                        if (provided.isA(regType)) variable = provided;
                        else variable = new MetalVariable(regType,provided.getAsRawDouble());
                        arguments.put(requires.get(index).getKey(),variable);
                    }
                }
                stack.add(new StackPoint(startAction,current, LOCAL_VARIABLES, DYNAMIC_LOCAL_VARIABLES,returnable));
                current = target;
                DYNAMIC_LOCAL_VARIABLES = new HashMap<>();
                LOCAL_VARIABLES = arguments;
                currentAction = -1;
            });
            return returnable;
        }
        public void skipActionInc(){
            skipActionInc = true;
        }
        private void executeNoBracket(String cmd) {
            operations++;
            if (context == RunContext.GLOBAL) {
                currentAction++;
                return;
            }
            switch (cmd) {
                case "ELSE" -> {
                    int ind = getBlockIfEndActionIndex();
                    if (ind <= blockLength && ind >= 0) currentAction = ind;
                }
                case "CONTINUE" -> {
                    if (current.type == CodeBlockType.LOOP){
                        StackPoint exitPoint = stack.getLast();
                        tryReturnByStack();
                        exitPoint.returnable.complete(MetalVariable.fromBool(false));
                        return;
                    }
                    jumpLocalStart();
                    return;
                }
                case "BREAK" -> {
                    if (current.type == CodeBlockType.LOOP){
                        current.stopped = true;
                        StackPoint exitPoint = stack.getLast();
                        tryReturnByStack();
                        exitPoint.returnable.complete(MetalVariable.fromBool(false));
                        return;
                    }
                }
                default -> {
                    tryAssignVariable(cmd);
                }
            }
            if (!skipActionInc) currentAction++;
        }
        private boolean tryAssignVariable(String cmd){
            String[] args = cmd.split(" ");
            String clean = cleaner(cmd);
            if (args.length >= 1){
                VariableType type = VariableType.fromStr(args[0]);
                if (type != null) {
                    if (args.length >= 2){
                        String varName = args[1].trim();
                        if (varName.startsWith("#") || varName.startsWith("$")){
                            boolean isGlobal = varName.startsWith("$");
                            if (isGlobal && context != RunContext.INIT) return false;
                            if (isGlobal) {
                                if(host.VARIABLES.containsKey(varName)) return false;
                                int equalInd = clean.indexOf("=");
                                if (equalInd == -1 || equalInd + 1 >= cmd.length()) return false;
                                String expr = cmd.substring(equalInd + 1).trim();
                                evaluate(expr).onReceive(v -> {
                                    host.VARIABLES.put(varName,cast(v,type));
                                });
                                return true;
                            } else {
                                if (LOCAL_VARIABLES.containsKey(varName) || DYNAMIC_LOCAL_VARIABLES.containsKey(varName)) return false;
                                int equalInd = clean.indexOf("=");
                                if (equalInd == -1 || equalInd + 1 >= cmd.length()) return false;
                                String expr = cmd.substring(equalInd + 1).trim();
                                evaluate(expr).onReceive(v -> {
                                    DYNAMIC_LOCAL_VARIABLES.put(varName,cast(v,type));
                                });
                                return true;
                            }
                        }
                    }
                } else {
                    Operator op = null;
                    int opInd = -1;
                    for (Operator o : Operator.values()) {
                        opInd = clean.indexOf(o.getAsString());
                        if (opInd != -1) {
                            op = o;
                            break;
                        }
                    }
                    final Operator fop = op;
                    if (opInd == -1) return false;
                    int opLen = op.getAsString().length();
                    String varName = cmd.substring(0,opInd).trim();
                    if (varName.startsWith("#") || varName.startsWith("$")){
                        boolean isGlobal = varName.startsWith("$");
                        if (isGlobal){
                            if (host.VARIABLES.containsKey(varName)) {
                                MetalVariable original = host.VARIABLES.get(varName);
                                if (op.isSingle()) {
                                    op.castOperator(original,null);
                                    return true;
                                }
                                if (opInd == -1 || opInd + opLen >= cmd.length()) return false;
                                String expr = cmd.substring(opInd + opLen).trim();
                                evaluate(expr).onReceive(v -> {
                                    fop.castOperator(original,v);
                                });
                                return true;
                            }
                        } else {
                            MetalVariable original = LOCAL_VARIABLES.getOrDefault(varName,null);
                            if (original == null) original = DYNAMIC_LOCAL_VARIABLES.getOrDefault(varName,null);
                            if (original == null) return false;
                            final MetalVariable f = original;
                            if (op.isSingle()) {
                                op.castOperator(original,null);
                                return true;
                            }
                            if (opInd == -1 || opInd + opLen >= cmd.length()) return false;
                            String expr = cmd.substring(opInd + opLen).trim();
                            evaluate(expr).onReceive(v -> {
                                fop.castOperator(f,v);
                            });
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        public enum Operator {
            INCREMENT("++"),
            DECREMENT("--"),
            ADD("+="),
            SUB("-="),
            DIV("/="),
            POW("**="),
            MUL("*="),
            MOD("%="),
            SET("=");
            private static final Map<String,Operator> stringToOperator = new HashMap<>();
            static {
                for (Operator op : values()){
                    stringToOperator.put(op.str,op);
                }
            }
            private final String str;
            Operator(String str) {
                this.str = str;
            }
            public String getAsString(){
                return str;
            }
            public static Operator fromString(String str){
                return stringToOperator.getOrDefault(str,null);
            }
            public boolean isSingle(){
                return this == INCREMENT || this == DECREMENT;
            }
            public void castOperator(MetalVariable left, MetalVariable right){
                switch(this){
                    case INCREMENT -> {
                        if (left.isA(VariableType.OBJECT)) break;
                        if (left.isA(VariableType.STRING)) break;
                        if (left.isA(VariableType.BOOL)) break;
                        left.setRawDouble(left.getAsRawDouble() + 1);
                    }
                    case DECREMENT -> {
                        if (left.isA(VariableType.OBJECT)) break;
                        if (left.isA(VariableType.STRING)) break;
                        if (left.isA(VariableType.BOOL)) break;
                        left.setRawDouble(left.getAsRawDouble() - 1);
                    }
                    case ADD -> {
                        if (left.isA(VariableType.OBJECT)) break;
                        if (left.isA(VariableType.BOOL)) break;
                        if (left.isA(VariableType.STRING)) left.set(MetalVariable.fromString(left.asString() + varToStr(right)));
                        left.setRawDouble(left.getAsRawDouble() + right.getAsRawDouble());
                    }
                    case SUB -> {
                        if (left.isA(VariableType.OBJECT)) break;
                        if (left.isA(VariableType.STRING)) break;
                        if (left.isA(VariableType.BOOL)) break;
                        left.setRawDouble(left.getAsRawDouble() - right.getAsRawDouble());
                    }
                    case DIV -> {
                        if (left.isA(VariableType.OBJECT)) break;
                        if (left.isA(VariableType.STRING)) break;
                        if (left.isA(VariableType.BOOL)) break;
                        left.setRawDouble(left.getAsRawDouble() / right.getAsRawDouble());
                    }
                    case MUL -> {
                        if (left.isA(VariableType.OBJECT)) break;
                        if (left.isA(VariableType.STRING)) break;
                        if (left.isA(VariableType.BOOL)) break;
                        left.setRawDouble(left.getAsRawDouble() * right.getAsRawDouble());
                    }
                    case POW -> {
                        if (left.isA(VariableType.OBJECT)) break;
                        if (left.isA(VariableType.STRING)) break;
                        if (left.isA(VariableType.BOOL)) break;
                        left.setRawDouble(Math.pow(left.getAsRawDouble(), right.getAsRawDouble()));
                    }
                    case MOD -> {
                        if (left.isA(VariableType.OBJECT)) break;
                        if (left.isA(VariableType.STRING)) break;
                        if (left.isA(VariableType.BOOL)) break;
                        left.setRawDouble(left.getAsRawDouble() % right.getAsRawDouble());
                    }
                    case SET -> {
                        left.set(cast(right,left.type));
                    }
                }
            }
        }
        private static MetalVariable cast(MetalVariable var, VariableType type) {
            if (var.isA(type)) return var;
            if (type == VariableType.STRING) return MetalVariable.fromString(varToStr(var));
            if (type == VariableType.OBJECT) return MetalVariable.fromObject(null);
            return new MetalVariable(type, var.getAsRawDouble());
        }
        private static String cleaner(String str){
            StringBuilder builder = new StringBuilder();
            int bracketBalance = 0;
            int figureBracketBalance = 0;
            boolean isString = false;
            boolean isBackslash = false;
            for (int i = 0; i < str.length(); i++){
                char ch = str.charAt(i);
                if (isString) {
                    if (ch == '"' && !isBackslash) isString = false;
                    if (ch == '\\' && !isBackslash) isBackslash = true;
                    else isBackslash = false;
                    builder.append(" ");
                } else {
                    isBackslash = false;
                    if (ch == '"') isString = true;
                    else if (ch == '(' && figureBracketBalance <= 0) bracketBalance++;
                        else if(ch == ')' && bracketBalance > 0 && figureBracketBalance <= 0) bracketBalance--;
                            else if (ch == '{') figureBracketBalance++;
                                else if (ch == '}' && figureBracketBalance > 0) figureBracketBalance--;
                                    else if (figureBracketBalance <= 0 && bracketBalance <= 0) {
                                        builder.append(ch);
                                        continue;
                                    }
                                builder.append(" ");
                }
            }
            return builder.toString();
        }
        public String getLast(){
            int i = getSemicolonIndex(current.content, currentAction + 1);
            int li = getSemicolonIndex(current.content, currentAction);

            if (i == -1) return "";

            String line = (li == -1) ? current.content.substring(0, i)
                    : current.content.substring(li + 1, i);
            line = line.trim();
            return line;
        }
        public void next() {
            skipActionInc = false;
            operations++;
            if (end) return;
            if (!hasNext()) return;
            if (!hasNextAtBlock()) {
                StackPoint exitPoint = stack.getLast();
                tryReturnByStack();
                exitPoint.returnable.complete(MetalVariable.fromBool(false));
                return;
            }
            for (MetalModule m : host.modules.values()){
                m.onTickScript(this);
            }
            if (canFreeze() && frozen) {
                return;
            }
            if (waitTicks > 0) {
                waitTicks--;
                return;
            }
            int i = getSemicolonIndex(current.content, currentAction + 1);
            int li = getSemicolonIndex(current.content, currentAction);

            if (i == -1) {
                skip();
                return;
            }

            String line = (li == -1) ? current.content.substring(0, i)
                    : current.content.substring(li + 1, i);
            line = line.trim();
            if (line.isEmpty()) {
                skip();
                return;
            }
            int bracketStart = line.indexOf("(");
            if (bracketStart == -1) {
                executeNoBracket(line);
                return;
            }
            String cmd = line.substring(0, bracketStart).trim();
            Bracket head = parseBrackets(line.substring(bracketStart));
            int bracketStop = bracketStart + head.headContent.length();
            if (context == RunContext.GLOBAL) {
                if (!cmd.equals("SYSTEM")) {
                    skip();
                    return;
                }
            }

            if (context == RunContext.RUN) {
                if (cmd.equals("MAKE_VAR") || cmd.equals("SYSTEM") || cmd.equals("BIND_ARGS_TO_SYSTEM") || cmd.equals("ACCEPT_INVOKES")) {
                    skip();
                    return;
                }
            }

            if (context == RunContext.INIT) {
                if (cmd.equals("SYSTEM") || cmd.equals("WAIT") || cmd.equals("RERUN")) {
                    skip();
                    return;
                }
            }
            if (isInvoke && cmd.equals("WAIT")) {
                skip();
                return;
            }
            for (MetalModule module : host.modules.values()){
                module.consumeRealAction(this,currentAction);
            }
            for (MetalModule module : host.modules.values()) {
                module.onNext(this,cmd,head);
            }
            clearReturnValue();
            for (MetalModule module : host.modules.values()) {
                if(module.handleAdvanced(this,cmd,head)){
                    processObject(line,removePointsAtBrackets(line,false),getReturnValue(),1);
                    if(!skipActionInc) currentAction++;
                    return;
                }
            }
            switch (cmd) {
                case "MAKE_VAR" -> {
                    if (context == RunContext.INIT) {
                        if (head.args.size() < 3) break;
                        VariableType type = VariableType.fromStr(head.args.getFirst().toUpperCase());
                        if (type == null) break;
                        String name = head.args.get(1);
                        evaluate(head.headArgs.get(2)).onReceive(val -> {
                            if (type != VariableType.OBJECT && type != VariableType.STRING)
                                host.putVariable(name, new MetalVariable(type, val.getAsRawDouble()));
                            else if (val.isA(VariableType.OBJECT))
                                host.putVariable(name, MetalVariable.fromObject(val.asObject()));
                                else if (val.isA(VariableType.STRING))
                                    host.putVariable(name, MetalVariable.fromString(val.asString()));
                        });
                    }
                }
                case "SET" -> {
                    if (head.args.size() < 2) break;
                    MetalVariable var = getVariable(head.args.getFirst());
                    evaluate(head.headArgs.get(1)).onReceive(val -> {
                        if (var != null && var.isNotA(VariableType.OBJECT) && var.isNotA(VariableType.STRING)) applyValue(var, val);
                        else if (var != null && val.isA(VariableType.OBJECT)){
                            var.setObjectValue(val.asObject());
                        } else if (var != null && var.isA(VariableType.STRING)) {
                            var.setStringValue(val.asString());
                        }
                    });
                }
                case "IF" -> {
                    MetalFuture condF = evaluate(head.headContent);
                    condF.onReceive(cond -> {
                        if (!cond.asBool()) {
                            currentAction++;
                            currentAction = getBlockIfEndOrElseActionIndex();
                        }
                    });
                }
                case "ELSE" -> {
                    int ind = getBlockIfEndActionIndex();
                    if (ind <= blockLength && ind >= 0) currentAction = ind;
                }
                case "SYSTEM" -> {
                    String sysType = head.args.getFirst().toUpperCase();
                    CodeBlock target = null;
                    for (CodeBlock sub : current.getCodeBlocks()) {
                        if (sub.type == CodeBlockType.SYSTEM && sub.getSystemName().equals(sysType)) {
                            target = sub;
                            break;
                        }
                    }
                    if (target == null) {
                        int endIdx = getBlockSystemEndActionIndex();
                        String blockContent = extractTextBetween(currentAction + 1, endIdx);
                        target = new CodeBlock(CodeBlockType.SYSTEM, blockContent, current.layer + 1);
                        target.setSystemName(sysType);
                        current.addCodeBlock(target);
                    }
                    bindArgsToSystem(head);
                }
                case "ADD" -> {
                    if (head.args.size() < 2) break;
                    MetalVariable var = getVariable(head.args.getFirst());
                    if (var == null) break;
                    evaluate(head.headArgs.get(1)).onReceive(val -> {
                        val.setRawDouble(var.getAsRawDouble() + val.getAsRawDouble());
                        applyValue(var, val);
                    });
                }
                case "WAIT" -> {
                    if (head.args.isEmpty()) break;
                    evaluate(head.headArgs.getFirst()).onReceive(ticks -> {
                        if (ticks != null) {
                            waitTicks += (int)ticks.getAsRawDouble();
                        }
                    });
                }
                case "SUB" -> {
                    if (head.args.size() < 2) break;
                    MetalVariable var = getVariable(head.args.getFirst());
                    if (var == null) break;
                    evaluate(head.headArgs.get(1)).onReceive(val -> {
                        val.setRawDouble(var.getAsRawDouble() - val.getAsRawDouble());
                        applyValue(var, val);
                    });
                }
                case "DIV" -> {
                    if (head.args.size() < 2) break;
                    MetalVariable var = getVariable(head.args.getFirst());
                    if (var == null) break;
                    evaluate(head.headArgs.get(1)).onReceive(val -> {
                        val.setRawDouble(var.getAsRawDouble() / val.getAsRawDouble());
                        applyValue(var, val);
                    });
                }
                case "MUL" -> {
                    if (head.args.size() < 2) break;
                    MetalVariable var = getVariable(head.args.getFirst());
                    if (var == null) break;
                    evaluate(head.headArgs.get(1)).onReceive(val -> {
                        val.setRawDouble(var.getAsRawDouble() * val.getAsRawDouble());
                        applyValue(var, val);
                    });
                }
                case "CONTINUE" -> {
                    if (current.type == CodeBlockType.LOOP){
                        StackPoint exitPoint = stack.getLast();
                        tryReturnByStack();
                        exitPoint.returnable.complete(MetalVariable.fromBool(false));
                        return;
                    }
                    jumpLocalStart();
                    return;
                }
                case "BREAK" -> {
                    if (current.type == CodeBlockType.LOOP){
                        current.stopped = true;
                        StackPoint exitPoint = stack.getLast();
                        tryReturnByStack();
                        exitPoint.returnable.complete(MetalVariable.fromBool(false));
                        return;
                    }
                }
                case "CALL_SYSTEM" -> {
                    MetalFuture result = callSystem(head,currentAction);
                    processObject(line,removePointsAtBrackets(line,false),result,1);
                }
                case "MAKE_LOCAL_VAR" -> {
                    if (head.args.size() < 3) break;
                    VariableType type = VariableType.fromStr(head.args.getFirst().toUpperCase());
                    if (type == null) break;
                    String name = head.args.get(1);
                    evaluate(head.headArgs.get(2)).onReceive(val -> {
                        if (type != VariableType.OBJECT && type != VariableType.STRING)
                            makeLocalVariable(name, new MetalVariable(type, val.getAsRawDouble()),true);
                        else if (val.isA(VariableType.OBJECT))
                            makeLocalVariable(name, MetalVariable.fromObject(val.asObject()),true);
                            else if(val.isA(VariableType.STRING)){
                                makeLocalVariable(name, MetalVariable.fromString(val.asString()),true);
                            }
                    });
                }
                case "BIND_ARGS_TO_SYSTEM" -> {
                    bindArgsToSystem(head);
                }
                case "RETURN" -> {
                    MetalFuture resultFuture = evaluate(head.headArgs.getFirst());
                    resultFuture.onReceive(result -> {
                        if (current.type == CodeBlockType.LOOP){
                            while(current.type == CodeBlockType.LOOP && !stack.isEmpty()){
                                current.stopped = true;
                                tryReturnByStack();
                            }
                            if (!stack.isEmpty()){
                                StackPoint last = stack.getLast();
                                tryReturnByStack();
                                last.returnable.complete(result);
                            }
                            return;
                        }
                        if (!stack.isEmpty()) {
                            StackPoint last = stack.getLast();
                            if (last.returnable != null) {
                                tryReturnByStack();
                                last.returnable.complete(result);
                                return;
                            }
                        }
                        
                        if (tryReturnByStack()) {
                            return;
                        }
                        
                        if (isInvoke() && !head.headArgs.isEmpty()) {
                            invokeResult = result;
                            end = true;
                            return;
                        }
                    });
                    return;
                }
                case "ACCEPT_INVOKES" -> {
                    host.acceptInvokes = true;
                }
                case "ABORT" -> end = true;
                default -> {
                    if (!tryAssignVariable(line)) {
                        MetalFuture objectResult = runObject(cmd,head);
                        if (objectResult == null) {
                            MetalFuture var = evaluateJFunction(cmd, head.headArgs);
                            processObject(line,removePointsAtBrackets(line,false),var,1);
                        }
                        else {
                            processObject(line,removePointsAtBrackets(line,false),objectResult,2);
                        }
                    }
                }
            }
            if (!skipActionInc)
            currentAction++;
        }
        private MetalFuture processObject(String line, String cleanLine, MetalFuture lastResult, int iteration) {
            int prevPoint = getPointIndex(cleanLine, iteration);
            if (prevPoint == -1) return lastResult;
            int point = getPointIndex(cleanLine, iteration + 1);
            String fullMethod = point == -1 ? line.substring(prevPoint + 1) : line.substring(prevPoint + 1, point);
            int bracketPos = fullMethod.indexOf("(");
            if (bracketPos == -1) return lastResult;
            
            String bracket = fullMethod.substring(bracketPos);
            String method = fullMethod.substring(0, bracketPos);
            Bracket head = parseBrackets(bracket);
            
            MetalFuture resultFuture = new MetalFuture();
            
            lastResult.onReceive(lastVar -> {
                MetalFuture methodResult = preRunCompleteObject(lastVar, method, head);
                methodResult.onReceive(methodValue -> {
                    processObject(line, cleanLine, MetalFuture.completed(methodValue), iteration + 1)
                        .onReceive(finalResult -> {
                            resultFuture.complete(finalResult);
                        });
                });
            });
            
            return resultFuture;
        }
        private String removePointsAtBrackets(String text, boolean startsWithBracket){
            int balance = startsWithBracket ? -1 : 0;
            int figureBalance = 0;
            StringBuilder builder = new StringBuilder(text);
            for (int i = 0; i < text.length(); i++){
                char at = text.charAt(i);
                if (at == '.' && (balance > 0 || figureBalance > 0)) builder.setCharAt(i,' ');
                else if (at == '(' && figureBalance <= 0) balance++;
                    else if (at == ')' && figureBalance <= 0) balance--;
                        else if(at == '{') figureBalance++;
                            else if(at == '}' && figureBalance > 0) figureBalance--;
                        
            }
            return builder.toString();
        }
        private boolean tryReturnByStack(){
            if (!stack.isEmpty()) {
                current = stack.getLast().block();
                currentAction = stack.getLast().startAction() + 1;
                LOCAL_VARIABLES = stack.getLast().LOCAL_VARIABLES();
                DYNAMIC_LOCAL_VARIABLES = stack.getLast().DYNAMIC_LOCAL_VARIABLES();
                stack.removeLast();
                return true;
            }
            return false;
        }
        public void addDelay(int duration) {
            this.waitTicks += duration;
        }
        public void jumpStart(){
            for (StackPoint p : stack) {
                CodeBlock block = p.block();
                if (block.type == CodeBlockType.LOOP) block.stopped = true;
            }
            currentAction = 0;
            current = starter;
            LOCAL_VARIABLES.clear();
            DYNAMIC_LOCAL_VARIABLES.clear();
            stack.clear();
        }
        public void jumpLocalStart(){
            DYNAMIC_LOCAL_VARIABLES.clear();
            currentAction = 0;
        }
        public boolean exitStack(){
            if (stack.isEmpty()) return false;
            for (StackPoint p : stack) {
                CodeBlock block = p.block();
                if (block.type == CodeBlockType.LOOP) block.stopped = true;
            }
            StackPoint first = stack.getFirst();
            current = first.block();
            currentAction = first.startAction() + 1;
            LOCAL_VARIABLES = first.LOCAL_VARIABLES();
            DYNAMIC_LOCAL_VARIABLES = first.DYNAMIC_LOCAL_VARIABLES();
            stack.clear();
            return true;
        }
        public boolean hasNext() {
            if (end) return false;
            if(!stack.isEmpty()) return true;
            return getSemicolonIndex(current.getContent(), currentAction + 1) != -1;
        }
        private boolean hasNextAtBlock() {
            if (end) return false;
            return getSemicolonIndex(current.getContent(), currentAction + 1) != -1;
        }
        private String getObjectName(String cmd) {
            if (cmd.startsWith("$") || cmd.startsWith("#")) {
                int point = cmd.indexOf(".");
                if (point >= cmd.length()) {
                    return null;
                }
                String name = cmd.substring(0,point);
                return name;
            }
            return null;
        }
        private static int getPointIndex(String text,int c) {
            int index = -1;
            for (int i = 0; i < c; i++) {
                index = text.indexOf('.', index + 1);
                if (index == -1) break;
            }
            return index;
        }
        private MetalFuture preRunCompleteObject(MetalVariable var, String methodName, Bracket head) {
            if (var!=null && var.isA(VariableType.OBJECT) && var.asObject() instanceof MetalFuture) {
                MetalFuture future = (MetalFuture) var.asObject();
                MetalFuture result = new MetalFuture();
                future.onReceive(readyVar -> {
                    preRunCompleteObjectSync(readyVar, methodName, head)
                        .onReceive(v -> result.complete(v));
                });
                return result;
            }
            
            return preRunCompleteObjectSync(var, methodName, head);
        }

        private MetalFuture preRunCompleteObjectSync(MetalVariable var, String methodName, Bracket head) {
            if (var == null || var.isNotA(VariableType.OBJECT)) {
                for (MetalModule module : host.modules.values()) {
                    module.onError(this, "Calling Object methods with " + ((var == null) ? "NULL" : var.getType().getStr()), ErrorType.UNKNOWN_FUNCTION);
                }
                return MetalFuture.completed(var);
            }
            return runCompleteObject(var.asObject(), methodName, head);
        }
        private MetalFuture runCompleteObject(MetalObject object, String methodName, Bracket head) {
            if (object == null) return MetalFuture.completed(null);
            List<MetalFuture> evalArgs = new ArrayList<>();
            for (String arg : head.headArgs) {
                evalArgs.add(evaluate(arg));
            }
            MetalFuture resultFuture = new MetalFuture();
            waitForAll(evalArgs, resolvedArgs -> {
                try {
                    Method method = findMethod(object.getClass(), methodName, head.args.size());
                    if (method != null) {
                        Object o = method.invoke(object, resolvedArgs, this);
                        if (o instanceof MetalVariable) {
                            resultFuture.complete((MetalVariable) o);
                        } else if (o instanceof MetalFuture) {
                            ((MetalFuture) o).onReceive(resultFuture::complete);
                        } else {
                            resultFuture.complete(new MetalVariable(0));
                        }
                    } else {
                        resultFuture.complete(new MetalVariable(0));
                    }
                } catch (Exception e) {
                    resultFuture.complete(new MetalVariable(0));
                }
            });
            
            return resultFuture;
        }
        
        
        private MetalFuture runObject(String cmd, Bracket head) {
            if (cmd.startsWith("$") || cmd.startsWith("#")) {
                int point = cmd.indexOf(".");
                if (point >= cmd.length()) {
                    return MetalFuture.completed(null);
                }
                String name = cmd.substring(0, point);
                String methodName = cmd.substring(point + 1);
                
                MetalVariable var = getVariable(name);
                return preRunCompleteObject(var, methodName, head);
            }
            return null;
        }
    }
}