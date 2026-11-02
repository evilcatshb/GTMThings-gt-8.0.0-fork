package com.hepdd.gtmthings;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.FinishedRecipe;

import com.hepdd.gtmthings.common.registry.GTMTRegistration;
import com.hepdd.gtmthings.data.GTMTRecipe;

import java.util.function.Consumer;

@GTAddon
public class GTMTGTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return GTMTRegistration.GTMTHINGS_REGISTRATE;
    }

    @Override
    public void initializeAddon() {}

    @Override
    public String addonModId() {
        return GTMThings.MOD_ID;
    }

    @Override
    public void addRecipes(Consumer<FinishedRecipe> provider) {
        System.out.println("[GTMThings ADDON] ======================================");
        System.out.println("[GTMThings ADDON] addRecipes() CALLED");
        System.out.println("[GTMThings ADDON] Addon MOD_ID: " + addonModId());
        System.out.println("[GTMThings ADDON] Provider class: " + provider.getClass().getName());
        System.out.println("[GTMThings ADDON] Starting GTMTRecipe.init()...");
        System.out.println("[GTMThings ADDON] ======================================");

        GTMTRecipe.init(provider);

        System.out.println("[GTMThings ADDON] ======================================");
        System.out.println("[GTMThings ADDON] GTMTRecipe.init() COMPLETED");
        System.out.println("[GTMThings ADDON] ======================================");
    }
}
