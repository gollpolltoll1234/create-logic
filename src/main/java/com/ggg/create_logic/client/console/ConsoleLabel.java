package com.ggg.create_logic.client.console;

import com.ggg.create_logic.metal_modules.ConsoleModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.awt.Color;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ConsoleLabel extends AbstractWidget {
    private List<ConsoleModule.ConsoleMessage> messages = null;
    private final Font font;
    private int scrollOffset = 0;
    public ConsoleLabel(int x, int y, int width, int height, Font font) {
        super(x, y, width, height, Component.empty());
        this.font = font;
    }
    public Font getFont(){
        return font;
    }
    public List<ConsoleModule.ConsoleMessage> getMessages(){
        return messages;
    }
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF1E1E1E);
        int borderColor = isFocused() ? 0xFFFFFFFF : 0xFF888888;
        graphics.renderOutline(getX(), getY(), width, height, borderColor);
        if (messages == null) return;
        int textX = getX() + 4;
        int textY = getY() + 4 - scrollOffset;
        for (int row = 0; row < messages.size(); row++) {
            int lineY = textY + row * (font.lineHeight + 2);
            if (lineY > getY() && lineY < getY() + height) {
                ConsoleModule.ConsoleMessage msg = messages.get(row);
                int color = msg.type() == ConsoleModule.MessageType.INFO ? Color.WHITE.getRGB() : msg.type() == ConsoleModule.MessageType.WARN ? Color.YELLOW.getRGB() : Color.RED.getRGB();
                graphics.drawString(font, Component.literal(msg.content()), textX, lineY, color);
            }
        }
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
    private int getMaxScroll() {
        if (messages == null || messages.isEmpty()) return 0;
        int totalHeight = messages.size() * (font.lineHeight + 2);
        return Math.max(0, totalHeight - height + 8);
    }
    public void updateMessages(List<ConsoleModule.ConsoleMessage> messages) {
        this.messages = messages;
        scrollOffset = getMaxScroll();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

    }
}
