package com.ggg.create_logic.blocks;

import com.ggg.create_logic.ModMain;
import com.ggg.create_logic.ModRegistry;
import com.ggg.create_logic.blockentities.ComputerBlockEntity;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ComputerBlock extends BaseEntityBlock {
    public static final BooleanProperty ACTIVATED = BooleanProperty.create("activated");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<ComputerBlock> CODEC = simpleCodec(ComputerBlock::new);
    private static Class<?> computerScreenClass = null;
    private static Class<?> consoleScreenClass = null;
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
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ComputerBlockEntity(pos, state);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (level.isClientSide) {
            if (computerScreenClass == null) try {
                computerScreenClass = Class.forName("com.ggg.create_logic.client.ComputerScreen");
                consoleScreenClass = Class.forName("com.ggg.create_logic.client.ConsoleScreen");
            } catch (ClassNotFoundException e) {
                ModMain.LOGGER.warn(e.getLocalizedMessage());
            }
            if (player.isShiftKeyDown()) return InteractionResult.SUCCESS;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ComputerBlockEntity computerBE) {
                if (computerBE.isActive())
                    openConsoleScreen(computerBE);
                else
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
    private void openComputerScreen(ComputerBlockEntity be) {
        if (computerScreenClass != null)
            try {
                computerScreenClass.getMethod("setScreen",ComputerBlockEntity.class).invoke(null,be);
            } catch (Exception e) {
                ModMain.LOGGER.warn(e.getLocalizedMessage());
            }
    }
    private void openConsoleScreen(ComputerBlockEntity be) {
        if (consoleScreenClass != null) {
            try {
                consoleScreenClass.getMethod("setScreen",ComputerBlockEntity.class).invoke(null,be);
            } catch (Exception e) {
                ModMain.LOGGER.warn(e.getLocalizedMessage());
            }
        }
    }
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return createTickerHelper(
                type,
                ModRegistry.COMPUTER_BE.get(),
                ComputerBlockEntity::tickAll
        );
    }


    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
