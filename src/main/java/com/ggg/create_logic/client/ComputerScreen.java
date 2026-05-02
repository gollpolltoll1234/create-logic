package com.ggg.create_logic.client;

import com.ggg.create_logic.blockentities.ComputerBlockEntity;
import com.ggg.create_logic.network.UpdateCodePayload;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class ComputerScreen extends net.minecraft.client.gui.screens.Screen {
    private net.minecraft.client.gui.components.MultiLineEditBox editBox;
    private final ComputerBlockEntity be;

    public ComputerScreen(ComputerBlockEntity be) {
        super(Component.literal("Metal Editor"));
        this.be = be;
    }

    @Override
    protected void init() {
        int w = (int)(this.width * 0.8);
        int h = (int)(this.height * 0.7);
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        editBox = new net.minecraft.client.gui.components.MultiLineEditBox(this.font, x, y, w, h, Component.empty(), Component.empty());
        editBox.setValue(be.getSourceCode());

        this.addRenderableWidget(editBox);
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Save"), (btn) -> {
            sendCodeToServer(editBox.getValue());
            this.onClose();
        }).bounds(this.width / 2 - 50, y + h + 10, 100, 20).build());
    }

    private void sendCodeToServer(String value) {
        PacketDistributor.sendToServer(new UpdateCodePayload(be.getBlockPos(), value));
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}