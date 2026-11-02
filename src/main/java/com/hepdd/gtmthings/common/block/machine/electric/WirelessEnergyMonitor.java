package com.hepdd.gtmthings.common.block.machine.electric;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.StringSyncValue;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.layout.Flow;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import com.hepdd.gtmthings.common.item.IWirelessMonitor;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.hepdd.gtmthings.utils.TeamUtil.GetName;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class WirelessEnergyMonitor extends MetaMachine implements IMuiMachine, IWirelessMonitor {

    /**
     * Content width - increased to accommodate display text without wrapping
     */
    public static final int CONTENT_WIDTH = 440;
    /**
     * Content height for the scrollable stats area
     */
    public static final int CONTENT_HEIGHT = 240;

    public static int DISPLAY_TEXT_WIDTH = 420;

    /** Width used by the scrollable statistics text so long rows never wrap. */
    public static final int STATISTICS_TEXT_WIDTH = CONTENT_WIDTH - 24;

    /** Total GUI content height including summary and statistics viewport. */
    public static final int PANEL_HEIGHT = 400;

    /** Countdown ticks for client-side block highlight (set by binding tool). */
    public static int p;
    /** Block position for client-side highlight overlay. */
    public static BlockPos pPos;

    // ──────────────────────────────────────────────────────────────────────────
    // Synced display text — computed server-side every 20 t and pushed to client
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Pre-built display Component assembled on the server each second, then pushed
     * to the client via GTCEu's sync system. The client-side GUI widget reads this
     * field directly — no server-only data access needed client-side.
     */
    // ──────────────────────────────────────────────────────────────────────────
    // State
    // ──────────────────────────────────────────────────────────────────────────

    @Getter
    @Setter
    private WirelessEnergyContainer WirelessEnergyContainerCache;

    @SaveField
    @Nullable
    private UUID wirelessTeamUUID;

    @SaveField
    private boolean all;

    // ──────────────────────────────────────────────────────────────────────────
    // Construction
    // ──────────────────────────────────────────────────────────────────────────

    public WirelessEnergyMonitor(BlockEntityCreationInfo info) {
        super(info);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote() && wirelessTeamUUID == null && getOwnerUUID() != null) {
            wirelessTeamUUID = com.hepdd.gtmthings.utils.TeamUtil.getWirelessNetworkUUID(getOwnerUUID());
            markAsChanged();
        }
    }

    // Binding / unbinding with the data stick
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Right-click with a {@code gtceu:data_stick} to bind this monitor to your
     * wireless energy network. Left-click to unbind.
     */
    @Override
    public InteractionResult onUse(ExtendedUseOnContext context) {
        var superResult = super.onUse(context);
        if (superResult != InteractionResult.PASS) return superResult;
        if (isRemote()) return InteractionResult.PASS;

        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(context.getHand());
        if (held.isEmpty()) return InteractionResult.PASS;

        if (held.is(GTItems.TOOL_DATA_STICK.get())) {
            setOwnerUUID(player.getUUID());
            wirelessTeamUUID = com.hepdd.gtmthings.utils.TeamUtil.getWirelessNetworkUUID(player.getUUID());
            markAsChanged();
            // Invalidate cache so the next server tick re-fetches the container
            setWirelessEnergyContainerCache(null);
            player.sendSystemMessage(Component.translatable(
                    "gtmthings.machine.wireless_energy_monitor.tooltip.bind", GetName(player)));
            return InteractionResult.sidedSuccess(false);
        }

        return InteractionResult.PASS;
    }

    @Override
    public boolean onLeftClick(Player player, InteractionHand hand, @Nullable Direction face) {
        if (isRemote()) return false;
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return false;

        if (held.is(GTItems.TOOL_DATA_STICK.get())) {
            setOwnerUUID(null);
            wirelessTeamUUID = null;
            markAsChanged();
            setWirelessEnergyContainerCache(null);
            player.sendSystemMessage(Component.translatable(
                    "gtmthings.machine.wireless_energy_monitor.tooltip.unbind"));
            return true;
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GUI panel configuration
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Remove the player inventory from the panel (display-only machine — no item
     * slots needed) and turn off the default machine configurators (no circuit slot,
     * no power button, no trait configurator side-panels).
     */
    @Override
    public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager syncManager,
                                                 UISettings settings) {
        return MachineUIPanelBuilder.panelBuilder(self())
                .attachInventory(false)
                .addDefaultConfigurators(false)
                .addTraitConfigurators(false);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GUI content — reads the server-synced displayText field; safe on client
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        BooleanSyncValue allSync = new BooleanSyncValue(() -> all, v -> all = v).allowC2S();
        syncManager.syncValue("all", allSync);

        // The server is the sole producer of the display payload. This block GUI is
        // a read-only consumer, just like Wireless Energy Terminal. StringSyncValue
        // sends the initial value immediately when the panel opens and then refreshes
        // it while the panel remains open. No client-side TRANSFER_DATA access occurs.
        String[] cachedSummaryJson = { "" };
        String[] cachedStatisticsJson = { "" };

        syncManager.syncValue("wem_summary", new StringSyncValue(
                () -> {
                    // The StringSyncValue supplier can execute while the client is
                    // constructing the panel. WirelessEnergySavaedData is server/world
                    // state and must never be touched from that client path.
                    if (getLevel().isClientSide()) return "";
                    return buildJson(getUUID() == null ? List.of(Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.not_bound")) : getDisplayText(all, DISPLAY_TEXT_WIDTH, false));
                },
                json -> cachedSummaryJson[0] = json));

        syncManager.syncValue("wem_statistics", new StringSyncValue(
                () -> {
                    // Statistics are generated once by the logical server and then
                    // synchronized to this GUI. Never read TRANSFER_DATA or saved
                    // wireless state from the client.
                    if (getLevel().isClientSide()) return "";
                    return buildJson(getUUID() == null ? List.of() : getTransferStatistics(all));
                },
                json -> cachedStatisticsJson[0] = json));

        var summaryWidget = Text.dynamic(() -> fromJson(cachedSummaryJson[0])).asWidget()
                .width(DISPLAY_TEXT_WIDTH);

        // Keep the statistics TextWidget inside the ListWidget's actual content width.
        // The previous 1000px child inside a 440px ListWidget triggered ModularUI's
        // padding/layout warning and could cause the statistics viewport to clip the
        // dynamically synchronized text. The Terminal has no ListWidget and therefore
        // did not suffer from this mismatch.
        //
        // The text remains a single dynamically synchronized component so line breaks
        // supplied by the authoritative server snapshot are preserved exactly.
        var statisticsTextWidget = Text.dynamic(() -> fromJson(cachedStatisticsJson[0])).asWidget()
                .width(STATISTICS_TEXT_WIDTH)
                .margin(0);

        ListWidget<?, ?> statisticsList = new ListWidget<>()
                .size(CONTENT_WIDTH, CONTENT_HEIGHT)
                .child(statisticsTextWidget);

        Flow column = Flow.col()
                .margin(2)
                .size(CONTENT_WIDTH, PANEL_HEIGHT)
                .child(Text.dynamic(() -> Component.translatable(
                        self().getBlockState().getBlock().getDescriptionId())).asWidget())
                .child(summaryWidget)
                .child(Text.dynamic(() -> Component.translatable(
                        "gtmthings.machine.wireless_energy_monitor.tooltip.statistics")).asWidget())
                .child(statisticsList);

        mainWidget.size(CONTENT_WIDTH, PANEL_HEIGHT).child(column);
    }

    private static String buildJson(List<Component> lines) {
        if (lines.isEmpty()) return Component.Serializer.toJson(Component.empty());
        Component result = lines.get(0);
        for (int i = 1; i < lines.size(); i++) {
            result = result.copy().append(Component.literal("\n")).append(lines.get(i));
        }
        return Component.Serializer.toJson(result);
    }

    private static Component fromJson(String json) {
        if (json == null || json.isEmpty()) return Component.empty();
        try {
            Component component = Component.Serializer.fromJson(json);
            return component == null ? Component.empty() : component;
        } catch (Exception ignored) {
            return Component.empty();
        }
    }

    // IWirelessMonitor / IWirelessEnergyContainerHolder
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public @Nullable UUID getUUID() {
        return wirelessTeamUUID;
    }

    @Override
    public boolean display() {
        return false;
    }
}
