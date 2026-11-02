package com.hepdd.gtmthings.common.item;

import com.gregtechceu.gtceu.api.mui.IItemUIHolder;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.StringSyncValue;
import brachy.modularui.widgets.layout.Flow;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

import static com.hepdd.gtmthings.common.block.machine.electric.WirelessEnergyMonitor.CONTENT_HEIGHT;
import static com.hepdd.gtmthings.common.block.machine.electric.WirelessEnergyMonitor.CONTENT_WIDTH;
import static com.hepdd.gtmthings.common.block.machine.electric.WirelessEnergyMonitor.DISPLAY_TEXT_WIDTH;

public class WirelessEnergyTerminalBehavior implements IItemUIHolder {

    @Override
    public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager syncManager,
                                   UISettings settings) {
        var player = data.getPlayer();
        String descId = data.getUsedItemStack().getDescriptionId();

        WirelessMonitor monitor = new WirelessMonitor(player.getUUID(), player.level());

        String[] cachedJson = { "" };

        StringSyncValue textSync = new StringSyncValue(
                () -> {
                    // The UI builder/sync supplier may run on the client. The wireless
                    // container and SavedData are server-only, so the client must wait
                    // for the synchronized server display payload instead of calling
                    // getDisplayText() locally.
                    if (monitor.getLevel().isClientSide()) return "";
                    return monitor.buildDisplayJson(DISPLAY_TEXT_WIDTH);
                },
                json -> cachedJson[0] = json);
        syncManager.syncValue("wem_display_text", textSync);

        ModularPanel<?> panel = ModularPanel.defaultPanel("wireless_energy_terminal",
                CONTENT_WIDTH + 20, CONTENT_HEIGHT + 40);

        Flow column = Flow.col()
                .coverChildren()
                .margin(2)
                .child(Text.dynamic(() -> Component.translatable(descId)).asWidget())
                .child(Text.dynamic(() -> {
                    if (cachedJson[0].isEmpty()) return Component.empty();
                    try {
                        Component c = Component.Serializer.fromJson(cachedJson[0]);
                        return c != null ? c : Component.empty();
                    } catch (Exception e) {
                        return Component.empty();
                    }
                }).asWidget());

        return panel.child(column);
    }

    private static class WirelessMonitor implements IWirelessMonitor {

        private final UUID uuid;
        private final Level level;

        @Getter
        @Setter
        private WirelessEnergyContainer WirelessEnergyContainerCache;

        private WirelessMonitor(UUID teamUUID, Level level) {
            this.uuid = com.hepdd.gtmthings.utils.TeamUtil.getWirelessNetworkUUID(teamUUID);
            this.level = level;
        }

        String buildDisplayJson(int displayWidth) {
            List<Component> lines = getDisplayText(false, displayWidth);
            if (lines.isEmpty()) return Component.Serializer.toJson(Component.empty());

            Component result = lines.get(0);
            for (int i = 1; i < lines.size(); i++) {
                result = result.copy().append(Component.literal("\n")).append(lines.get(i));
            }
            return Component.Serializer.toJson(result);
        }

        @Override
        public @Nullable UUID getUUID() {
            return uuid;
        }

        @Override
        public boolean display() {
            return false;
        }

        @Override
        public Level getLevel() {
            return level;
        }
    }
}
