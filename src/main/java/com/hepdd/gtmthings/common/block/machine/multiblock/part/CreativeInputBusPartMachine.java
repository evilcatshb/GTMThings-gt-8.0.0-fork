package com.hepdd.gtmthings.common.block.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.blockentity.IPaintable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDistinctPart;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.PhantomItemSlotSyncHandler;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.slot.ModularSlot;
import brachy.modularui.widgets.slot.PhantomItemSlot;
import com.hepdd.gtmthings.api.misc.UnlimitedItemStackTransfer;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CreativeInputBusPartMachine extends TieredIOPartMachine
                                         implements IDistinctPart, IMuiMachine, IPaintable {

    private final int ITEM_SIZE = 5;

    @Getter
    @SaveField
    private final NotifiableItemStackHandler inventory;
    @Nullable
    protected TickableSubscription autoIOSubs;
    @Getter
    @SaveField
    protected final NotifiableItemStackHandler circuitInventory;
    @SaveField
    protected CustomItemStackHandler creativeStorage;
    protected ArrayList<Item> lstItem;

    public CreativeInputBusPartMachine(BlockEntityCreationInfo info) {
        this(info, CustomItemStackHandler::new);
    }

    public CreativeInputBusPartMachine(BlockEntityCreationInfo info,
                                       IntFunction<CustomItemStackHandler> transferFactory) {
        super(info, GTValues.MAX, IO.IN);
        this.inventory = attachTrait(createInventory());
        this.circuitInventory = attachTrait(createCircuitItemHandler());
        this.creativeStorage = transferFactory.apply(this.getInventorySize());
        this.creativeStorage.setOnContentsChanged(this::syncCreativeItems);
        this.lstItem = new ArrayList<>();
    }

    protected int getInventorySize() {
        return ITEM_SIZE * ITEM_SIZE;
    }

    protected NotifiableItemStackHandler createInventory() {
        return new InfinityItemStackHandler(getInventorySize(), io, io, UnlimitedItemStackTransfer::new);
    }

    protected NotifiableItemStackHandler createCircuitItemHandler() {
        return new NotifiableItemStackHandler(1, IO.IN, IO.NONE)
                .setFilter(IntCircuitBehaviour::isIntegratedCircuit);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        for (int i = 0; i < this.getInventorySize(); i++) {
            ItemStack is = this.creativeStorage.getStackInSlot(i);
            if (!is.isEmpty()) {
                lstItem.add(is.getItem());
            }
        }
        getHandlerList().setColor(getPaintingColor());
        updateInventorySubscription();
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

    @Override
    public boolean isDistinct() {
        return getHandlerList().isDistinct();
    }

    @Override
    public void setDistinct(boolean isDistinct) {
        getHandlerList().setDistinctAndNotify(isDistinct);
    }

    protected void autoKeep() {
        if (getOffsetTimer() % 5 == 0) {
            for (int i = 0; i < this.getInventorySize(); i++) {
                ItemStack is = this.creativeStorage.getStackInSlot(i);
                if (!is.isEmpty()) {
                    var newItem = is.copy();
                    newItem.setCount(Integer.MAX_VALUE);
                    getInventory().storage.setStackInSlot(i, newItem);
                }
            }
            updateInventorySubscription();
        }
    }

    protected void updateInventorySubscription() {
        if (!lstItem.isEmpty()) {
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoKeep);
        } else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        int size = getInventorySize();
        int rowSize = ITEM_SIZE;
        int colSize = ITEM_SIZE;
        if (size == 8) {
            rowSize = 4;
            colSize = 2;
        }

        Grid slotGrid = new Grid()
                .coverChildren()
                .gridOfSizeWidth(size, rowSize, (x, y, i) -> new PhantomItemSlot()
                        .syncHandler(new PhantomItemSlotSyncHandler(
                                new ModularSlot(creativeStorage, i).ignoreMaxStackSize(true)
                                        .accessibility(true, false))));

        mainWidget.name("content")
                .coverChildren()
                .child(slotGrid.horizontalCenter());
    }

    /**
     * Mirrors the old PhantomSlotWidget click logic: keep the tracked item list
     * and the infinite inventory slot in sync with the phantom slot content.
     */
    private void syncCreativeItems() {
        lstItem.clear();
        for (int i = 0; i < this.getInventorySize(); i++) {
            ItemStack is = this.creativeStorage.getStackInSlot(i);
            if (!is.isEmpty()) {
                lstItem.add(is.getItem());
                var itemStack = is.copy();
                itemStack.setCount(Integer.MAX_VALUE);
                getInventory().storage.setStackInSlot(i, itemStack);
            } else {
                getInventory().storage.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        updateInventorySubscription();
    }

    private static class InfinityItemStackHandler extends NotifiableItemStackHandler {

        public InfinityItemStackHandler(int slots, @NotNull IO handlerIO, @NotNull IO capabilityIO,
                                        IntFunction<CustomItemStackHandler> storageFactory) {
            super(slots, handlerIO, capabilityIO, storageFactory);
        }

        @Override
        public List<Ingredient> handleRecipeInner(IO io, @Nullable GTRecipe recipe, List<Ingredient> left,
                                                  boolean simulate) {
            return super.handleRecipeInner(io, recipe, left, true);
        }
    }
}
