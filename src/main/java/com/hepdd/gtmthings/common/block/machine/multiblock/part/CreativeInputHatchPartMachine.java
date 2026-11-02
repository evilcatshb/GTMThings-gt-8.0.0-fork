package com.hepdd.gtmthings.common.block.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.blockentity.IPaintable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDistinctPart;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraftforge.fluids.FluidStack;

import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.FluidSlotSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.slot.FluidSlot;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CreativeInputHatchPartMachine extends TieredIOPartMachine
                                           implements IDistinctPart, IMuiMachine, IPaintable {

    private final int SLOT_COUNT = 9;

    @SaveField
    public final NotifiableFluidTank tank;
    private final int slots;
    @Nullable
    protected TickableSubscription autoIOSubs;
    private Map<Integer, FluidStack> fluidMap;
    @SaveField
    private CustomFluidTank[] creativeTanks;

    public CreativeInputHatchPartMachine(BlockEntityCreationInfo info) {
        this(info, 9);
    }

    public CreativeInputHatchPartMachine(BlockEntityCreationInfo info, int slots, Object... args) {
        super(info, GTValues.MAX, IO.IN);
        this.slots = slots;
        this.tank = attachTrait(createTank());
        this.fluidMap = new HashMap<>();
        this.creativeTanks = new CustomFluidTank[slots];
        for (int i = 0; i < this.creativeTanks.length; i++) {
            this.creativeTanks[i] = new CustomFluidTank(1);
        }
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    protected NotifiableFluidTank createTank() {
        return new InfinityFluidTank(slots, Integer.MAX_VALUE, IO.IN);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        for (int i = 0; i < slots; i++) {
            if (this.creativeTanks[i] != null && !this.creativeTanks[i].getFluid().isEmpty()) {
                fluidMap.put(i, this.creativeTanks[i].getFluid());
            }
        }
        getHandlerList().setColor(getPaintingColor());
        updateTankSubscription();
    }

    @Override
    public void onPaintingColorChanged(int color) {
        getHandlerList().setColor(color, true);
    }

    @Override
    public int tintColor(int index) {
        if (index == 9) return getRealColor();
        return -1;
    }

    @Override
    public boolean supportsDistinct() {
        return io != IO.OUT;
    }

    protected void updateTankSubscription() {
        if (!fluidMap.isEmpty()) {
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoKeep);
        } else if (autoIOSubs != null) {
            clearAll();
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    protected void autoKeep() {
        if (getOffsetTimer() % 5 == 0) {
            for (int i = 0; i < slots; i++) {
                if (fluidMap.containsKey(i)) {
                    var mFluid = this.creativeTanks[i].getFluid().copy();
                    mFluid.setAmount(Integer.MAX_VALUE);
                    this.tank.setFluidInTank(i, mFluid);
                } else {
                    if (!this.tank.getFluidInTank(i).isEmpty()) {
                        this.tank.setFluidInTank(i, FluidStack.EMPTY);
                    }
                }
            }
            updateTankSubscription();
        }
    }

    protected void clearAll() {
        for (int i = 0; i < slots; i++) {
            if (!this.tank.getFluidInTank(i).isEmpty()) {
                this.tank.setFluidInTank(i, FluidStack.EMPTY);
            }
        }
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        int rowSize = (int) Math.sqrt(slots);
        int colSize = rowSize;
        if (slots == 8) {
            rowSize = 4;
            colSize = 2;
        }

        for (int i = 0; i < slots; i++) {
            int finalIndex = i;
            creativeTanks[i].setOnContentsChanged(() -> onCreativeFluidChanged(finalIndex, creativeTanks[finalIndex].getFluid()));
            syncManager.syncValue("creative_fluid_" + i,
                    new FluidSlotSyncHandler(creativeTanks[i]).phantom(true).controlsAmount(false));
        }

        Grid fluidGrid = new Grid()
                .coverChildren()
                .gridOfSizeWidth(slots, rowSize,
                        (x, y, i) -> new FluidSlot().syncHandler("creative_fluid_" + i));

        mainWidget.name("content")
                .coverChildren()
                .child(fluidGrid.horizontalCenter());
    }

    /**
     * Mirrors the old PhantomFluidWidget change listener: keeps phantom tank
     * contents (amount 1) in sync with the creativeFluidMap used by autoKeep().
     */
    private void onCreativeFluidChanged(int index, FluidStack fluid) {
        if (fluid.isEmpty()) {
            fluidMap.remove(index);
        } else {
            // deduplicate: reject a fluid already configured in another slot
            for (Map.Entry<Integer, FluidStack> e : fluidMap.entrySet()) {
                if (e.getKey() != index && e.getValue().getFluid() == fluid.getFluid()) {
                    return;
                }
            }
            setFluid(index, fluid);
        }
        updateTankSubscription();
    }

    private void setFluid(int index, FluidStack fs) {
        var newFluid = fs.copy();
        newFluid.setAmount(1);
        this.creativeTanks[index].setFluid(newFluid);
        fluidMap.put(index, newFluid);
    }

    @Override
    public boolean isDistinct() {
        return getHandlerList().isDistinct();
    }

    @Override
    public void setDistinct(boolean isDistinct) {
        getHandlerList().setDistinctAndNotify(isDistinct);
    }

    private static class InfinityFluidTank extends NotifiableFluidTank {

        public InfinityFluidTank(int slots, int capacity, IO io) {
            super(slots, capacity, io);
        }

        @Override
        public List<FluidIngredient> handleRecipeInner(IO io, @Nullable GTRecipe recipe, List<FluidIngredient> left,
                                                       boolean simulate) {
            return super.handleRecipeInner(io, recipe, left, true);
        }
    }
}
