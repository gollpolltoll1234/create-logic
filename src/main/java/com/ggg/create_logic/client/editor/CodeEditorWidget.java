package com.ggg.create_logic.client.editor;

import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CodeEditorWidget extends AbstractWidget {

    private final Font font;
    private String text = "";
    private int cursorPos = 0;
    private int selectionStart = 0;
    private int selectionEnd = 0;
    private int scrollOffset = 0;
    private final SyntaxHighlighter highlighter;
    private boolean selecting = false;

    public CodeEditorWidget(Font font, int x, int y, int width, int height, SyntaxHighlighter highlighter) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.highlighter = highlighter;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        this.cursorPos = Math.min(cursorPos, text.length());
        this.selectionStart = this.selectionEnd = cursorPos;
    }

    private String getSelectedText() {
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        return text.substring(start, end);
    }

    private void deleteSelection() {
        if (selectionStart == selectionEnd) return;
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        text = text.substring(0, start) + text.substring(end);
        cursorPos = start;
        selectionStart = selectionEnd = cursorPos;
        updateScroll();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF1E1E1E);
        int borderColor = isFocused() ? 0xFFFFFFFF : 0xFF888888;
        graphics.renderOutline(getX(), getY(), width, height, borderColor);

        String[] lines = text.split("\n", -1);
        int textX = getX() + 4;
        int textY = getY() + 4 - scrollOffset;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineY = textY + i * (font.lineHeight + 2);

            if (lineY > getY() && lineY < getY() + height) {
                renderSelection(graphics, line, i, textX, lineY);
                drawLineWithSpaces(graphics, line, textX, lineY);
            }
        }
        if (isFocused() && selectionStart == selectionEnd && (System.currentTimeMillis() / 500) % 2 == 0) {
            drawCursor(graphics);
        }
    }
    private void drawLineWithSpaces(GuiGraphics graphics, String line, int x, int y) {
        if (line.isEmpty()) return;
        graphics.drawString(font,highlighter.highlight(line),x,y, Color.WHITE.getRGB());
    }

    private void renderSelection(GuiGraphics graphics, String line, int lineIndex, int lineX, int lineY) {
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);

        int globalStart = getPositionAt(lineIndex, 0);
        int globalEnd = getPositionAt(lineIndex, line.length());

        int selStart = Math.max(start, globalStart);
        int selEnd = Math.min(end, globalEnd);

        if (selStart < selEnd) {
            int localStart = selStart - globalStart;
            int localEnd = selEnd - globalStart;

            String before = line.substring(0, localStart);
            String selected = line.substring(localStart, localEnd);

            int startX = lineX + font.width(before);
            int width = font.width(selected);

            graphics.fill(startX, lineY, startX + width, lineY + font.lineHeight, 0xFF3366CC);
        }
    }

    private int getPositionAt(int lineIndex, int charIndex) {
        String[] lines = text.split("\n", -1);
        int pos = 0;
        for (int i = 0; i < lineIndex && i < lines.length; i++) {
            pos += lines[i].length() + 1;
        }
        return pos + Math.min(charIndex, lines[lineIndex].length());
    }

    private void drawCursor(GuiGraphics graphics) {
        int[] cursor = getCursorPosition(cursorPos);
        int cursorX = getX() + 4 + font.width(cursor[1] == 0 ? "" : getTextUpToCursor(cursorPos, cursor[0], cursor[1]));
        int cursorY = getY() + 4 - scrollOffset + cursor[0] * (font.lineHeight + 2);
        graphics.fill(cursorX, cursorY, cursorX + 2, cursorY + font.lineHeight, 0xFFFFFFFF);
    }

    private int[] getCursorPosition(int pos) {
        String[] lines = text.split("\n", -1);
        int currentPos = 0;
        for (int i = 0; i < lines.length; i++) {
            if (pos <= currentPos + lines[i].length()) {
                return new int[]{i, pos - currentPos};
            }
            currentPos += lines[i].length() + 1;
        }
        return new int[]{lines.length - 1, lines[lines.length - 1].length()};
    }

    private String getTextUpToCursor(int pos, int lineIndex, int colIndex) {
        String[] lines = text.split("\n", -1);
        return lines[lineIndex].substring(0, Math.min(colIndex, lines[lineIndex].length()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) return false;
        boolean shift = (modifiers & 1) != 0;
        boolean ctrl = (modifiers & 2) != 0;
        if (ctrl && keyCode == 65) {
            selectionStart = 0;
            selectionEnd = text.length();
            cursorPos = text.length();
            return true;
        }
        if (ctrl && keyCode == 67) {
            Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
            return true;
        }
        if (ctrl && keyCode == 86) {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (!clipboard.isEmpty()) {
                if (selectionStart != selectionEnd) deleteSelection();
                text = text.substring(0, cursorPos) + clipboard + text.substring(cursorPos);
                cursorPos += clipboard.length();
                selectionStart = selectionEnd = cursorPos;
                updateScroll();
            }
            return true;
        }
        if (ctrl && keyCode == 88) {
            Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
            deleteSelection();
            return true;
        }
        switch (keyCode) {
            case 256:
                setFocused(false);
                return true;

            case 259:
                if (selectionStart != selectionEnd) {
                    deleteSelection();
                } else if (cursorPos > 0) {
                    if (shift) {
                        selectionStart = cursorPos;
                        cursorPos--;
                        selectionEnd = cursorPos;
                    } else {
                        text = text.substring(0, cursorPos - 1) + text.substring(cursorPos);
                        cursorPos--;
                        selectionStart = selectionEnd = cursorPos;
                    }
                }
                updateScroll();
                return true;

            case 261:
                if (selectionStart != selectionEnd) {
                    deleteSelection();
                } else if (cursorPos < text.length()) {
                    if (shift) {
                        selectionStart = cursorPos;
                        cursorPos++;
                        selectionEnd = cursorPos;
                    } else {
                        text = text.substring(0, cursorPos) + text.substring(cursorPos + 1);
                        selectionStart = selectionEnd = cursorPos;
                    }
                }
                updateScroll();
                return true;

            case 257:
            case 335:
                if (selectionStart != selectionEnd) deleteSelection();
                text = text.substring(0, cursorPos) + "\n" + text.substring(cursorPos);
                cursorPos++;
                selectionStart = selectionEnd = cursorPos;
                updateScroll();
                return true;

            case 262:
                if (cursorPos < text.length()) {
                    if (shift) {
                        if (selectionStart == selectionEnd) selectionStart = cursorPos;
                        cursorPos++;
                        selectionEnd = cursorPos;
                    } else {
                        cursorPos++;
                        selectionStart = selectionEnd = cursorPos;
                    }
                }
                updateScroll();
                return true;

            case 263:
                if (cursorPos > 0) {
                    if (shift) {
                        if (selectionStart == selectionEnd) selectionStart = cursorPos;
                        cursorPos--;
                        selectionEnd = cursorPos;
                    } else {
                        cursorPos--;
                        selectionStart = selectionEnd = cursorPos;
                    }
                }
                updateScroll();
                return true;

            case 264:
                moveCursorDown(shift);
                return true;

            case 265:
                moveCursorUp(shift);
                return true;

            case 268:
                moveToLineStart(shift);
                return true;

            case 269:
                moveToLineEnd(shift);
                return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveCursorUp(boolean shift) {
        int[] cursor = getCursorPosition(cursorPos);
        if (cursor[0] > 0) {
            String[] lines = text.split("\n", -1);
            int prevLineLength = lines[cursor[0] - 1].length();
            int newCol = Math.min(cursor[1], prevLineLength);

            if (shift) {
                if (selectionStart == selectionEnd) selectionStart = cursorPos;
            }

            int newPos = getPositionAt(cursor[0] - 1, newCol);
            cursorPos = newPos;

            if (shift) {
                selectionEnd = cursorPos;
            } else {
                selectionStart = selectionEnd = cursorPos;
            }
            updateScroll();
        }
    }

    private void moveCursorDown(boolean shift) {
        int[] cursor = getCursorPosition(cursorPos);
        String[] lines = text.split("\n", -1);
        if (cursor[0] < lines.length - 1) {
            int nextLineLength = lines[cursor[0] + 1].length();
            int newCol = Math.min(cursor[1], nextLineLength);

            if (shift) {
                if (selectionStart == selectionEnd) selectionStart = cursorPos;
            }

            cursorPos = getPositionAt(cursor[0] + 1, newCol);

            if (shift) {
                selectionEnd = cursorPos;
            } else {
                selectionStart = selectionEnd = cursorPos;
            }
            updateScroll();
        }
    }

    private void moveToLineStart(boolean shift) {
        int[] cursor = getCursorPosition(cursorPos);
        int newPos = getPositionAt(cursor[0], 0);

        if (shift) {
            if (selectionStart == selectionEnd) selectionStart = cursorPos;
            cursorPos = newPos;
            selectionEnd = cursorPos;
        } else {
            cursorPos = newPos;
            selectionStart = selectionEnd = cursorPos;
        }
        updateScroll();
    }

    private void moveToLineEnd(boolean shift) {
        int[] cursor = getCursorPosition(cursorPos);
        String[] lines = text.split("\n", -1);
        int newPos = getPositionAt(cursor[0], lines[cursor[0]].length());

        if (shift) {
            if (selectionStart == selectionEnd) selectionStart = cursorPos;
            cursorPos = newPos;
            selectionEnd = cursorPos;
        } else {
            cursorPos = newPos;
            selectionStart = selectionEnd = cursorPos;
        }
        updateScroll();
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isFocused()) return false;

        if (selectionStart != selectionEnd) deleteSelection();

        text = text.substring(0, cursorPos) + codePoint + text.substring(cursorPos);
        cursorPos++;
        selectionStart = selectionEnd = cursorPos;
        updateScroll();

        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            setFocused(true);
            selecting = true;
            updateCursorPosition(mouseX, mouseY);
            selectionStart = selectionEnd = cursorPos;
            return true;
        } else {
            setFocused(false);
            selecting = false;
            return false;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (selecting && isFocused()) {
            updateCursorPosition(mouseX, mouseY);
            selectionEnd = cursorPos;
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        selecting = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseOver(mouseX, mouseY)) {
            scrollOffset -= (int) (scrollY * 20);
            int maxScroll = getMaxScroll();
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        return false;
    }

    private void updateCursorPosition(double mouseX, double mouseY) {
        String[] lines = text.split("\n", -1);
        int clickedLine = (int)((mouseY - getY() - 4 + scrollOffset) / (font.lineHeight + 2));
        clickedLine = Math.max(0, Math.min(clickedLine, lines.length - 1));

        int clickX = (int)mouseX - getX() - 4;
        int charPos = 0;
        for (int i = 0; i <= lines[clickedLine].length(); i++) {
            String sub = lines[clickedLine].substring(0, Math.min(i, lines[clickedLine].length()));
            if (font.width(sub) > clickX && clickX >= 0) {
                charPos = i - 1;
                break;
            }
            charPos = i;
        }
        charPos = Math.max(0, Math.min(charPos, lines[clickedLine].length()));

        cursorPos = getPositionAt(clickedLine, charPos);
    }

    private void updateScroll() {
        int[] cursor = getCursorPosition(cursorPos);
        int lineY = cursor[0] * (font.lineHeight + 2);

        if (lineY < scrollOffset) {
            scrollOffset = lineY;
        }
        if (lineY + font.lineHeight > scrollOffset + height - 8) {
            scrollOffset = lineY + font.lineHeight - height + 8;
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, getMaxScroll()));
    }

    private int getMaxScroll() {
        String[] lines = text.split("\n", -1);
        int totalHeight = lines.length * (font.lineHeight + 2);
        return Math.max(0, totalHeight - height + 8);
    }
}