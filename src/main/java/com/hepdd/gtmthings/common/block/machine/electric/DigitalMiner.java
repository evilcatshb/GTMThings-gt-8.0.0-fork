package com.hepdd.gtmthings.common.block.machine.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.cover.filter.Filter;
import com.gregtechceu.gtceu.api.cover.filter.Filters;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.common.mui.GTMuiMachineUtil;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import com.gregtechceu.gtceu.utils.ISubscription;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import com.hepdd.gtmthings.api.capability.IDigitalMiner;
import com.hepdd.gtmthings.common.block.machine.trait.miner.DigitalMinerLogic;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class DigitalMiner extends WorkableTieredMachine implements IDigitalMiner, IMuiMachine, IDataInfoProvider {

    private long energyPerTick;

    @SaveField
    protected final CustomItemStackHandler filterInventory;
    @Getter
    protected Filter<ItemStack> itemFilter;

    @Getter
    @Setter
    @SaveField
    private int minerRadius;
    @Getter
    @Setter
    @SaveField
    private int minHeight;
    @Getter
    @Setter
    @SaveField
    private int maxHeight;

    private int silkLevel;
    private int fortuneLevel;

    @Nullable
    protected TickableSubscription autoOutputSubs;
    @Nullable
    protected ISubscription exportItemSubs;

    public DigitalMiner(BlockEntityCreationInfo info, int tier, Object... args) {
        super(info, tier, false,
                new DigitalMinerLogic(32, 0, 64, 0, null, (int) (40 / Math.pow(2, tier))),
                GTMachineUtils.defaultTankSizeFunction);
        this.energyPerTick = GTValues.VEX[tier - 1];
        this.filterInventory = createFilterItemHandler();
        this.fortuneLevel = 1;
        this.silkLevel = 0;
        this.minHeight = 0;
        this.maxHeight = 64;
        this.minerRadius = 32;
    }

    protected CustomItemStackHandler createFilterItemHandler() {
        var transfer = new CustomItemStackHandler();
        transfer.setFilter(item -> item.is(GTItems.ITEM_FILTER.get()) || item.is(GTItems.TAG_FILTER.get()));
        return transfer;
    }

    @Override
    public DigitalMinerLogic getRecipeLogic() {
        return (DigitalMinerLogic) super.getRecipeLogic();
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoOutputSubscription();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            filterChange();
            if (getLevel() instanceof ServerLevel serverLevel) {
                serverLevel.getServer().tell(new TickTask(0, this::updateAutoOutputSubscription));
            }
            exportItemSubs = exportItems.addChangedListener(this::updateAutoOutputSubscription);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (exportItemSubs != null) {
            exportItemSubs.unsubscribe();
            exportItemSubs = null;
        }
    }

    protected void updateAutoOutputSubscription() {
        var outputFacing = getFrontFacing();
        if (!exportItems.isEmpty() &&
                GTCapabilityHelper.getItemHandler(getLevel(), getBlockPos().relative(outputFacing),
                        outputFacing.getOpposite()) != null) {
            autoOutputSubs = subscribeServerTick(autoOutputSubs, this::autoOutput);
        } else if (autoOutputSubs != null) {
            autoOutputSubs.unsubscribe();
            autoOutputSubs = null;
        }
    }

    protected void autoOutput() {
        if (getOffsetTimer() % 5 == 0) {
            exportItems.exportToNearby(getFrontFacing());
        }
        updateAutoOutputSubscription();
    }

    @Override
    public boolean drainInput(boolean simulate) {
        long resultEnergy = energyContainer.getEnergyStored() - energyPerTick;
        if (resultEnergy >= 0L && resultEnergy <= energyContainer.getEnergyCapacity()) {
            if (!simulate)
                energyContainer.removeEnergy(energyPerTick);
            return true;
        }
        return false;
    }

    // ── GUI ──────────────────────────────────────────────────────────────────

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        int outputSlots = exportItems.getSlots();
        int cols = 9;
        int rows = Math.max(1, (outputSlots + cols - 1) / cols);
        String[] slotMatrix = new String[rows];
        for (int r = 0; r < rows; r++) slotMatrix[r] = "iiiiiiiii";

        var outputGrid = GTMuiMachineUtil.createSlotGroupFromInventory(
                exportItems.storage, "output_items", outputSlots, 'i', syncManager, slotMatrix);

        var filterGrid = GTMuiMachineUtil.createSlotGroupFromInventory(
                filterInventory, "filter_slot", 1, 'f', syncManager, "f");

        mainWidget
                .name("content")
                .coverChildren()
                .child(Flow.col()
                        .name("main_col")
                        .coverChildren()
                        .margin(2, 3)
                        .childPadding(2)
                        .child(Flow.row()
                                .name("config_row")
                                .coverChildren()
                                .childPadding(4)
                                .child(filterGrid)
                                .child(Flow.col()
                                        .name("settings_col")
                                        .coverChildren()
                                        .childPadding(2)
                                        .child(Flow.row().coverChildren().childPadding(2)
                                                .child(new TextWidget<>(Text.lang("gtmthings.gui.digital_miner.radius")))
                                                .child(new TextFieldWidget()
                                                        .setNumbers(() -> 1, () -> (int) (8 * Math.pow(2, getTier())))
                                                        .value(SyncHandlers.intNumber(this::getMinerRadius, v -> {
                                                            setMinerRadius(v);
                                                            resetRecipe();
                                                        }))))
                                        .child(Flow.row().coverChildren().childPadding(2)
                                                .child(new TextWidget<>(Text.lang("gtmthings.gui.digital_miner.min_height")))
                                                .child(new TextFieldWidget()
                                                        .setNumbers(
                                                                () -> getLevel() != null ? getLevel().getMinBuildHeight() : -64,
                                                                () -> getLevel() != null ? getLevel().getMaxBuildHeight() : 320)
                                                        .value(SyncHandlers.intNumber(this::getMinHeight, this::setMinHeight))))
                                        .child(Flow.row().coverChildren().childPadding(2)
                                                .child(new TextWidget<>(Text.lang("gtmthings.gui.digital_miner.max_height")))
                                                .child(new TextFieldWidget()
                                                        .setNumbers(
                                                                () -> getLevel() != null ? getLevel().getMinBuildHeight() : -64,
                                                                () -> getLevel() != null ? getLevel().getMaxBuildHeight() : 320)
                                                        .value(SyncHandlers.intNumber(this::getMaxHeight, this::setMaxHeight))))
                                        .child(Flow.row().coverChildren().childPadding(4)
                                                .child(new ButtonWidget<>().size(18).onMousePressed((ctx, btn) -> {
                                                    resetRecipe();
                                                    return true;
                                                }).tooltip(t -> t.add(Component.translatable("gtmthings.gui.digital_miner.reset"))))
                                                .child(new ButtonWidget<>().size(18).onMousePressed((ctx, btn) -> {
                                                    toggleSilk();
                                                    return true;
                                                }).tooltip(t -> t.add(Component.translatable("gtmthings.gui.digital_miner.silk")))))))
                        .child(outputGrid));
    }

    private void resetRecipe() {
        setWorkingEnabled(false);
        getRecipeLogic().resetRecipeLogic(this.minerRadius, this.minHeight, this.maxHeight, this.silkLevel, itemFilter);
    }

    private void filterChange() {
        this.itemFilter = null;
        if (!filterInventory.getStackInSlot(0).isEmpty())
            this.itemFilter = Filters.loadFilter(ItemStack.class, filterInventory.getStackInSlot(0));
        resetRecipe();
    }

    private void toggleSilk() {
        if (silkLevel == 0) {
            silkLevel = 1;
            energyPerTick = GTValues.VEX[getTier() - 1] * 4;
        } else {
            silkLevel = 0;
            energyPerTick = GTValues.VEX[getTier() - 1];
        }
        resetRecipe();
    }

    // ── IDataInfoProvider ────────────────────────────────────────────────────

    @NotNull
    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL ||
                mode == PortableScannerBehavior.DisplayMode.SHOW_MACHINE_INFO) {
            int workingArea = IDigitalMiner.getWorkingArea(getRecipeLogic().getCurrentRadius());
            return Collections.singletonList(
                    Component.translatable("gtceu.universal.tooltip.working_area", workingArea, workingArea));
        }
        return new ArrayList<>();
    }

    @Override
    protected InteractionResult onScrewdriverClick(ExtendedUseOnContext context) {
        Player playerIn = context.getPlayer();
        if (isRemote()) return InteractionResult.SUCCESS;
        if (!this.isActive()) {
            int currentRadius = getRecipeLogic().getCurrentRadius();
            if (currentRadius == 1)
                getRecipeLogic().setCurrentRadius(getRecipeLogic().getMaximumRadius());
            else if (playerIn.isShiftKeyDown())
                getRecipeLogic().setCurrentRadius(Math.max(1, Math.round(currentRadius / 2.0f)));
            else
                getRecipeLogic().setCurrentRadius(Math.max(1, currentRadius - 1));
            getRecipeLogic().resetArea(true);
            int workingArea = IDigitalMiner.getWorkingArea(getRecipeLogic().getCurrentRadius());
            playerIn.sendSystemMessage(
                    Component.translatable("gtceu.universal.tooltip.working_area", workingArea, workingArea));
        } else {
            playerIn.sendSystemMessage(Component.translatable("gtceu.multiblock.large_miner.errorradius"));
        }
        return InteractionResult.SUCCESS;
    }
}
