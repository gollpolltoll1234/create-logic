package com.ggg.create_logic.client;

import com.ggg.create_logic.blockentities.ComputerBlockEntity;
import com.ggg.create_logic.client.editor.CodeEditorWidget;
import com.ggg.create_logic.client.editor.SyntaxHighlighter;
import com.ggg.create_logic.network.UpdateCodePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class ComputerScreen extends Screen {
    private CodeEditorWidget editBox;
    private final ComputerBlockEntity be;
    private final SyntaxHighlighter highlighter;
    public ComputerScreen(ComputerBlockEntity be) {
        super(Component.literal("Metal Editor"));
        this.be = be;
        this.highlighter = new SyntaxHighlighter();
    }
    @SuppressWarnings("unused")
    public static void setScreen(ComputerBlockEntity be){
        Minecraft.getInstance().setScreen(new ComputerScreen(be));
    }
    @Override
    protected void init() {
        int w = (int)(this.width * 0.8);
        int h = (int)(this.height * 0.7);
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        editBox = new CodeEditorWidget(this.font, x, y, w, h,
                highlighter);
        editBox.setText(be.getSourceCode());

        this.addRenderableWidget(editBox);
        this.addRenderableWidget(Button.builder(Component.literal("Save"), (btn) -> {
            sendCodeToServer(editBox.getText());
            this.onClose();
        }).bounds(this.width / 2 - 50, y + h + 10, 100, 20).build());
    }
    private void sendCodeToServer(String value) {
        PacketDistributor.sendToServer(new UpdateCodePayload(be.getBlockPos(), value));
    }
    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}