package com.ggg.create_logic.lexer;

import com.ggg.create_logic.metal.Metal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MetalLexer {
    private static final Set<String> VARIABLE_TYPES = Set.of(
            "INT", "DOUBLE", "BOOL", "POWER", "STRING", "OBJECT"
    );
    private static final Set<String> ENTRYPOINTS = Set.of(
            "INIT",
            "TICK"
    );
    private static final Set<String> KEYWORDS = Set.of(
            "MAKE_VAR",
            "MAKE_LOCAL_VAR",
            "SET", "ADD", "SUB", "DIV", "MUL",
            "IF", "ELSE", "END_IF", "START", "END_SYSTEM",
            "CALL_SYSTEM", "RETURN", "CONTINUE", "EXIT_STACK",
            "SYSTEM", "WAIT","RERUN","BIND_ARGS_TO_SYSTEM","ACCEPT_INVOKES","ABORT"
    );
    private static final Set<String> ADDITIONAL_KEYWORDS = new HashSet<>();
    public static void addKeyword(String keyword){
        ADDITIONAL_KEYWORDS.add(keyword);
    }

    public enum TokenType {
        MAKE_VAR,
        MAKE_LOCAL_VAR,
        SET, ADD, SUB, DIV, MUL,
        JAVA_FUNCTION,
        CALL_SYSTEM,
        RETURN, CONTINUE, BREAK,
        ENTRYPOINT,
        VARIABLE_TYPE,
        GLOBAL_VARIABLE,
        LOCAL_VARIABLE,
        BOOLEAN_CONSTANT,
        NUMBER,
        KEYWORD,
        PUNCTUATION,
        OPERATOR,
        WHITESPACE,
        STRING,
        UNKNOWN
    }

    public static class Token {
        public final TokenType type;
        public final String text;
        public final int start;
        public final int end;

        public Token(TokenType type, String text, int start, int end) {
            this.type = type;
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    private final Set<String> javaFunctions;

    public MetalLexer() {
        this.javaFunctions = Metal.getJavaFunctions();
    }

    public List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int length = text.length();
        boolean isString = false;
        boolean isBackslash = false;
        StringBuilder builder = new StringBuilder();
        int stringStart = 0;

        while (i < length) {
            char current = text.charAt(i);
            if (isString) {
                builder.append(current);
                if (current == '\\' && !isBackslash) isBackslash = true;
                else {
                    if (!isBackslash && current == '"') {
                        isString = false;
                        tokens.add(new Token(TokenType.STRING, builder.toString(), stringStart, i + 1));
                        builder = new StringBuilder();
                    }
                    isBackslash = false;
                }
                i++;
                continue;
            } else if(current == '"'){
                isString = true;
                stringStart = i;
                i++;
                builder.append('"');
                continue;
            }
            if (current == '.' && text.length() > i + 1 && Character.isLetter(text.charAt(i + 1))) {
                tokens.add(new Token(TokenType.PUNCTUATION, String.valueOf(current),i,i+1));
                i++;
                StringBuilder b = new StringBuilder();
                int start = i;
                while (i < text.length()){
                    if(Character.isLetterOrDigit(text.charAt(i))) b.append(text.charAt(i));
                    else break;
                    i++;
                }
                tokens.add(new Token(TokenType.JAVA_FUNCTION,b.toString(),start,i));
                continue;
            }
            if (Character.isWhitespace(current)) {
                int start = i;
                while (i < length && Character.isWhitespace(text.charAt(i))) i++;
                tokens.add(new Token(TokenType.WHITESPACE, text.substring(start, i), start, i));
                continue;
            }
            if (Character.isDigit(current) || (current == '-' && i + 1 < length && Character.isDigit(text.charAt(i + 1)))) {
                int start = i;
                if (current == '-') i++;
                boolean hasDot = false;
                while (i < length && (Character.isDigit(text.charAt(i)) || (!hasDot && text.charAt(i) == '.'))) {
                    if (text.charAt(i) == '.') hasDot = true;
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, text.substring(start, i), start, i));
                continue;
            }
            if (current == '$') {
                int start = i;
                i++;
                while (i < length && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) i++;
                tokens.add(new Token(TokenType.GLOBAL_VARIABLE, text.substring(start, i), start, i));
                continue;
            }
            if (current == '#') {
                int start = i;
                i++;
                while (i < length && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) i++;
                tokens.add(new Token(TokenType.LOCAL_VARIABLE, text.substring(start, i), start, i));
                continue;
            }
            if (Character.isLetter(current) || current == '_') {
                int start = i;
                while (i < length && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) i++;
                String word = text.substring(start, i);
                if (word.equals("TRUE") || word.equals("FALSE")) {
                    tokens.add(new Token(TokenType.BOOLEAN_CONSTANT, word, start, i));
                }
                else if (word.equals("MAKE_VAR")) {
                    tokens.add(new Token(TokenType.MAKE_VAR, word, start, i));
                }
                else if (word.equals("MAKE_LOCAL_VAR")) {
                    tokens.add(new Token(TokenType.MAKE_LOCAL_VAR, word, start, i));
                }
                else if (word.equals("SET")) {
                    tokens.add(new Token(TokenType.SET, word, start, i));
                }
                else if (word.equals("ADD")) {
                    tokens.add(new Token(TokenType.ADD, word, start, i));
                }
                else if (word.equals("SUB")) {
                    tokens.add(new Token(TokenType.SUB, word, start, i));
                }
                else if (word.equals("DIV")) {
                    tokens.add(new Token(TokenType.DIV, word, start, i));
                }
                else if (word.equals("MUL")) {
                    tokens.add(new Token(TokenType.MUL, word, start, i));
                }
                else if (word.equals("RETURN")) {
                    tokens.add(new Token(TokenType.RETURN, word, start, i));
                }
                else if (word.equals("CONTINUE")) {
                    tokens.add(new Token(TokenType.CONTINUE, word, start, i));
                }
                else if (word.equals("BREAK")) {
                    tokens.add(new Token(TokenType.BREAK, word, start, i));
                }
                else if (word.equals("CALL_SYSTEM")) {
                    tokens.add(new Token(TokenType.CALL_SYSTEM, word, start, i));
                }
                else if (ENTRYPOINTS.contains(word)) {
                    tokens.add(new Token(TokenType.ENTRYPOINT, word, start, i));
                }
                else if (KEYWORDS.contains(word)) {
                    tokens.add(new Token(TokenType.KEYWORD, word, start, i));
                }
                else if(ADDITIONAL_KEYWORDS.contains(word)) {
                    tokens.add(new Token(TokenType.KEYWORD, word, start, i));
                }
                else if (VARIABLE_TYPES.contains(word)) {
                    tokens.add(new Token(TokenType.VARIABLE_TYPE, word, start, i));
                }
                else if (javaFunctions.contains(word)) {
                    tokens.add(new Token(TokenType.JAVA_FUNCTION, word, start, i));
                }
                else {
                    tokens.add(new Token(TokenType.UNKNOWN, word, start, i));
                }
                continue;
            }
            if (isOperator(current)) {
                int start = i;
                if (current == '*' && i + 1 < length && text.charAt(i + 1) == '*') {
                    i += 2;
                    tokens.add(new Token(TokenType.OPERATOR, "**", start, i));
                    continue;
                }
                if (i + 1 < length && isTwoCharOperator(current, text.charAt(i + 1))) {
                    i += 2;
                    tokens.add(new Token(TokenType.OPERATOR, text.substring(start, i), start, i));
                    continue;
                }
                i++;
                tokens.add(new Token(TokenType.OPERATOR, String.valueOf(current), start, i));
                continue;
            }
            String punctuation = "(),;:{}";
            if (punctuation.indexOf(current) != -1) {
                tokens.add(new Token(TokenType.PUNCTUATION, String.valueOf(current), i, i + 1));
                i++;
                continue;
            }
            tokens.add(new Token(TokenType.UNKNOWN, String.valueOf(current), i, i + 1));
            i++;
        }
        return tokens;
    }

    private boolean isOperator(char c) {
        return "+-*/%=<>!&|^".indexOf(c) != -1;
    }

    private boolean isTwoCharOperator(char first, char second) {
        String pair = "" + first + second;
        return Set.of("==", "!=", "<=", ">=", "&&", "||").contains(pair);
    }
}