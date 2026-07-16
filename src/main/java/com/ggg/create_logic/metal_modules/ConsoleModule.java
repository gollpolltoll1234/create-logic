package com.ggg.create_logic.metal_modules;

import com.ggg.create_logic.lexer.MetalLexer;
import com.ggg.create_logic.metal.Metal;
import com.ggg.create_logic.metal.Metal.MetalVariable;
import com.ggg.create_logic.metal.MetalFuture;
import com.google.common.collect.ImmutableList;

import java.util.*;

public class ConsoleModule extends Metal.MetalModule {
    public static enum MessageType {
        INFO(0),
        WARN(1),
        ERROR(2);
        private final int id;
        private static final Map<Integer, MessageType> ID_TO_TYPE = new HashMap<>();
        static {
            for (MessageType type : MessageType.values()) {
                ID_TO_TYPE.put(type.id,type);
            }
        }
        MessageType(int id) {
            this.id = id;
        }
        public int getId(){
            return id;
        }
        public static MessageType fromId(int id) {
            return ID_TO_TYPE.get(id);
        }
    }
    public record ConsoleMessage(MessageType type, String content) { }
    private final List<ConsoleMessage> MESSAGES = Collections.synchronizedList(new ArrayList<>());
    private static int LIMIT = 100;
    private boolean dirty = false;
    public boolean isDirty(){
        return dirty;
    }
    public void markClean(){
        dirty = false;
    }
    public static void setLimit(int limit) {
        LIMIT = limit;
    }
    public ConsoleModule(Metal metal) {
        super(metal);
    }
    public static ConsoleModule getConsole(Metal.Script script){
        Metal metal = script.getHost();
        if(metal.getModule("console") instanceof ConsoleModule console) return console;
        else return null;
    }
    
    public static MetalFuture buildAsync(Metal.Script script, Metal.Script.Bracket head) {
        MetalFuture result = new MetalFuture();
        ConsoleModule c = getConsole(script);
        List<MetalFuture> futures = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        
        for (int i = 0; i < head.args.size(); i++) {
            String str = head.args.get(i);
            if (str.startsWith("~") && str.length() >= 2) {
                parts.add(str.substring(1));
            } else {
                MetalFuture future = c.evaluate(script, head.headArgs.get(i));
                futures.add(future);
                parts.add(null);
            }
        }
        if (futures.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (String part : parts) {
                if (part != null) builder.append(part);
            }
            result.complete(MetalVariable.fromString(builder.toString()));
            return result;
        }
        Metal.waitForAll(futures,(values -> {
            StringBuilder builder = new StringBuilder();
            int futureIndex = 0;
            for (String part : parts) {
                if (part != null) {
                    builder.append(part);
                } else {
                    builder.append(Metal.varToStr(values.get(futureIndex++)));
                }
                builder.append(" ");
            }
            
            result.complete(MetalVariable.fromString(builder.toString()));
        }));
        
        return result;
    }
    @Override
    protected boolean handleAdvanced(Metal.Script script, String cmd, Metal.Script.Bracket head) {
        switch (cmd) {
            case "PRINT" -> {
                buildAsync(script,head).onReceive(str -> {
                    info(str.asString());
                });
                return true;
            }
            case "WARN" -> {
                buildAsync(script,head).onReceive(str -> {
                    warn(str.asString());
                });
                return true;
            }
            case "ERROR" -> {
                buildAsync(script,head).onReceive(str -> {
                    error(str.asString());
                });
                return true;
            }
        }
        return false;
    }
    public static void register() {
        MetalLexer.addKeyword("PRINT");
        MetalLexer.addKeyword("WARN");
        MetalLexer.addKeyword("ERROR");
        Metal.registerModule("console",ConsoleModule.class);
    }
    private void append(String text,MessageType type) {
        synchronized(MESSAGES) {
            MESSAGES.add(new ConsoleMessage(type, text));
            if (MESSAGES.size() > LIMIT) MESSAGES.removeFirst();
        }
        dirty = true;
    }
    public void info(String text) {
        append(text,MessageType.INFO);
    }
    public void warn(String text) {
        append(text,MessageType.WARN);
    }
    public void error(String text) {
        append(text,MessageType.ERROR);
    }
    public List<ConsoleMessage> getConsoleContent(){
        synchronized (MESSAGES) {
            return ImmutableList.copyOf(MESSAGES);
        }
    }
}
