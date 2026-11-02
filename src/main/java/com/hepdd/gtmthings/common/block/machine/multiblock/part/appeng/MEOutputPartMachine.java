package com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DualHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.layout.Flow;
import com.hepdd.gtmthings.api.machine.trait.InaccessibleInfiniteHandler;
import com.hepdd.gtmthings.api.machine.trait.InaccessibleInfiniteTank;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Getter;
import lombok.Setter;

import java.util.EnumSet;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MEOutputPartMachine extends DualHatchPartMachine implements IGridConnectedMachine {

    @SyncToClient
    @Getter
    @Setter
    protected boolean isOnline;

    @SaveField
    protected final GridNodeHolder nodeHolder;
    protected final IActionSource actionSource;

    @Getter
    protected Object2LongOpenHashMap<AEKey> returnBuffer = new Object2LongOpenHashMap<>();

    @SaveField
    private KeyStorage internalBuffer;
    @SaveField
    private KeyStorage internalTankBuffer;

    public MEOutputPartMachine(BlockEntityCreationInfo info) {
        super(info, GTValues.LuV, IO.OUT);
        this.nodeHolder = attachTrait(new GridNodeHolder(this));
        this.actionSource = IActionSource.ofMachine(nodeHolder.getMainNode()::getNode);
        this.internalBuffer = new KeyStorage();
        this.internalTankBuffer = new KeyStorage();
        attachTrait(new InaccessibleInfiniteHandler(internalBuffer));
        attachTrait(new InaccessibleInfiniteTank(internalTankBuffer));
    }

    @Override
    public IManagedGridNode getMainNode() {
        return nodeHolder.getMainNode();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        IGridConnectedMachine.super.onMainNodeStateChanged(reason);
        this.updateInventorySubscription();
    }

    @Override
    protected void updateInventorySubscription() {
        if (shouldSubscribe()) {
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoIO);
        } else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (isRemote()) return;
    }

    @Override
    public void onMachineDestroyed() {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            if (!internalBuffer.isEmpty()) {
                for (var entry : internalBuffer) {
                    grid.getStorageService().getInventory().insert(entry.getKey(), entry.getLongValue(),
                            Actionable.MODULATE, actionSource);
                }
            }
            if (!internalTankBuffer.isEmpty()) {
                for (var entry : internalTankBuffer) {
                    grid.getStorageService().getInventory().insert(entry.getKey(), entry.getLongValue(),
                            Actionable.MODULATE, actionSource);
                }
            }
        }
    }

    protected boolean shouldSubscribe() {
        return isWorkingEnabled() && isOnline() &&
                (!internalBuffer.storage.isEmpty() || !internalTankBuffer.storage.isEmpty());
    }

    @Override
    protected void autoIO() {
        if (!this.shouldSyncME()) return;
        if (this.updateMEStatus()) {
            var grid = getMainNode().getGrid();
            if (grid != null) {
                if (!internalBuffer.isEmpty()) {
                    internalBuffer.insertInventory(grid.getStorageService().getInventory(), actionSource);
                }
                if (!internalTankBuffer.isEmpty()) {
                    internalTankBuffer.insertInventory(grid.getStorageService().getInventory(), actionSource);
                }
            }
            this.updateInventorySubscription();
        }
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        getMainNode().setExposedOnSides(EnumSet.of(newFacing));
    }

    @Override
    public boolean isWorkingEnabled() {
        return true;
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        mainWidget.child(Flow.col()
                .coverChildren()
                .margin(4, 4)
                .childPadding(2)
                .child(new TextWidget<>(Text.dynamic(() -> isOnline ? Component.translatable("gtceu.gui.me_network.online") : Component.translatable("gtceu.gui.me_network.offline")))));
    }
}
