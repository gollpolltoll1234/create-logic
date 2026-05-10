package com.ggg.create_logic.client;

import com.ggg.create_logic.blockentities.ComputerBlockEntity;
import com.ggg.create_logic.client.console.ConsoleLabel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class ConsoleScreen extends Screen {
    private ConsoleLabel label;
    private final ComputerBlockEntity be;
    public ConsoleScreen(ComputerBlockEntity be) {
        super(Component.literal("Metal Console"));
        this.be = be;
    }
    @SuppressWarnings("unused")
    public static void setScreen(ComputerBlockEntity be){
        Minecraft.getInstance().setScreen(new ConsoleScreen(be));
    }
    @Override
    protected void init() {
        int w = (int)(this.width * 0.8);
        int h = (int)(this.height * 0.7);
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        label = new ConsoleLabel(x,y,w,h,font);
        label.updateMessages(be.getConsoleMessages());
        this.addRenderableWidget(label);
    }
    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (be.isConsoleDirty()) {
            label.updateMessages(be.getConsoleMessages());
            be.markConsoleClean();
        }
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
