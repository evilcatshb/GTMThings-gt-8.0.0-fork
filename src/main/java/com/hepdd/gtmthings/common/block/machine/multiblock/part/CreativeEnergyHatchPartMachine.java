package com.hepdd.gtmthings.common.block.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanel;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.utils.ISubscription;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.utils.Alignment;
import brachy.modularui.value.StringValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.menu.DropdownWidget;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CreativeEnergyHatchPartMachine extends TieredIOPartMachine
                                            implements IDataInfoProvider, IMuiMachine {

    private static final int MIN_AMPS = 1;
    private static final int MAX_AMPS = 67108864;
    private static final String[] ENERGY_TIER_NAMES = {
            "ULV", "LV", "MV", "HV", "EV", "IV", "LUV", "ZPM", "UV", "UHV",
            "UEV", "UIV", "UXV", "OPV", "MAX"
    };
    private static final int[] ENERGY_TIER_VALUES = {
            GTValues.ULV, GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV, GTValues.IV,
            GTValues.LuV, GTValues.ZPM, GTValues.UV, GTValues.UHV, GTValues.UEV, GTValues.UIV,
            GTValues.UXV, GTValues.OpV, GTValues.MAX
    };

    @SaveField
    public final NotifiableEnergyContainer energyContainer;
    private TickableSubscription energySubs;
    private ISubscription energyListener;

    @SaveField
    @SyncToClient
    private long maxEnergy;
    @SaveField
    @SyncToClient
    private long voltage = 0;
    @SaveField
    @SyncToClient
    @Getter
    private int amps = 1;
    @SaveField
    @SyncToClient
    private int setTier = GTValues.ULV;

    public CreativeEnergyHatchPartMachine(BlockEntityCreationInfo info) {
        super(info, GTValues.MAX, IO.IN);
        this.voltage = GTValues.VEX[setTier];
        this.maxEnergy = calculateCapacity(this.voltage, this.amps);
        this.energyContainer = attachTrait(createEnergyContainer());
    }

    protected NotifiableEnergyContainer createEnergyContainer() {
        return new InfinityEnergyContainer(this.maxEnergy, this.voltage, this.amps, 0L, 0L);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isValidTier(this.setTier)) this.setTier = GTValues.ULV;
        if (this.voltage <= 0) this.voltage = GTValues.VEX[this.setTier];
        if (this.amps < MIN_AMPS || this.amps > MAX_AMPS) this.amps = MIN_AMPS;
        // Never allow malformed legacy/persisted values to overflow the long capacity and crash world loading.
        if (this.voltage > Long.MAX_VALUE / this.amps) {
            this.voltage = Long.MAX_VALUE / this.amps;
        }
        this.maxEnergy = calculateCapacity(this.voltage, this.amps);
        updateEnergyContainer();

        // Re-establish the creative/infinite-energy tick subscription after world load.
        // MetaMachine.onLoad() may run before the complete world is available, so defer
        // multiblock capability reconstruction until the first full server tick.
        this.energyListener = this.energyContainer.addChangedListener(this::infinityEnergySubscription);
        infinityEnergySubscription();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (this.energyListener != null) {
            this.energyListener.unsubscribe();
            this.energyListener = null;
        }
        this.energySubs = null;
    }

    private void infinityEnergySubscription() {
        this.energySubs = subscribeServerTick(this.energySubs, this::addEnergy);
    }

    private void addEnergy() {
        if (this.energyContainer.getEnergyStored() < this.maxEnergy) {
            this.energyContainer.setEnergyStored(this.maxEnergy);
        }
    }

    @Override
    public void addedToController(MultiblockControllerMachine controller, String substructureName) {
        super.addedToController(controller, substructureName);
    }

    @Override
    public void removedFromController(MultiblockControllerMachine controller) {
        super.removedFromController(controller);
    }

    @Override
    public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return MachineUIPanelBuilder.panelBuilder(this).attachInventory(false);
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        final StringValue pendingVoltage = new StringValue(Long.toString(this.voltage));
        final StringValue pendingAmps = new StringValue(Integer.toString(this.amps));
        final StringValue pendingTier = new StringValue(tierName(this.setTier));

        syncManager.registerSyncedAction("applyCreativeEnergyConfiguration", false, true, buf -> {
            if (getLevel() == null || getLevel().isClientSide) return;
            if (!(syncManager.getPlayer() instanceof ServerPlayer player)) return;
            if (player.containerMenu != syncManager.getContainer()) return;

            final long requestedVoltage = buf.readLong();
            final int requestedAmps = buf.readVarInt();
            final int requestedTier = buf.readVarInt();

            if (!isValidVoltage(requestedVoltage) || !isValidAmps(requestedAmps) || !isValidTier(requestedTier)) {
                return;
            }

            final long requestedCapacity;
            try {
                requestedCapacity = Math.multiplyExact(requestedVoltage, requestedAmps);
            } catch (ArithmeticException ignored) {
                return;
            }

            com.gregtechceu.gtceu.GTCEu.LOGGER.info("[GTMThings PowerSubstationDiag] CREATIVE.Apply accepted hatchPos={} voltage={} amps={} tier={} controllers={}", getBlockPos(), requestedVoltage, requestedAmps, requestedTier, getControllers().size());
            this.voltage = requestedVoltage;
            this.amps = requestedAmps;
            this.setTier = requestedTier;
            this.maxEnergy = requestedCapacity;
            updateEnergyContainer();
            markAsChanged();
            syncDataHolder.markClientSyncFieldDirty("voltage");
            syncDataHolder.markClientSyncFieldDirty("amps");
            syncDataHolder.markClientSyncFieldDirty("setTier");
            syncDataHolder.markClientSyncFieldDirty("maxEnergy");
            updateMachine();
        });

        final TierDropdown tierDropdown = new TierDropdown(pendingTier, pendingVoltage);
        tierDropdown.options(List.of(ENERGY_TIER_NAMES));

        final TextWidget<?> outputPreview = Text.dynamic(() -> {
            try {
                long v = Long.parseLong(pendingVoltage.getStringValue());
                int a = Integer.parseInt(pendingAmps.getStringValue());
                if (!isValidVoltage(v) || !isValidAmps(a) || Math.multiplyExact(v, a) < 0) {
                    return Component.literal("Invalid configuration");
                }
                return Component.literal(v + " V × " + a + " A = " + Math.multiplyExact(v, a) + " EU/t");
            } catch (NumberFormatException | ArithmeticException ignored) {
                return Component.literal("Invalid configuration");
            }
        }).asWidget();

        ButtonWidget<?> applyButton = new ButtonWidget<>()
                .overlay(Text.str("Apply"))
                .size(60, 20)
                .onMousePressed((context, button) -> {
                    try {
                        long requestedVoltage = Long.parseLong(pendingVoltage.getStringValue());
                        int requestedAmps = Integer.parseInt(pendingAmps.getStringValue());
                        int requestedTier = tierIndex(pendingTier.getStringValue());
                        if (!isValidVoltage(requestedVoltage) || !isValidAmps(requestedAmps) || !isValidTier(requestedTier)) {
                            return true;
                        }
                        Math.multiplyExact(requestedVoltage, requestedAmps);
                        syncManager.callSyncedAction("applyCreativeEnergyConfiguration", buf -> {
                            buf.writeLong(requestedVoltage);
                            buf.writeVarInt(requestedAmps);

                            buf.writeVarInt(requestedTier);
                        });
                    } catch (NumberFormatException | ArithmeticException ignored) {
                        // Keep the previous committed machine state. The preview exposes the invalid pending input.
                    }
                    return true;
                });

        mainWidget.child(Flow.col()
                .size(MachineUIPanel.DEFAULT_CONTENT_WIDTH, 132)
                .name("creative_energy_hatch")
                .padding(7)
                .child(Flow.row().height(18).child(new TextWidget<>("Voltage").width(62).verticalCenter())
                        .child(new TextFieldWidget()
                                .setTextAlignment(Alignment.CENTER)
                                .setNumbersLong(() -> 1L, () -> Long.MAX_VALUE)
                                .value(pendingVoltage)
                                .width(90)))
                .child(Flow.row().height(18).marginTop(4)
                        .child(new TextWidget<>("Amperage").width(62).verticalCenter())
                        .child(new TextFieldWidget()
                                .setTextAlignment(Alignment.CENTER)
                                .setNumbers(MIN_AMPS, MAX_AMPS)
                                .value(pendingAmps)
                                .width(90)))
                .child(Flow.row().height(18).marginTop(4)
                        .child(new TextWidget<>("Energy Tier").width(62).verticalCenter())
                        .child(tierDropdown.size(90, 18)))
                .child(Flow.col().coverChildren().marginTop(6)
                        .child(new TextWidget<>("Output").width(62))
                        .child(outputPreview))
                .child(Flow.row().coverChildren().marginTop(6).child(applyButton)));
    }

    private final class TierDropdown extends DropdownWidget<String, TierDropdown> {

        private final StringValue pendingTier;
        private final StringValue pendingVoltage;

        private TierDropdown(StringValue pendingTier, StringValue pendingVoltage) {
            super("creative_energy_tier", String.class);
            this.pendingTier = pendingTier;
            this.pendingVoltage = pendingVoltage;
            this.value(pendingTier);
            this.maxVerticalMenuSize(90);
            this.directionDown();
        }

        @Override
        protected TextWidget<?> valueToWidget(String value, boolean fromUser) {
            return new TextWidget<>(value).alignment(Alignment.Center);
        }

        @Override
        protected void setValue(String value, boolean fromUser) {
            super.setValue(value, fromUser);
            if (fromUser) {
                int tier = tierIndex(value);
                if (isValidTier(tier)) {
                    pendingVoltage.setStringValue(Long.toString(GTValues.VEX[tier]));
                }
            }
        }
    }

    private static boolean isValidTier(int tier) {
        return tier >= 0 && tier < ENERGY_TIER_VALUES.length && ENERGY_TIER_VALUES[tier] == tier;
    }

    private static boolean isValidVoltage(long value) {
        return value > 0;
    }

    private static boolean isValidAmps(int value) {
        return value >= MIN_AMPS && value <= MAX_AMPS;
    }

    private static int tierIndex(String name) {
        for (int i = 0; i < ENERGY_TIER_NAMES.length; i++) {
            if (ENERGY_TIER_NAMES[i].equals(name)) return i;
        }
        return -1;
    }

    private static String tierName(int tier) {
        return isValidTier(tier) ? ENERGY_TIER_NAMES[tier] : ENERGY_TIER_NAMES[GTValues.ULV];
    }

    private static long calculateCapacity(long voltage, int amps) {
        try {
            return Math.multiplyExact(voltage, amps);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Creative Energy Hatch voltage/amperage exceeds long capacity", e);
        }
    }

    private void updateEnergyContainer() {
        this.energyContainer.resetBasicInfo(this.maxEnergy, this.voltage, this.amps, 0L, 0L);
        this.energyContainer.setEnergyStored(this.maxEnergy);
    }

    private void updateMachine() {
        updateEnergyContainer();
    }

    @Override
    public int tintColor(int index) {
        if (index == 2) return GTValues.VC[getTier()];
        return super.tintColor(index);
    }

    @NotNull
    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL ||
                mode == PortableScannerBehavior.DisplayMode.SHOW_ELECTRICAL_INFO) {
            return Collections.singletonList(Component.literal(
                    String.format("%d/%d EU", energyContainer.getEnergyStored(), energyContainer.getEnergyCapacity())));
        }
        return new ArrayList<>();
    }

    private static class InfinityEnergyContainer extends NotifiableEnergyContainer {

        public InfinityEnergyContainer(long maxCapacity, long maxInputVoltage, long maxInputAmperage,
                                       long maxOutputVoltage, long maxOutputAmperage) {
            super(maxCapacity, maxInputVoltage, maxInputAmperage, maxOutputVoltage, maxOutputAmperage);
        }

        @Override
        public List<EnergyStack> handleRecipeInner(IO io, GTRecipe recipe, List<EnergyStack> left,
                                                   boolean simulate) {
            return super.handleRecipeInner(io, recipe, left, true);
        }

        @Override
        public long changeEnergy(long energyToAdd) {
            long oldEnergyStored = getEnergyStored();
            long newEnergyStored = (getEnergyCapacity() - oldEnergyStored < energyToAdd) ? getEnergyCapacity() : (oldEnergyStored + energyToAdd);
            if (newEnergyStored < 0) newEnergyStored = 0;
            return newEnergyStored - oldEnergyStored;
        }

        @Override
        public void checkOutputSubscription() {}

        @Override
        public void serverTick() {}

        @Override
        public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
            return 0;
        }

        @Override
        public boolean outputsEnergy(Direction side) {
            return false;
        }

        @Override
        public boolean inputsEnergy(Direction side) {
            return false;
        }
    }
}
