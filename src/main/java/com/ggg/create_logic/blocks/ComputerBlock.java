package com.ggg.create_logic.blocks;

import com.ggg.create_logic.ModRegistry;
import com.ggg.create_logic.blockentities.ComputerBlockEntity;
import com.ggg.create_logic.client.ComputerScreen;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

public class ComputerBlock extends BaseEntityBlock {
    public static final BooleanProperty ACTIVATED = BooleanProperty.create("activated");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<ComputerBlock> CODEC = simpleCodec(ComputerBlock::new);
    public ComputerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVATED, false));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVATED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComputerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            if (player.isShiftKeyDown()) return InteractionResult.SUCCESS;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ComputerBlockEntity computerBE) {
                openComputerScreen(computerBE);
            }
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ComputerBlockEntity computer) {

            if (player.isShiftKeyDown()) {
                if (computer.getMetal().isRunning()) {
                    computer.stop();
                    level.setBlock(pos, state.setValue(ACTIVATED, false), 3);
                    player.displayClientMessage(Component.literal("Computer Shutting Down..."), true);
                } else {
                    computer.run();
                    level.setBlock(pos, state.setValue(ACTIVATED, true), 3);
                    player.displayClientMessage(Component.literal("Computer Starting..."), true);
                }
            }
        }

        return InteractionResult.CONSUME;
    }
    @OnlyIn(Dist.CLIENT)
    private void openComputerScreen(ComputerBlockEntity be) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new ComputerScreen(be));
    }
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(
                type,
                ModRegistry.COMPUTER_BE.get(),
                ComputerBlockEntity::tickAll
        );
    }


    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
