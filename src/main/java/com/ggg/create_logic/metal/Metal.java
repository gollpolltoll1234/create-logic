package com.ggg.create_logic.metal;

import com.ggg.create_logic.ModMain;
import com.ggg.create_logic.blockentities.ComputerBlockEntity;
import com.ggg.create_logic.redstone_link_custom.RedstoneLinkInjector;
import com.simibubi.create.Create;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Metal {
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
    public enum RunContext {
        GLOBAL,
        INIT,
        RUN
    }
    public enum VariableType {
        POWER("POWER"),
        INT("INT"),
        DOUBLE("DOUBLE"),
        BOOL("BOOL");
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
        private final VariableType type;
        private boolean boolValue = false;
        private int intValue = 0;
        private double doubleValue = 0;
        private int powerValue = 0;

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

        public double getAsRawDouble() {
            return switch (type) {
                case BOOL -> boolValue ? 1.0 : 0.0;
                case INT -> (double) intValue;
                case DOUBLE -> doubleValue;
                case POWER -> (double) asPower();
            };
        }
        public void setRawDouble(double rawDouble) {
            switch (type) {
                case DOUBLE -> doubleValue = rawDouble;
                case POWER -> powerValue = Math.max(((int)rawDouble) % 16,0);
                case BOOL -> boolValue = rawDouble > 0;
                case INT -> intValue = (int)rawDouble;
            }
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
        public MetalVariable(VariableType type, double value) {
            this.type = type;
            this.name = "temp";
            setRawDouble(value);
        }
        private void setName(String name) {
            this.name = name;
        }
    }
    @FunctionalInterface
    public interface MetalJavaFunction {
        MetalVariable execute(Script script,List<MetalVariable> args);
    }
    private static final Map<String, MetalJavaFunction> JAVA_FUNCTIONS = new HashMap<>();
    private final Map<String, MetalVariable> VARIABLES = new ConcurrentHashMap<>();
    private void putVariable(String name, MetalVariable variable) {
        variable.setName(name);
        VARIABLES.put(name,variable);
    }
    public static void register(String name, MetalJavaFunction func) {
        if (!JAVA_FUNCTIONS.containsKey(name))
            JAVA_FUNCTIONS.put(name, func);
    }
    private Script.CodeBlock INIT_BLOCK = null;
    private Script.CodeBlock TICK_BLOCK = null;
    private Script.CodeBlock ROOT_BLOCK = null;
    private Script TICK_SCRIPT = null;
    private final Map<String,List<AbstractMap.SimpleEntry<String, VariableType>>> ARGUMENT_MAP = new HashMap<>();
    private boolean isRunning = false;
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
    public void setCode(String code) {
        if (isRunning) return;
        ROOT_BLOCK = new Script.CodeBlock(Script.CodeBlockType.SYSTEM,code,0);
    }
    public void run(){
        if (isRunning) return;
        if (ROOT_BLOCK == null) return;
        isRunning = true;
        ROOT_BLOCK.end();
        Script rootScript = new Script(RunContext.GLOBAL,ROOT_BLOCK,this,false);
        while (rootScript.hasNext()) rootScript.next();
        INIT_BLOCK = findSystemBlock(ROOT_BLOCK,"INIT");
        if (INIT_BLOCK != null) {
            INIT_BLOCK.end();
            Script initScript = new Script(RunContext.INIT,INIT_BLOCK,this,false);
            try {
                while (initScript.hasNext()) initScript.next();
            } catch (Exception e) {
                ModMain.LOGGER.error("Exception in Metal at Server Thread: " + e.getMessage());
            }
        }
        TICK_BLOCK = findSystemBlock(ROOT_BLOCK,"TICK");
        if (TICK_BLOCK != null) {
            TICK_SCRIPT = new Script(RunContext.RUN,TICK_BLOCK,this, false);
            scriptsToAdd.add(TICK_SCRIPT);
        }
        tickRegistered.add(this);
    }
    public void stop(){
        if (!isRunning) return;
        isRunning = false;
        if(TICK_SCRIPT != null)TICK_SCRIPT.end = true;
        VARIABLES.clear();
        acceptInvokes = false;
        if (owner instanceof ComputerBlockEntity be) {
            for (Integer id : INJECTORS.keySet()) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(be.getLevel(),getInjector(id));
            }
        }
        INJECTORS.clear();
        STORE.clear();
        ARGUMENT_MAP.clear();
        if (!tickRegistered.remove(this)) ModMain.LOGGER.error("Cannot remove Metal object from tick listeners");
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
    private static final List<Script> scriptList = new ArrayList<>();
    private static final List<Script> scriptsToRemove = new ArrayList<>();
    private static final List<Script> scriptsToAdd = new ArrayList<>();
    private static final List<Metal> tickRegistered = new ArrayList<>();
    private static Thread runThread = null;
    private static boolean isStarted = false;
    private boolean acceptInvokes = false;
    public boolean isAcceptInvokes(){
        return acceptInvokes;
    }
    private void tick(){
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
    public static void tickAll(){
        for (Metal metal : tickRegistered) {
            metal.tick();
        }
    }
    public static void launch(){
        if (isStarted) return;
        isStarted = true;
        runThread = new Thread(() -> {
            ModMain.LOGGER.info("Metal THREAD was been started!");
            while (isStarted) {
                for (Script script : scriptList) {
                    if (script.end) {
                        scriptsToRemove.add(script);
                    } else if(script.hasNext()) {
                        try {
                            script.next();
                        } catch (Exception e) {
                            ModMain.LOGGER.error("Error in Metal THREAD: " + e.getMessage());
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
                    ModMain.LOGGER.error(e.toString());
                }
            }
            ModMain.LOGGER.info("Metal THREAD was been terminated!");
        });
        runThread.setName("Metal-Thread");
        runThread.start();
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
    public static void terminate(){
        isStarted = false;
    }
    public static class Script {
        private final boolean isInvoke;
        private MetalVariable invokeResult = null;
        private final Metal host;
        public boolean end = false;
        private long operations = 0;
        public long getOperations(){
            return operations;
        }
        public boolean isInvoke(){
            return isInvoke;
        }
        public MetalVariable getInvokeResult(){
            return invokeResult;
        }
        public void setArguments(Map<String, MetalVariable> args) {
            if (!isInvoke) return;
            LOCAL_VARIABLES = args;
        }
        private MetalVariable evaluate(String expr) {
            String finalExpr = expr.trim();
            if (finalExpr.equalsIgnoreCase("TRUE")) return new MetalVariable(VariableType.BOOL, 1);
            if (finalExpr.equalsIgnoreCase("FALSE")) return new MetalVariable(VariableType.BOOL, 0);
            return new Object() {
                int pos = -1, ch;

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

                MetalVariable parse() {
                    nextChar();
                    return parseLogicalOr();
                }
                MetalVariable parseLogicalOr() {
                    MetalVariable x = parseLogicalAnd();
                    for (;;) {
                        if (eat("||")) {
                            boolean res = x.asBool() || parseLogicalAnd().asBool();
                            x = new MetalVariable(VariableType.BOOL, res ? 1 : 0);
                        } else return x;
                    }
                }
                MetalVariable parseLogicalAnd() {
                    MetalVariable x = parseComparison();
                    for (;;) {
                        if (eat("&&")) {
                            boolean res = x.asBool() && parseComparison().asBool();
                            x = new MetalVariable(VariableType.BOOL, res ? 1 : 0);
                        } else return x;
                    }
                }
                MetalVariable parseComparison() {
                    MetalVariable x = parseExpression();
                    for (;;) {
                        if (eat("==")) x = compare(x, parseExpression(), "==");
                        else if (eat("!=")) x = compare(x, parseExpression(), "!=");
                        else if (eat(">=")) x = compare(x, parseExpression(), ">=");
                        else if (eat("<=")) x = compare(x, parseExpression(), "<=");
                        else if (eat(">"))  x = compare(x, parseExpression(), ">");
                        else if (eat("<"))  x = compare(x, parseExpression(), "<");
                        else if (eat("^")) {
                            boolean res = x.asBool() ^ parseExpression().asBool();
                            x = new MetalVariable(VariableType.BOOL, res ? 1 : 0);
                        }
                        else return x;
                    }
                }
                MetalVariable parseExpression() {
                    MetalVariable x = parseTerm();
                    for (;;) {
                        if      (eat("+")) x = new MetalVariable(VariableType.DOUBLE, x.getAsRawDouble() + parseTerm().getAsRawDouble());
                        else if (eat("-")) x = new MetalVariable(VariableType.DOUBLE, x.getAsRawDouble() - parseTerm().getAsRawDouble());
                        else return x;
                    }
                }
                MetalVariable parseTerm() {
                    MetalVariable x = parseFactor();
                    for (;;) {
                        if      (eat("*")) x = new MetalVariable(VariableType.DOUBLE, x.getAsRawDouble() * parseFactor().getAsRawDouble());
                        else if (eat("/")) x = new MetalVariable(VariableType.DOUBLE, x.getAsRawDouble() / parseFactor().getAsRawDouble());
                        else return x;
                    }
                }
                MetalVariable parseFactor() {
                    if (eat("!")) return new MetalVariable(VariableType.BOOL, parseFactor().asBool() ? 0 : 1);
                    if (eat("+")) return parseFactor();
                    if (eat("-")) return new MetalVariable(VariableType.DOUBLE, -parseFactor().getAsRawDouble());
                    MetalVariable x;
                    if (eat("(")) {
                        x = parseLogicalOr();
                        eat(")");
                    } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                        int startPos = this.pos;
                        while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                        x = new MetalVariable(VariableType.DOUBLE, Double.parseDouble(finalExpr.substring(startPos, this.pos)));
                    } else if (ch == '$' || ch == '#' || Character.isLetter(ch)) {
                        boolean isGlobalVar = ch == '$';
                        int startPos = this.pos;
                        while ((Character.isLetterOrDigit(ch) || ch == '$' || ch == '_' || ch == ' ' || ch == '#') && ch != '(' && ch != ')') {
                            nextChar();
                        }
                        String name = finalExpr.substring(startPos, this.pos).trim();
                        if (ch == '(') {
                            int bracketStart = this.pos;
                            int balance = 0;
                            while (this.pos < finalExpr.length()) {
                                if (ch == '(') balance++;
                                if (ch == ')') balance--;
                                nextChar();
                                if (balance == 0) break;
                            }
                            String funcFullBody = finalExpr.substring(bracketStart, this.pos);
                            Bracket bracket = parseBrackets(funcFullBody);
                            List<MetalVariable> evalArgs = new ArrayList<>();
                            for (String arg : bracket.rawArgs) {
                                MetalVariable var = evaluate(arg);
                                evalArgs.add(var);
                            }
                            x = evaluateCompleteJFunction(name, evalArgs);
                            if (x == null) x = new MetalVariable(VariableType.BOOL, 0);
                        } else {
                            if (isGlobalVar) x = host.VARIABLES.getOrDefault(name, new MetalVariable(VariableType.BOOL,0));
                            else {
                                x = LOCAL_VARIABLES.getOrDefault(name, null);
                                if (x == null) x = DYNAMIC_LOCAL_VARIABLES.getOrDefault(name, new MetalVariable(VariableType.BOOL, 0));
                            }
                        }
                    } else {
                        ModMain.LOGGER.error("Unexpected char: " + (char)ch + " at pos " + pos);
                        return new MetalVariable(VariableType.BOOL, 0);
                    }
                    return x;
                }


                MetalVariable compare(MetalVariable left, MetalVariable right, String op) {
                    double l = left.getAsRawDouble();
                    double r = right.getAsRawDouble();
                    boolean res = switch (op) {
                        case "==" -> l == r;
                        case "!=" -> l != r;
                        case ">" -> l > r;
                        case "<" -> l < r;
                        case ">=" -> l >= r;
                        case "<=" -> l <= r;
                        default -> false;
                    };
                    return new MetalVariable(VariableType.BOOL, res ? 1 : 0);
                }
            }.parse();
        }

        public static int getSemicolonIndex(String text,int c) {
            int index = -1;
            for (int i = 0; i < c; i++) {
                index = text.indexOf(';', index + 1);
                if (index == -1) break;
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
                head.writeHead(at);
                if (isBracketStarted) {
                    current.write(at);
                }
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
            SYSTEM("SYSTEM",List.of(RunContext.GLOBAL));
            private final List<RunContext> runContexts;
            private final String text;
            CodeBlockType(String text,List<RunContext> runContexts) {
                this.text = text;
                this.runContexts = runContexts;
            }

            public String getText() {
                return text;
            }

            public boolean canRunAt(RunContext context) {
                return runContexts.contains(context);
            }
        }
        public static class CodeBlock {
            private CodeBlock parent;
            public final CodeBlockType type;
            private final int layer;
            private String content;
            private final List<CodeBlock> codeBlocks = new ArrayList<>();
            private boolean ended = false;
            private int returnActionIndex = 0;
            private String name = "";
            public void setReturnActionIndex(int index) {
                this.returnActionIndex = index;
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

            public int getReturnActionIndex() {
                return returnActionIndex;
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
        private CodeBlock current;
        private Map<String, MetalVariable> LOCAL_VARIABLES;
        private Map<String, MetalVariable> DYNAMIC_LOCAL_VARIABLES;
        private final CodeBlock starter;
        private final RunContext context;
        private int currentAction = 0;
        private int blockLength;
        private int waitTicks = 0;
        public static class Bracket {
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
            private void processArgs(char symbol,int argType) {
                if (symbol == ',') {
                    if (argType == 0) {
                        headArgs.add(currentHeadArg.toString().trim());
                        currentHeadArg = new StringBuilder();
                    }
                    else if (argType == 1) {
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
                processArgs(symbol,1);
                if (symbol == '(' || symbol == ')') {
                    return;
                }
                processArgs(symbol,2);
                content += symbol;
            }
            public void writeHead(char symbol) {
                if (closed) return;
                processArgs(symbol,0);
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
                }
                if (!currentRawArg.isEmpty()) {
                    rawArgs.add(currentRawArg.toString());
                }
                if (!currentHeadArg.isEmpty()) {
                    headArgs.add(currentHeadArg.toString());
                }
            }
            public Bracket(){}
        }

        private record StackPoint(int startAction, CodeBlock block, Map<String,MetalVariable> LOCAL_VARIABLES, Map<String,MetalVariable> DYNAMIC_LOCAL_VARIABLES) { }
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
        private MetalVariable evaluateJFunction(String name,List<String> args) {
            List<MetalVariable> evalArgs = new ArrayList<>();
            for (String arg : args) evalArgs.add(evaluate(arg));
            return evaluateCompleteJFunction(name,evalArgs);
        }
        private MetalVariable evaluateCompleteJFunction(String name, List<MetalVariable> args) {
            if (JAVA_FUNCTIONS.containsKey(name)) {
                return JAVA_FUNCTIONS.get(name).execute(this, args);
            }
            return null;
        }
        private void applyValue(MetalVariable target, MetalVariable source) {
            double val = source.getAsRawDouble();
            target.setRawDouble(val);
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
        private final List<StackPoint> stack = new ArrayList<>();
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
        public void next() {
            if (end) return;
            if (!hasNext()) return;
            operations++;
            if (waitTicks > 0) {
                waitTicks--;
                return;
            }
            int i = getSemicolonIndex(current.content, currentAction + 1);
            int li = getSemicolonIndex(current.content, currentAction);

            if (i == -1) return;

            String line = (li == -1) ? current.content.substring(0, i)
                    : current.content.substring(li + 1, i);
            line = line.trim();

            if (line.isEmpty()) {
                skip();
                return;
            }

            int bracketStart = line.indexOf("(");
            if (bracketStart == -1) {
                skip();
                return;
            }
            String cmd = line.substring(0, bracketStart).trim().toUpperCase();
            Bracket head = parseBrackets(line.substring(bracketStart));
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
                if (cmd.equals("CONTINUE") || cmd.equals("SYSTEM") || cmd.equals("WAIT") || cmd.equals("RERUN")) {
                    skip();
                    return;
                }
            }
            if (isInvoke && cmd.equals("WAIT")) {
                skip();
                ModMain.LOGGER.info("Skipped WAIT bc its invoke");
                return;
            }
            switch (cmd) {
                case "MAKE_VAR" -> {
                    if (context == RunContext.INIT) {
                        if (head.args.size() < 3) break;
                        VariableType type = VariableType.fromStr(head.args.getFirst().toUpperCase());
                        if (type == null) break;
                        String name = head.args.get(1);
                        MetalVariable val = evaluate(head.headArgs.get(2));
                        host.putVariable(name, new MetalVariable(type, val.getAsRawDouble()));
                    }
                }
                case "MAKE_ITEM_VAR" -> {
                    if (context == RunContext.INIT) {
                        if (head.args.size() < 2) break;
                        String name = head.args.getFirst();
                        String itemId = head.args.get(1).trim();
                        host.putVariable(name, new MetalVariable(VariableType.INT, getItemId(itemId)));
                    }
                }
                case "SET" -> {
                    if (head.args.size() < 2) break;
                    MetalVariable var = getVariable(head.args.getFirst());
                    MetalVariable val = evaluate(head.headArgs.get(1));
                    if (var != null) applyValue(var, val);
                }
                case "IF" -> {
                    MetalVariable cond = evaluate(head.headContent);
                    if (!cond.asBool()) {
                        currentAction++;
                        currentAction = getBlockIfEndOrElseActionIndex();
                    }
                }
                case "ELSE" -> {
                    int ind = getBlockIfEndActionIndex();
                    if (ind <= blockLength) currentAction = ind;
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
                        current.setReturnActionIndex(endIdx + 1);
                        current.addCodeBlock(target);
                    }
                }
                case "ADD" -> {
                    if (head.args.size() < 2) break;
                    MetalVariable var = getVariable(head.args.getFirst());
                    if (var == null) break;
                    MetalVariable val = evaluate(head.headArgs.get(1));
                    val.setRawDouble(var.getAsRawDouble() + val.getAsRawDouble());
                    applyValue(var, val);
                }
                case "WAIT" -> {
                    if (head.args.isEmpty()) break;
                    MetalVariable ticks = evaluate(head.headArgs.getFirst());
                    if (ticks != null) {
                        waitTicks += (int)ticks.getAsRawDouble();
                    }
                }
                case "SUB" -> {
                    if (head.args.size() < 2) break;
                    MetalVariable var = getVariable(head.args.getFirst());
                    if (var == null) break;
                    MetalVariable val = evaluate(head.headArgs.get(1));
                    val.setRawDouble(var.getAsRawDouble() - val.getAsRawDouble());
                    applyValue(var, val);
                }
                case "DIV" -> {
                    if (head.args.size() < 2) break;
                    MetalVariable var = getVariable(head.args.getFirst());
                    if (var == null) break;
                    MetalVariable val = evaluate(head.headArgs.get(1));
                    val.setRawDouble(var.getAsRawDouble() / val.getAsRawDouble());
                    applyValue(var, val);
                }
                case "MUL" -> {
                    if (head.args.size() < 2) break;
                    MetalVariable var = getVariable(head.args.getFirst());
                    if (var == null) break;
                    MetalVariable val = evaluate(head.headArgs.get(1));
                    val.setRawDouble(var.getAsRawDouble() * val.getAsRawDouble());
                    applyValue(var, val);
                }
                case "CONTINUE" -> {
                    jumpLocalStart();
                    return;
                }
                case "RERUN" -> {
                    jumpStart();
                    return;
                }
                case "EXIT_STACK" -> {
                    exitStack();
                    return;
                }
                case "MAKE_LOCAL_VAR" -> {
                    if (head.args.size() < 3) break;
                    VariableType type = VariableType.fromStr(head.args.getFirst().toUpperCase());
                    if (type == null) break;
                    String name = head.args.get(1);
                    MetalVariable val = evaluate(head.headArgs.get(2));
                    makeLocalVariable(name, new MetalVariable(type, val.getAsRawDouble()),true);
                }
                case "BIND_ARGS_TO_SYSTEM" -> {
                    if (head.args.size() < 2) break;
                    String name = head.args.getFirst();
                    if (host.ARGUMENT_MAP.containsKey(name) || isReservedSystem(name) || host.findSystemBlock(host.ROOT_BLOCK,name) == null) break;
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
                case "CALL_SYSTEM" -> {
                    if (head.args.isEmpty()) break;
                    if (stack.size() > 16) break;
                    String name = head.args.getFirst();
                    if (isReservedSystem(name)) break;
                    if (host.ROOT_BLOCK == null) break;
                    CodeBlock target = host.findSystemBlock(host.ROOT_BLOCK,name);
                    if (target == null) break;
                    Map<String, MetalVariable> arguments = new HashMap<>();
                    if(head.args.size() > 1 && host.ARGUMENT_MAP.containsKey(name)) {
                        List<AbstractMap.SimpleEntry<String,VariableType>> requires = host.ARGUMENT_MAP.get(name);
                        for (int index = 0; index < requires.size(); index++) {
                            MetalVariable variable;
                            VariableType regType = requires.get(index).getValue();
                            if (index + 1 < head.args.size()) {
                                MetalVariable provided = evaluate(head.args.get(index + 1));
                                if (provided.isA(regType)) variable = provided;
                                else variable = new MetalVariable(regType,provided.getAsRawDouble());
                            } else variable = new MetalVariable(regType,0);
                            arguments.put(requires.get(index).getKey(),variable);
                        }
                    }
                    stack.add(new StackPoint(currentAction,current, LOCAL_VARIABLES, DYNAMIC_LOCAL_VARIABLES));
                    current = target;
                    DYNAMIC_LOCAL_VARIABLES = new HashMap<>();
                    LOCAL_VARIABLES = arguments;
                    currentAction = 0;
                    return;
                }
                case "RETURN" -> {
                    if(tryReturnByStack())
                        return;
                    if (isInvoke() && !head.headArgs.isEmpty()) {
                        invokeResult = evaluate(head.headArgs.getFirst());
                        end = true;
                        return;
                    }
                }
                case "ACCEPT_INVOKES" -> {
                    host.acceptInvokes = true;
                }
                case "ABORT" -> end = true;
                default -> evaluateJFunction(cmd,head.args);
            }
            currentAction++;
            if (!hasNext()) tryReturnByStack();
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
        public void exitStack(){
            if (stack.isEmpty()) return;
            StackPoint first = stack.getFirst();
            current = first.block();
            currentAction = first.startAction() + 1;
            LOCAL_VARIABLES = first.LOCAL_VARIABLES();
            DYNAMIC_LOCAL_VARIABLES = first.DYNAMIC_LOCAL_VARIABLES();
            stack.clear();
        }
        public boolean hasNext() {
            if (end) return false;
            return getSemicolonIndex(current.getContent(), currentAction + 1) != -1;
        }
    }
}
