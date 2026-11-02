package com.hepdd.gtmthings.common.cover;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
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

import java.util.Objects;

import javax.annotation.ParametersAreNonnullByDefault;

import static net.minecraft.resources.ResourceLocation.tryParse;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class WirelessTransferCover extends CoverBehavior {

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

    public WirelessTransferCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide,
                                 int transferType) {
        super(definition, coverHolder, attachedSide);
        this.transferType = transferType;
    }

    @Override
    public boolean canAttach() {
        var targetMachine = MetaMachine.getMachine(coverHolder.getLevel(), coverHolder.getBlockPos());
        if (targetMachine instanceof WorkableTieredMachine workableTieredMachine) {
            if ((workableTieredMachine.exportItems.getSlots() > 0 && this.transferType == TRANSFER_ITEM) ||
                    (workableTieredMachine.exportFluids.getTanks() > 0 && this.transferType == TRANSFER_FLUID)) {
                for (var cover : targetMachine.getCoverContainer().getCovers()) {
                    if (cover instanceof WirelessTransferCover wtc && wtc.transferType == this.transferType)
                        return false;
                }
                return true;
            }
            return false;
        } else if (targetMachine instanceof PumpMachine || targetMachine instanceof QuantumTankMachine ||
                targetMachine instanceof DrumMachine) {
                    if (this.transferType == TRANSFER_FLUID) {
                        for (var cover : targetMachine.getCoverContainer().getCovers()) {
                            if (cover instanceof WirelessTransferCover) return false;
                        }
                        return true;
                    }
                    return false;
                } else
            if (targetMachine instanceof QuantumChestMachine || targetMachine instanceof CrateMachine) {
                if (this.transferType == TRANSFER_ITEM) {
                    for (var cover : targetMachine.getCoverContainer().getCovers()) {
                        if (cover instanceof WirelessTransferCover) return false;
                    }
                    return true;
                }
                return false;
            } else if ((targetMachine instanceof ItemBusPartMachine ibm &&
                    ibm.getInventory().handlerIO != IO.IN && this.transferType == TRANSFER_ITEM) ||
                    (targetMachine instanceof FluidHatchPartMachine fhm &&
                            fhm.tank.handlerIO != IO.IN && this.transferType == TRANSFER_FLUID)) {
                                for (var cover : targetMachine.getCoverContainer().getCovers()) {
                                    if (cover instanceof WirelessTransferCover wtc && wtc.transferType == this.transferType)
                                        return false;
                                }
                                return true;
                            }
        return false;
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

    @Override
    public void onAttached(ItemStack itemStack, ServerPlayer player) {
        CompoundTag tag = itemStack.getTag();
        if (tag != null) {
            this.dimensionId = tag.getString("dimensionid");
            this.targetPos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
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

    protected void resolveLevel() {
        if (this.dimensionId == null) return;
        ResourceLocation resLoc = tryParse(this.dimensionId);
        ResourceKey<Level> resKey = ResourceKey.create(Registries.DIMENSION, resLoc);
        this.targetLevel = Objects.requireNonNull(coverHolder.getLevel().getServer()).getLevel(resKey);
    }

    private void update() {
        if (coverHolder.getOffsetTimer() % 5 != 0) return;
        if (this.targetLevel == null || this.targetPos == null || coverHolder.getLevel().isClientSide()) return;

        if (this.transferType == TRANSFER_ITEM) {
            var src = GTCapabilityHelper.getItemHandler(coverHolder.getLevel(), coverHolder.getBlockPos(), attachedSide);
            var dst = GTCapabilityHelper.getItemHandler(this.targetLevel, this.targetPos, this.facing);
            if (src != null && dst != null) {
                GTTransferUtils.transferItemsFiltered(src, dst, s -> true);
            }
        } else {
            var src = GTCapabilityHelper.getFluidHandler(coverHolder.getLevel(), coverHolder.getBlockPos(), attachedSide);
            var dst = GTCapabilityHelper.getFluidHandler(this.targetLevel, this.targetPos, this.facing);
            if (src != null && dst != null) {
                GTTransferUtils.transferFluidsFiltered(src, dst, s -> true);
            }
        }
    }
}
