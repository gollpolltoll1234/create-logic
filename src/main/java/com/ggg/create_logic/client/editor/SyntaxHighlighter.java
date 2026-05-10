package com.ggg.create_logic.client.editor;

import com.ggg.create_logic.lexer.MetalLexer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class SyntaxHighlighter {

    private static final Map<MetalLexer.TokenType, Integer> COLOR_SCHEME = Map.ofEntries(
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.MAKE_VAR, 0xFF55FFFF),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.MAKE_LOCAL_VAR, 0xFF44AAAA),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.SET, 0xFF55FF55),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.ADD, 0xFFDDAA55 ),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.SUB, 0xFFDDAA55 ),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.DIV, 0xFFDDAA55 ),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.MUL, 0xFFDDAA55 ),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.GLOBAL_VARIABLE, 0xFFFFFF55),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.LOCAL_VARIABLE, 0xFFAADD55),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.JAVA_FUNCTION, 0xFF88AAFF),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.CALL_SYSTEM, 0xFFAA55FF),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.RETURN, 0xFFFF6666),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.CONTINUE, 0xFFFFAA66),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.EXIT_STACK, 0xFFFF3333),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.ENTRYPOINT, 0xFF44FF44),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.VARIABLE_TYPE, 0xFF00AAAA),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.BOOLEAN_CONSTANT, 0xFF66D9AA),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.NUMBER, 0xFFAAFF55),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.KEYWORD, 0xFFFF55FF),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.PUNCTUATION, 0xFFAAAAAA),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.OPERATOR, 0xFFFFAA55),
            new AbstractMap.SimpleEntry<>(MetalLexer.TokenType.UNKNOWN, 0xFFFF5555)
    );
    private static final Map<MetalLexer.TokenType,Integer> COLOR_CHANGES = new HashMap<>();

    public static void overwriteColor(MetalLexer.TokenType type, int color) {
        COLOR_CHANGES.put(type,color);
    }

    public MutableComponent highlight(String text) {
        MetalLexer lexer = new MetalLexer();
        List<MetalLexer.Token> tokens = lexer.tokenize(text);
        MutableComponent result = Component.empty();
        int lastEnd = 0;
        boolean powerMode = false;
        for (int idx = 0; idx < tokens.size(); idx++) {
            MetalLexer.Token token = tokens.get(idx);
            if (token.start > lastEnd) {
                result.append(Component.literal(text.substring(lastEnd, token.start)));
            }
            if ((token.type == MetalLexer.TokenType.MAKE_VAR ||
                    token.type == MetalLexer.TokenType.MAKE_LOCAL_VAR) &&
                    idx + 2 < tokens.size() &&
                    tokens.get(idx + 2).type == MetalLexer.TokenType.VARIABLE_TYPE &&
                    tokens.get(idx + 2).text.equals("POWER")) {
                powerMode = true;
            }

            Integer color = COLOR_CHANGES.get(token.type);
            if (color == null) color = COLOR_SCHEME.get(token.type);
            if (color != null && token.type != MetalLexer.TokenType.WHITESPACE) {
                if (token.type == MetalLexer.TokenType.NUMBER && powerMode) {
                    try {
                        int value = Integer.parseInt(token.text);
                        if (value < 0 || value > 15) {
                            result.append(Component.literal(token.text)
                                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF3333))));
                        } else {
                            result.append(Component.literal(token.text)
                                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
                        }
                    } catch (NumberFormatException e) {
                        result.append(Component.literal(token.text)
                                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
                    }
                } else {
                    result.append(Component.literal(token.text)
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
                }
            } else {
                result.append(Component.literal(token.text));
            }

            if (powerMode && token.type == MetalLexer.TokenType.PUNCTUATION && token.text.equals(")")) {
                powerMode = false;
            }

            lastEnd = token.end;
        }

        if (lastEnd < text.length()) {
            result.append(Component.literal(text.substring(lastEnd)));
        }

        return result;
    }
}