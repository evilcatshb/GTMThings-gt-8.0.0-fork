package com.hepdd.gtmthings.api.pattern;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.OriginOffset;
import com.gregtechceu.gtceu.api.multiblock.PredicateContext;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.PatternSlice;
import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;
import com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.common.block.CoilBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.hepdd.gtmthings.common.item.AdvancedTerminalBehavior;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Triplet;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class AdvancedBlockPattern {

    static Direction[] FACINGS = { Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST, Direction.UP,
            Direction.DOWN };
    static Direction[] FACINGS_H = { Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST };

    private final BlockPattern blockPattern;

    public AdvancedBlockPattern(BlockPattern blockPattern) {
        this.blockPattern = blockPattern;
    }

    public static AdvancedBlockPattern getAdvancedBlockPattern(BlockPattern blockPattern) {
        return new AdvancedBlockPattern(blockPattern);
    }

    public void autoBuild(Player player, PatternState patternState,
                          AdvancedTerminalBehavior.AutoBuildSetting autoBuildSetting) {
        Level world = player.level();
        MultiblockControllerMachine controller = patternState.getController();
        if (controller == null) return;

        clearWorldState(patternState);
        BlockPos centerPos = controller.getBlockPos();
        Direction facing = controller.getFrontFacing();
        Direction upwardsFacing = controller.getUpwardsFacing();
        boolean isFlipped = controller.isFlipped();

        PredicateContext context = patternState.getContext();
        Map<BlockPos, Object> blocks = new HashMap<>();
        Set<BlockPos> placeBlockPos = new HashSet<>();
        blocks.put(centerPos, controller);

        PatternSlice[] slices = blockPattern.getSlices();
        RelativeDirection[] directions = blockPattern.getDirections();
        OriginOffset offset = blockPattern.getOffset();

        // Resolve the pattern's OWN slice/row/column axes into absolute world directions,
        // exactly like BlockPattern.checkSlice() does. Most gtceu multiblocks are declared
        // as MultiblockPatternBuilder.start(FRONT, UP, RIGHT) - NOT FRONT/UP/LEFT - so
        // hardcoding UP/LEFT/FRONT here (as the previous version did) walks the column axis
        // backwards relative to what checkPatternAt() validates, producing a mirrored/rotated
        // build. That mirror shows up as a "90 degree rotation" in creative (raw block
        // placement doesn't care), and in survival it causes most target cells to land on
        // already-occupied terrain, so the "already filled" check below silently skips them -
        // meaning nothing gets placed and nothing gets pulled from the player's inventory.
        Direction absoluteSlice = directions[0].getRelativeFacing(facing, upwardsFacing, isFlipped);
        Direction absoluteString = directions[1].getRelativeFacing(facing, upwardsFacing, isFlipped);
        Direction absoluteChar = directions[2].getRelativeFacing(facing, upwardsFacing, isFlipped);

        BlockPos.MutableBlockPos originStart = centerPos.mutable();
        offset.apply(originStart, facing, upwardsFacing, isFlipped);
        BlockPos originPos = originStart.immutable();

        int sliceOffset = 0;
        for (PatternSlice slice : slices) {
            int repeats = slice.getActualRepeats();
            if (repeats <= 0) repeats = slice.getMinRepeats() > 0 ? slice.getMinRepeats() : 1;

            for (int r = 0; r < repeats; r++) {
                context.clearLayerCounts();
                char[][] pattern = slice.getPattern();
                BlockPos.MutableBlockPos sliceStart = originPos.mutable().move(absoluteSlice, sliceOffset);
                BlockPos.MutableBlockPos stringStart = sliceStart.mutable();
                for (int y = 0; y < pattern.length; y++) {
                    char[] row = pattern[y];
                    BlockPos.MutableBlockPos charCursor = stringStart.mutable();
                    for (int x = 0; x < row.length; x++) {
                        char c = row[x];
                        BlockPos pos = charCursor.immutable();
                        charCursor.move(absoluteChar);

                        MultiPredicate multiPredicate = blockPattern.getPredicates().get(c);
                        if (multiPredicate == null) continue;

                        updateWorldState(patternState, pos, multiPredicate);

                        ItemStack coilItemStack = null;
                        if (!world.isEmptyBlock(pos)) {
                            if (world.getBlockState(pos).getBlock() instanceof CoilBlock coilBlock && autoBuildSetting.isReplaceCoilMode()) {
                                coilItemStack = coilBlock.asItem().getDefaultInstance();
                            } else {
                                blocks.put(pos, world.getBlockState(pos));
                                continue;
                            }
                        }

                        // Get all base predicates from this multi predicate
                        List<BasePredicate> allPredicates = getAllPredicates(multiPredicate);

                        boolean find = false;
                        BlockInfo[] infos = new BlockInfo[0];

                        // First try limited predicates (with min count requirements)
                        for (BasePredicate limit : allPredicates) {
                            if (limit.getMinSliceCount() > 0 && autoBuildSetting.isPlaceHatch(limit.getCandidates().toArray(new BlockInfo[0]))) {
                                int curr = context.getSliceCount(limit);
                                if (curr < limit.getMinSliceCount() &&
                                        (limit.getMaxSliceCount() == -1 || curr < limit.getMaxSliceCount())) {
                                    context.incrementSliceCount(limit);
                                } else {
                                    continue;
                                }
                            } else {
                                continue;
                            }
                            infos = limit.getCandidates().toArray(new BlockInfo[0]);
                            find = true;
                            break;
                        }

                        if (!find) {
                            for (BasePredicate limit : allPredicates) {
                                if (limit.getMinCount() > 0 && autoBuildSetting.isPlaceHatch(limit.getCandidates().toArray(new BlockInfo[0]))) {
                                    int curr = context.getGlobalCount(limit);
                                    if (curr < limit.getMinCount() && (limit.getMaxCount() == -1 || curr < limit.getMaxCount())) {
                                        context.incrementGlobalCount(limit);
                                    } else {
                                        continue;
                                    }
                                } else {
                                    continue;
                                }
                                infos = limit.getCandidates().toArray(new BlockInfo[0]);
                                find = true;
                                break;
                            }
                        }

                        if (!find) {
                            // No limited predicates matched, try all
                            for (BasePredicate limit : allPredicates) {
                                if (!autoBuildSetting.isPlaceHatch(limit.getCandidates().toArray(new BlockInfo[0]))) {
                                    continue;
                                }
                                if (limit.getMaxSliceCount() != -1 &&
                                        context.getSliceCount(limit) >= limit.getMaxSliceCount()) {
                                    continue;
                                }
                                if (limit.getMaxCount() != -1 &&
                                        context.getGlobalCount(limit) >= limit.getMaxCount()) {
                                    continue;
                                }
                                context.incrementSliceCount(limit);
                                context.incrementGlobalCount(limit);
                                infos = ArrayUtils.addAll(infos, limit.getCandidates().toArray(new BlockInfo[0]));
                            }
                        }

                        List<ItemStack> candidates = autoBuildSetting.apply(infos);

                        if (autoBuildSetting.isReplaceCoilMode() && coilItemStack != null && !candidates.isEmpty() && ItemStack.isSameItem(candidates.get(0), coilItemStack))
                            continue;

                        // check inventory
                        Triplet<ItemStack, IItemHandler, Integer> result = foundItem(player, candidates, autoBuildSetting.getIsUseAE());
                        ItemStack found = result.getA();
                        IItemHandler handler = result.getB();
                        int foundSlot = result.getC();

                        if (found == null) continue;

                        // check can get old coilBlock
                        IItemHandler holderHandler = null;
                        int holderSlot = -1;
                        if (autoBuildSetting.isReplaceCoilMode() && coilItemStack != null) {
                            Pair<IItemHandler, Integer> holderResult = foundHolderSlot(player, coilItemStack);
                            holderHandler = holderResult.getFirst();
                            holderSlot = holderResult.getSecond();

                            if (holderHandler != null && holderSlot < 0) {
                                continue;
                            }
                        }

                        if (autoBuildSetting.isReplaceCoilMode() && coilItemStack != null) {
                            world.removeBlock(pos, true);
                            if (holderHandler != null) holderHandler.insertItem(holderSlot, coilItemStack, false);
                        }

                        BlockItem itemBlock = (BlockItem) found.getItem();
                        BlockPlaceContext contextPlace = new BlockPlaceContext(world, player, InteractionHand.MAIN_HAND,
                                found, BlockHitResult.miss(player.getEyePosition(0), Direction.UP, pos));
                        InteractionResult interactionResult = itemBlock.place(contextPlace);
                        if (interactionResult != InteractionResult.FAIL) {
                            placeBlockPos.add(pos);
                            if (handler != null) {
                                handler.extractItem(foundSlot, 1, false);
                            }
                        }
                        if (world.getBlockEntity(pos) instanceof MetaMachine machine) {
                            blocks.put(pos, machine);
                        } else {
                            blocks.put(pos, world.getBlockState(pos));
                        }
                    }
                    stringStart.move(absoluteString);
                }
                sliceOffset++;
            }
        }

        Direction frontFacing = controller.getFrontFacing();
        blocks.forEach((pos, block) -> { // adjust facing
            if (!(block instanceof MultiblockControllerMachine)) {
                if (block instanceof BlockState && placeBlockPos.contains(pos)) {
                    resetFacing(pos, (BlockState) block, frontFacing, (p, f) -> {
                        Object object = blocks.get(p.relative(f));
                        return object == null ||
                                (object instanceof BlockState && ((BlockState) object).getBlock() == Blocks.AIR);
                    }, state -> world.setBlock(pos, state, 3));
                } else if (block instanceof MetaMachine machine) {
                    resetFacing(pos, machine.getBlockState(), frontFacing, (p, f) -> {
                        Object object = blocks.get(p.relative(f));
                        if (object == null || (object instanceof BlockState blockState && blockState.isAir())) {
                            return machine.isFacingValid(f);
                        }
                        return false;
                    }, state -> world.setBlock(pos, state, 3));
                }
            }
        });
    }

    private List<BasePredicate> getAllPredicates(MultiPredicate multiPredicate) {
        List<BasePredicate> result = new ArrayList<>(multiPredicate.predicates());
        for (MultiPredicate child : multiPredicate.children()) {
            result.addAll(getAllPredicates(child));
        }
        return result;
    }

    private Triplet<ItemStack, IItemHandler, Integer> foundItem(Player player, List<ItemStack> candidates, int isUseAE) {
        ItemStack found = null;
        IItemHandler handler = null;
        int foundSlot = -1;
        if (!player.isCreative()) {
            var foundHandler = getMatchStackWithHandler(candidates,
                    player.getCapability(ForgeCapabilities.ITEM_HANDLER), player, isUseAE);
            if (foundHandler != null) {
                foundSlot = foundHandler.firstInt();
                handler = foundHandler.second();
                found = handler.getStackInSlot(foundSlot).copy();
            }
        } else {
            for (ItemStack candidate : candidates) {
                found = candidate.copy();
                if (!found.isEmpty() && found.getItem() instanceof BlockItem) {
                    break;
                }
                found = null;
            }
        }
        return new Triplet<>(found, handler, foundSlot);
    }

    private Pair<IItemHandler, Integer> foundHolderSlot(Player player, ItemStack coilItemStack) {
        IItemHandler handler = null;
        int foundSlot = -1;
        if (!player.isCreative()) {
            handler = player.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            for (int i = 0; i < handler.getSlots(); i++) {
                @NotNull
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) {
                    if (foundSlot < 0) {
                        foundSlot = i;
                    }
                } else if (ItemStack.isSameItemSameTags(coilItemStack, stack) && (stack.getCount() + 1) <= stack.getMaxStackSize()) {
                    foundSlot = i;
                }
            }
        }

        return new Pair<>(handler, foundSlot);
    }

    private void clearWorldState(PatternState patternState) {
        patternState.getContext().reset();
    }

    private void updateWorldState(PatternState patternState, BlockPos posIn, MultiPredicate predicate) {
        // Update context for position tracking
        PredicateContext context = patternState.getContext();
        context.updatePos(posIn);
    }

    @Nullable
    private static IntObjectPair<IItemHandler> getMatchStackWithHandler(
                                                                        List<ItemStack> candidates,
                                                                        LazyOptional<IItemHandler> cap,
                                                                        Player player, int isUseAE) {
        IItemHandler handler = cap.resolve().orElse(null);
        if (handler == null) {
            return null;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            @NotNull
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            @NotNull
            LazyOptional<IItemHandler> stackCap = stack.getCapability(ForgeCapabilities.ITEM_HANDLER);
            if (stackCap.isPresent()) {
                var rt = getMatchStackWithHandler(candidates, stackCap, player, isUseAE);
                if (rt != null) {
                    return rt;
                }
            } else if (isUseAE == 1 && stack.getItem() instanceof WirelessTerminalItem terminalItem && stack.hasTag() && stack.getTag().contains("accessPoint", 10)) {
                IGrid grid = terminalItem.getLinkedGrid(stack, player.level(), player);
                if (grid != null) {
                    MEStorage storage = grid.getStorageService().getInventory();
                    for (ItemStack candidate : candidates) {
                        if (storage.extract(AEItemKey.of(candidate), 1, Actionable.MODULATE, null) > 0) {
                            NonNullList<ItemStack> stacks = NonNullList.withSize(1, candidate);
                            IItemHandler handler1 = new ItemStackHandler(stacks);
                            return IntObjectPair.of(0, handler1);
                        }
                    }
                }

            } else if (candidates.stream().anyMatch(candidate -> ItemStack.isSameItemSameTags(candidate, stack)) &&
                    !stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                        return IntObjectPair.of(i, handler);
                    }
        }
        return null;
    }

    private void resetFacing(BlockPos pos, BlockState blockState, Direction facing,
                             BiPredicate<BlockPos, Direction> checker, Consumer<BlockState> consumer) {
        if (blockState.hasProperty(BlockStateProperties.FACING)) {
            tryFacings(blockState, pos, checker, consumer, BlockStateProperties.FACING,
                    facing == null ? FACINGS : ArrayUtils.addAll(new Direction[] { facing }, FACINGS));
        } else if (blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            tryFacings(blockState, pos, checker, consumer, BlockStateProperties.HORIZONTAL_FACING,
                    facing == null || facing.getAxis() == Direction.Axis.Y ? FACINGS_H :
                            ArrayUtils.addAll(new Direction[] { facing }, FACINGS_H));
        }
    }

    private void tryFacings(BlockState blockState, BlockPos pos, BiPredicate<BlockPos, Direction> checker,
                            Consumer<BlockState> consumer, Property<Direction> property, Direction[] facings) {
        Direction found = null;
        for (Direction facing : facings) {
            if (checker.test(pos, facing)) {
                found = facing;
                break;
            }
        }
        if (found == null) {
            found = Direction.NORTH;
        }
        consumer.accept(blockState.setValue(property, found));
    }
}
