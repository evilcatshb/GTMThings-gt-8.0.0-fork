package com.hepdd.gtmthings.utils;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;

import java.util.UUID;

/** Utilities for the single canonical identity used by every GTMThings wireless network. */
public final class TeamUtil {

    private TeamUtil() {}

    /**
     * Converts a player identity into the canonical wireless network identity.
     * If FTB Teams is active and the player belongs to a team, the team UUID is used;
     * otherwise the player's UUID is the network UUID.
     */
    public static UUID getWirelessNetworkUUID(UUID playerUUID) {
        if (playerUUID == null) return null;
        if (GTCEu.isModLoaded("ftbteams") && FTBTeamsAPI.api().isManagerLoaded()) {
            var team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(playerUUID);
            return team.map(Team::getTeamId).orElse(playerUUID);
        }
        return playerUUID;
    }

    /** Backwards-compatible name for old callers that pass a player UUID. */
    public static UUID getTeamUUID(UUID playerUUID) {
        return getWirelessNetworkUUID(playerUUID);
    }

    public static Component GetName(Player player) {
        if (player == null) return Component.literal("unknown");
        return getWirelessNetworkName(player.level(), getWirelessNetworkUUID(player.getUUID()));
    }

    /** Resolves an already-canonical wireless network UUID. Never remaps it through TeamUtil. */
    public static Component getWirelessNetworkName(Level level, UUID wirelessNetworkUUID) {
        if (wirelessNetworkUUID == null) return Component.literal("unknown");

        if (GTCEu.isModLoaded("ftbteams") && FTBTeamsAPI.api().isManagerLoaded()) {
            var manager = FTBTeamsAPI.api().getManager();
            var teamById = manager.getTeamByID(wirelessNetworkUUID);
            if (teamById.isPresent()) return teamById.get().getName();
            var teamForPlayer = manager.getTeamForPlayerID(wirelessNetworkUUID);
            if (teamForPlayer.isPresent()) return teamForPlayer.get().getName();
        }

        if (level != null) {
            Player player = level.getPlayerByUUID(wirelessNetworkUUID);
            if (player != null) return player.getName();
        }
        return Component.literal(wirelessNetworkUUID.toString());
    }

    /** Backwards-compatible resolver for callers whose UUID may be legacy player/team data. */
    public static Component GetName(Level level, UUID wirelessNetworkUUID) {
        return getWirelessNetworkName(level, wirelessNetworkUUID);
    }

    public static boolean hasOwner(Level level, UUID wirelessNetworkUUID) {
        if (wirelessNetworkUUID == null) return false;
        if (GTCEu.isModLoaded("ftbteams") && FTBTeamsAPI.api().isManagerLoaded()) {
            var manager = FTBTeamsAPI.api().getManager();
            if (manager.getTeamByID(wirelessNetworkUUID).isPresent()) return true;
            if (manager.getTeamForPlayerID(wirelessNetworkUUID).isPresent()) return true;
        }
        return level != null && level.getPlayerByUUID(wirelessNetworkUUID) != null;
    }
}
