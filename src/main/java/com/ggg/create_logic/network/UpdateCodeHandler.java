package com.ggg.create_logic.network;

import com.ggg.create_logic.blockentities.ComputerBlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class UpdateCodeHandler {
    public static void handle(final UpdateCodePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            var level = player.level();
            if (level.getBlockEntity(payload.pos()) instanceof ComputerBlockEntity be) {
                be.setSourceCode(payload.code());
            }
        });
    }
}