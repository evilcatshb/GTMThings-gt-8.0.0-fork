package com.hepdd.gtmthings.common.cover;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IMuiCover;
import com.gregtechceu.gtceu.api.cover.filter.Filter;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.machine.electric.PumpMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.storage.CrateMachine;
import com.gregtechceu.gtceu.common.machine.storage.DrumMachine;
import com.gregtechceu.gtceu.common.machine.storage.QuantumChestMachine;
import com.gregtechceu.gtceu.common.machine.storage.QuantumTankMachine;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import brachy.modularui.factory.SidedPosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.layout.Flow;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import javax.annotation.ParametersAreNonnullByDefault;

import static net.minecraft.resources.ResourceLocation.tryParse;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class AdvancedWirelessTransferCover extends CoverBehavior implements IMuiCover {

    public static final int TRANSFER_ITEM = 1;
    public static final int TRANSFER_FLUID = 2;

    @SaveField
    protected final int transferType;
    private TickableSubscription subscription;
    protected ServerLevel targetLevel;
    @SaveField
    private String dimensionId;
    @SaveField
    protected BlockPos targetPos;
    @SaveField
    protected Direction facing;

    @SaveField
    @Getter
    protected final FilterHandler<FluidStack> filterHandlerFluid;
    @SaveField
    @Getter
    protected final FilterHandler<ItemStack> filterHandlerItem;

    public AdvancedWirelessTransferCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide,
                                         int transferType) {
        super(definition, coverHolder, attachedSide);
        this.transferType = transferType;

        this.filterHandlerFluid = new FilterHandler<>(this, FluidStack.class)
                .onFilterLoaded(f -> configureFilter())
                .onFilterUpdated(f -> configureFilter())
                .onFilterRemoved(this::configureFilter);

        this.filterHandlerItem = new FilterHandler<>(this, ItemStack.class)
                .onFilterLoaded(f -> configureFilter())
                .onFilterUpdated(f -> configureFilter())
                .onFilterRemoved(this::configureFilter);
    }

    @Override
    public boolean canAttach() {
        var targetMachine = MetaMachine.getMachine(coverHolder.getLevel(), coverHolder.getBlockPos());
        if (targetMachine instanceof WorkableTieredMachine workableTieredMachine) {
            return (workableTieredMachine.exportItems.getSlots() > 0 && this.transferType == TRANSFER_ITEM) ||
                    (workableTieredMachine.exportFluids.getTanks() > 0 && this.transferType == TRANSFER_FLUID);
        } else if (targetMachine instanceof PumpMachine || targetMachine instanceof QuantumTankMachine ||
                targetMachine instanceof DrumMachine) {
                    return this.transferType == TRANSFER_FLUID;
                } else
            if (targetMachine instanceof QuantumChestMachine || targetMachine instanceof CrateMachine) {
                return this.transferType == TRANSFER_ITEM;
            } else {
                return (targetMachine instanceof ItemBusPartMachine itemBusPartMachine &&
                        itemBusPartMachine.getInventory().handlerIO != IO.IN && this.transferType == TRANSFER_ITEM) ||
                        (targetMachine instanceof FluidHatchPartMachine fluidHatchPartMachine &&
                                fluidHatchPartMachine.tank.handlerIO != IO.IN && this.transferType == TRANSFER_FLUID);
            }
    }

    @Override
    public void onAttached(ItemStack itemStack, ServerPlayer player) {
        CompoundTag tag = itemStack.getTag();
        if (tag != null) {
            this.dimensionId = tag.getString("dimensionid");
            var intX = tag.getInt("x");
            var intY = tag.getInt("y");
            var intZ = tag.getInt("z");
            this.targetPos = new BlockPos(intX, intY, intZ);
            this.facing = Direction.byName(tag.getString("facing"));
            resolveLevel();
        }
        var targetMachine = MetaMachine.getMachine(coverHolder.getLevel(), coverHolder.getBlockPos());
        if (targetMachine instanceof SimpleTieredMachine simpleTieredMachine) {
            var auto = simpleTieredMachine.getTrait(com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait.class);
            if (auto != null) {
                if (this.transferType == TRANSFER_ITEM) auto.setAllowAutoOutputItems(false);
                if (this.transferType == TRANSFER_FLUID) auto.setAllowAutoOutputFluids(false);
            }
        } else if (targetMachine instanceof ItemBusPartMachine ibm && this.transferType == TRANSFER_ITEM) {
            ibm.setWorkingEnabled(false);
        } else if (targetMachine instanceof FluidHatchPartMachine fhm && this.transferType == TRANSFER_FLUID) {
            fhm.setWorkingEnabled(false);
        }
        super.onAttached(itemStack, player);
    }

    @Override
    public List<ItemStack> getAdditionalDrops() {
        var list = super.getAdditionalDrops();
        if (!filterHandlerFluid.getFilterItem().isEmpty()) {
            list.add(filterHandlerFluid.getFilterItem());
        }
        if (!filterHandlerItem.getFilterItem().isEmpty()) {
            list.add(filterHandlerItem.getFilterItem());
        }
        return list;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        resolveLevel();
        subscription = coverHolder.subscribeServerTick(subscription, this::update);
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        if (subscription != null) {
            subscription.unsubscribe();
        }
    }

    private void update() {
        long timer = coverHolder.getOffsetTimer();
        if (timer % 5 == 0) {
            if (transferType == TRANSFER_ITEM) {
                var adjacentItemTransfer = getAdjacentItemTransfer();
                var myItemHandler = getOwnItemTransfer();
                if (adjacentItemTransfer != null && myItemHandler != null) {
                    moveInventoryItems(myItemHandler, adjacentItemTransfer);
                }
            } else if (transferType == TRANSFER_FLUID) {
                var adjacentFluidTransfer = getAdjacentFluidTransfer();
                var ownFluidTransfer = getOwnFluidTransfer();
                if (ownFluidTransfer != null && adjacentFluidTransfer != null) {
                    transferAny(ownFluidTransfer, adjacentFluidTransfer);
                }
            }
        }
    }

    protected void moveInventoryItems(IItemHandler sourceInventory, IItemHandler targetInventory) {
        Filter<ItemStack> filter = filterHandlerItem.getFilter();
        int itemsLeftToTransfer = Integer.MAX_VALUE;

        for (int srcIndex = 0; srcIndex < sourceInventory.getSlots(); srcIndex++) {
            ItemStack sourceStack = sourceInventory.extractItem(srcIndex, itemsLeftToTransfer, true);
            if (sourceStack.isEmpty()) continue;
            if (!filter.test(sourceStack)) continue;

            ItemStack remainder = ItemHandlerHelper.insertItemStacked(targetInventory, sourceStack, true);
            int amountToInsert = sourceStack.getCount() - remainder.getCount();

            if (amountToInsert > 0) {
                sourceStack = sourceInventory.extractItem(srcIndex, amountToInsert, false);
                if (!sourceStack.isEmpty()) {
                    ItemHandlerHelper.insertItemStacked(targetInventory, sourceStack, false);
                    itemsLeftToTransfer -= sourceStack.getCount();
                    if (itemsLeftToTransfer == 0) break;
                }
            }
        }
    }

    protected void transferAny(IFluidHandler source, IFluidHandler destination) {
        GTTransferUtils.transferFluidsFiltered(source, destination,
                filterHandlerFluid.getFilter(), Integer.MAX_VALUE);
    }

    protected void resolveLevel() {
        if (this.dimensionId == null) return;
        ResourceLocation resLoc = tryParse(this.dimensionId);
        ResourceKey<Level> resKey = ResourceKey.create(Registries.DIMENSION, resLoc);
        this.targetLevel = Objects.requireNonNull(coverHolder.getLevel().getServer()).getLevel(resKey);
    }

    protected void configureFilter() {
        // Do nothing in the base implementation.
    }

    protected @Nullable IItemHandler getOwnItemTransfer() {
        return coverHolder.getItemHandlerCap(attachedSide, false);
    }

    protected @Nullable IItemHandler getAdjacentItemTransfer() {
        if (targetLevel == null || targetPos == null) return null;
        return GTTransferUtils.getItemHandler(targetLevel, targetPos, facing.getOpposite()).resolve().orElse(null);
    }

    protected @Nullable IFluidHandler getOwnFluidTransfer() {
        return coverHolder.getFluidHandlerCap(attachedSide, false);
    }

    protected @Nullable IFluidHandler getAdjacentFluidTransfer() {
        if (targetLevel == null || targetPos == null) return null;
        return FluidUtil.getFluidHandler(targetLevel, targetPos, facing.getOpposite()).resolve().orElse(null);
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////

    @Override
    public void createCoverUIRows(Flow column, SidedPosGuiData data, PanelSyncManager syncManager,
                                  UISettings settings) {
        String titleKey = transferType == TRANSFER_ITEM ?
                "item.gtmthings.advanced_wireless_item_transfer_cover" :
                "item.gtmthings.advanced_wireless_fluid_transfer_cover";
        column.child(new TextWidget<>(titleKey));
        if (transferType == TRANSFER_ITEM) {
            Filter<ItemStack> filter = getItemFilter();
            if (filter != null) {
                column.child(filter.getFilterUI(data, syncManager, settings));
            }
        } else {
            Filter<FluidStack> filter = getFluidFilter();
            if (filter != null) {
                column.child(filter.getFilterUI(data, syncManager, settings));
            }
        }
    }

    public @Nullable Filter<ItemStack> getItemFilter() {
        return filterHandlerItem.getFilter();
    }

    public @Nullable Filter<FluidStack> getFluidFilter() {
        return filterHandlerFluid.getFilter();
    }
}
