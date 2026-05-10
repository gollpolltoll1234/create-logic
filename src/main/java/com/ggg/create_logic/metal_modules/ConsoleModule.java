package com.ggg.create_logic.metal_modules;

import com.ggg.create_logic.ModMain;
import com.ggg.create_logic.lexer.MetalLexer;
import com.ggg.create_logic.metal.Metal;
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
    private String varToStr(Metal.MetalVariable variable) {
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
            case null -> {
                return "NULL";
            }
        }
    }
    private String build(Metal.Script script, Metal.Script.Bracket head) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < head.args.size(); i++) {
            String str = head.args.get(i);
            if (str.startsWith("~") && str.length() >= 2) builder.append(str.substring(1));
            else builder.append(varToStr(evaluate(script,head.headArgs.get(i))));
            if (i + 1 < head.args.size()) builder.append(" ");
        }
        return builder.toString();
    }
    @Override
    protected boolean handleAdvanced(Metal.Script script, String cmd, Metal.Script.Bracket head) {
        switch (cmd) {
            case "PRINT" -> {
                info(build(script,head));
                return true;
            }
            case "WARN" -> {
                warn(build(script,head));
                return true;
            }
            case "ERROR" -> {
                error(build(script,head));
                return true;
            }
        }
        return false;
    }
    static {
        MetalLexer.addKeyword("PRINT");
        MetalLexer.addKeyword("WARN");
        MetalLexer.addKeyword("ERROR");
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
