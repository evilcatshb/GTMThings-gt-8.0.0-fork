package com.hepdd.gtmthings.common.block.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.ISubscription;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import com.hepdd.gtmthings.common.block.machine.trait.CatalystFluidStackHandler;
import com.hepdd.gtmthings.utils.FormatUtil;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class HugeDualHatchPartMachine extends HugeBusPartMachine {

    @Getter
    @SaveField
    protected final NotifiableFluidTank tank;

    @Getter
    @SaveField
    protected final CatalystFluidStackHandler shareTank;

    @Nullable
    protected ISubscription tankSubs;

    private boolean hasFluidTransfer;
    private boolean hasItemTransfer;

    public HugeDualHatchPartMachine(BlockEntityCreationInfo info, int tier, IO io, Object... args) {
        super(info, tier, io, 9, args);
        this.tank = attachTrait(createTank());
        this.shareTank = attachTrait(new CatalystFluidStackHandler(9, 16000, IO.IN, IO.NONE));
    }

    protected NotifiableFluidTank createTank(Object... args) {
        return new NotifiableFluidTank(getTankInventorySize(), Integer.MAX_VALUE, io) {

            @Override
            public boolean canCapOutput() {
                return true;
            }
        };
    }

    protected int getTankInventorySize() {
        return getTier() + 1;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        tankSubs = tank.addChangedListener(this::updateInventorySubscription);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (tankSubs != null) {
            tankSubs.unsubscribe();
            tankSubs = null;
        }
    }

    @Override
    protected void updateInventorySubscription() {
        boolean canOutput = io == IO.OUT && (!tank.isEmpty() || !getInventory().isEmpty());
        Level level = getLevel();
        if (level != null) {
            hasItemTransfer = GTTransferUtils.hasAdjacentItemHandler(level, getBlockPos(), getFrontFacing());
            hasFluidTransfer = GTTransferUtils.hasAdjacentFluidHandler(level, getBlockPos(), getFrontFacing());
        } else {
            hasItemTransfer = false;
            hasFluidTransfer = false;
        }
        if (!isWorkingEnabled() || (!canOutput && io != IO.IN) || (!hasItemTransfer && !hasFluidTransfer)) {
            if (autoIOSubs != null) {
                autoIOSubs.unsubscribe();
                autoIOSubs = null;
            }
        } else {
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoIO);
        }
    }

    @Override
    protected void autoIO() {
        if (getOffsetTimer() % 5 == 0) {
            if (isWorkingEnabled()) {
                if (io == IO.OUT) {
                    if (hasItemTransfer) getInventory().exportToNearby(getFrontFacing());
                    if (hasFluidTransfer) tank.exportToNearby(getFrontFacing());
                } else if (io == IO.IN) {
                    if (hasItemTransfer) getInventory().importFromNearby(getFrontFacing());
                    if (hasFluidTransfer) tank.importFromNearby(getFrontFacing());
                }
            }
        }
    }

    @Override
    protected String buildDisplayString() {
        int itemCount = 0, tankCount = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < super.getInventorySize(); i++) {
            var is = getInventory().getStackInSlot(i);
            if (!is.isEmpty()) {
                sb.append(is.getDisplayName().getString())
                        .append(" x").append(FormatUtil.formatNumber(is.getCount())).append("\n");
                itemCount++;
            }
        }
        for (int i = 0; i < getTankInventorySize(); i++) {
            FluidStack fs = tank.getFluidInTank(i);
            if (!fs.isEmpty()) {
                sb.append(fs.getDisplayName().getString()).append(" ")
                        .append(fs.getAmount() < 1000 ? fs.getAmount() + "mB" : FormatUtil.formatNumber(fs.getAmount() / 1000L) + "B").append("\n");
                tankCount++;
            }
        }
        return "[Items " + itemCount + "/" + super.getInventorySize() + "] " + "[Fluids " + tankCount + "/" + getTankInventorySize() + "]\n" + sb;
    }
}
