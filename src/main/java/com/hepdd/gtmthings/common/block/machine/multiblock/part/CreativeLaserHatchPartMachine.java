package com.hepdd.gtmthings.common.block.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableLaserContainer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.ISubscription;

import net.minecraft.network.chat.Component;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import static net.minecraft.ChatFormatting.*;

@ParametersAreNonnullByDefault
public class CreativeLaserHatchPartMachine extends TieredIOPartMachine
                                           implements IDataInfoProvider, IMuiMachine {

    @SaveField
    private NotifiableLaserContainer buffer;

    @Nullable
    protected ISubscription laserListener;
    protected TickableSubscription explosionSubs;

    private long maxEnergy;

    @SaveField
    @SyncToClient
    private long voltage = 0;

    @SaveField
    @SyncToClient
    @Getter
    private int amps = 256;

    @SaveField
    @SyncToClient
    private int setTier = GTValues.IV;

    public static final String[] VNF = new String[] {
            BLUE + "IV", LIGHT_PURPLE + "LuV", RED + "ZPM", DARK_AQUA + "UV",
            DARK_RED + "UHV", GREEN + "UEV", DARK_GREEN + "UIV", YELLOW + "UXV",
            BLUE.toString() + BOLD + "OpV", RED.toString() + BOLD + "MAX" };

    public CreativeLaserHatchPartMachine(BlockEntityCreationInfo info) {
        super(info, GTValues.MAX, IO.IN);
        this.voltage = GTValues.VEX[setTier];
        this.maxEnergy = voltage * 64L * amps;
        this.buffer = attachTrait(
                NotifiableLaserContainer.receiverContainer(this.maxEnergy, voltage, amps));
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (laserListener != null) {
            laserListener.unsubscribe();
            laserListener = null;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        laserListener = buffer.addChangedListener(this::updateEnergySubscription);
        updateEnergySubscription();
    }

    protected void updateEnergySubscription() {
        explosionSubs = subscribeServerTick(explosionSubs, this::addEng);
    }

    protected void addEng() {
        if (buffer.getInputVoltage() != voltage || buffer.getInputAmperage() != amps) {
            maxEnergy = voltage * 64L * amps;
            buffer.resetBasicInfo(maxEnergy, voltage, amps, 0, 0);
            buffer.setEnergyStored(0);
        }
        if (buffer.getEnergyStored() < maxEnergy) {
            buffer.setEnergyStored(maxEnergy);
        }
    }

    @Override
    public boolean canShared(com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine controller, String substructureName) {
        return false;
    }

    // ── GUI ───────────────────────────────────────────────────────────────────

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        mainWidget.name("content").coverChildren()
                .child(Flow.col().coverChildren().margin(4, 4).childPadding(4)
                        .child(new TextWidget<>(Text.lang("gtceu.creative.energy.voltage")))
                        .child(new TextFieldWidget()
                                .setNumbersLong(v -> {
                                    voltage = v;
                                    setTier = GTUtil.getTierByVoltage(voltage);
                                    syncDataHolder.markClientSyncFieldDirty("voltage");
                                    syncDataHolder.markClientSyncFieldDirty("setTier");
                                    return v;
                                })
                                .value(SyncHandlers.longNumber(
                                        () -> voltage,
                                        v -> {
                                            voltage = v;
                                            syncDataHolder.markClientSyncFieldDirty("voltage");
                                        })))
                        .child(new TextWidget<>(Text.lang("gtceu.creative.energy.amperage")))
                        .child(new TextFieldWidget()
                                .setNumbers(() -> 256, () -> 67108864)
                                .value(SyncHandlers.intNumber(
                                        () -> amps,
                                        v -> {
                                            amps = v;
                                            syncDataHolder.markClientSyncFieldDirty("amps");
                                        }))));
    }

    // ── IDataInfoProvider ─────────────────────────────────────────────────────

    @NotNull
    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL || mode == PortableScannerBehavior.DisplayMode.SHOW_ELECTRICAL_INFO) {
            return Collections.singletonList(Component.literal(
                    String.format("%d/%d EU", buffer.getEnergyStored(), buffer.getEnergyCapacity())));
        }
        return new ArrayList<>();
    }
}
