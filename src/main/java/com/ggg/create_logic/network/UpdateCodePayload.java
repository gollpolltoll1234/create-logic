package com.ggg.create_logic.network;

import com.ggg.create_logic.ModMain;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateCodePayload(BlockPos pos, String code) implements CustomPacketPayload {

    public static final Type<UpdateCodePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MOD_ID, "update_code"));

    public static final StreamCodec<FriendlyByteBuf, UpdateCodePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UpdateCodePayload::pos,
            ByteBufCodecs.stringUtf8(32767), UpdateCodePayload::code,
            UpdateCodePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
