package com.hepdd.gtmthings;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.hepdd.gtmthings.config.ConfigHolder;
import com.hepdd.gtmthings.data.*;

import static com.hepdd.gtmthings.common.registry.GTMTRegistration.GTMTHINGS_REGISTRATE;

@Mod(GTMThings.MOD_ID)
public class GTMThings {

    public static final String MOD_ID = "gtmthings";
    public static final String NAME = "GTM Things";

    public static ResourceLocation id(String name) {
        return new ResourceLocation(MOD_ID, name);
    }

    public GTMThings(FMLJavaModLoadingContext context) {
        System.out.println("[GTMThings INIT] ======================================");
        System.out.println("[GTMThings INIT] GTMThings mod constructor starting");

        IEventBus modEventBus = context.getModEventBus();
        // TEMPORARY DIAGNOSTIC: inspect the final Forge creative-tab contents at
        // LOWEST priority, after normal tab population listeners have run.
        // BuildCreativeModeTabContentsEvent is an IModBusEvent in Forge 1.20.1.
        modEventBus.addListener(EventPriority.LOWEST, this::diagnoseCreativeTabContents);
        ConfigHolder.init();
        GTMTHINGS_REGISTRATE.registerEventListeners(modEventBus);

        System.out.println("[GTMThings INIT] Registering GTRecipeType event listener...");
        modEventBus.addGenericListener(GTRecipeType.class, this::registerRecipeTypes);

        System.out.println("[GTMThings INIT] Registering MachineDefinition event listener...");
        modEventBus.addGenericListener(MachineDefinition.class, this::registerMachines);

        System.out.println("[GTMThings INIT] Registering CoverDefinition event listener...");
        modEventBus.addGenericListener(CoverDefinition.class, this::registerCovers);

        System.out.println("[GTMThings INIT] ======================================");
    }

    private void registerRecipeTypes(GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        System.out.println("[GTMThings RECIPE_TYPES] ======================================");
        System.out.println("[GTMThings RECIPE_TYPES] registerRecipeTypes() CALLED");

        GTMTRecipeTypes.init();

        System.out.println("[GTMThings RECIPE_TYPES] Verifying Digital Miner recipe type...");
        var digitalMinerType = GTRegistries.RECIPE_TYPES.get(GTMThings.id("digital_miner"));
        if (digitalMinerType != null) {
            System.out.println("[GTMThings RECIPE_TYPES] ✓ Found recipe type: " + digitalMinerType.registryName);
            System.out.println("[GTMThings RECIPE_TYPES] ✓ Category: " + digitalMinerType.getCategory());
            System.out.println("[GTMThings RECIPE_TYPES] ✓ Category in registry: " +
                    GTRegistries.RECIPE_CATEGORIES.containsKey(GTMThings.id("digital_miner")));
        } else {
            System.out.println("[GTMThings RECIPE_TYPES] ✗ ERROR: Recipe type NOT FOUND!");
        }
        System.out.println("[GTMThings RECIPE_TYPES] ======================================");
    }

    private void registerMachines(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        System.out.println("[GTMThings MACHINES] Registering machines...");
        // Creative tabs must be fully initialized before any GTRegistrate
        // registration selects one as its current creative tab.
        // CreativeModeTabs deliberately uses lazy icon suppliers so this call
        // cannot recursively initialize CustomMachines/WirelessMachines.
        CreativeModeTabs.init();
        CreativeMachines.init();
        WirelessMachines.init();
        CustomMachines.init();

        // TEMPORARY DIAGNOSTIC: verify GTRegistrate TAB_LOOKUP membership for
        // representative MORE_MACHINES registrations immediately after creation.
        var moreMachinesTab = CreativeModeTabs.MORE_MACHINES;
        System.out.println("[GTMThings MORE_MACHINES DIAG] tab=" + moreMachinesTab);
        for (var entry : GTMTHINGS_REGISTRATE.getAll(net.minecraft.core.registries.Registries.BLOCK)) {
            var id = entry.getId();
            if (id.getNamespace().equals(MOD_ID)) {
                if (id.getPath().contains("digital_miner") || id.getPath().contains("huge_") || id.getPath().contains("programmablec") || id.getPath().equals("me_export_buffer")) {
                    System.out.println("[GTMThings MORE_MACHINES DIAG] BLOCK " + id + " -> " + GTMTHINGS_REGISTRATE.isInCreativeTab(entry, moreMachinesTab));
                }
            }
        }
        for (var entry : GTMTHINGS_REGISTRATE.getAll(net.minecraft.core.registries.Registries.ITEM)) {
            var id = entry.getId();
            if (id.getNamespace().equals(MOD_ID)) {
                if (id.getPath().contains("digital_miner") || id.getPath().contains("huge_") || id.getPath().contains("programmablec") || id.getPath().equals("me_export_buffer")) {
                    System.out.println("[GTMThings MORE_MACHINES DIAG] ITEM " + id + " -> " + GTMTHINGS_REGISTRATE.isInCreativeTab(entry, moreMachinesTab));
                }
            }
        }
        System.out.println("[GTMThings MACHINES] Machines registered successfully");
    }

    private void diagnoseCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        var tabKey = event.getTabKey();
        if (tabKey == null) return;

        boolean isWireless = tabKey.location().equals(GTMThings.id("wireless"));
        boolean isMoreMachines = tabKey.location().equals(GTMThings.id("more_machines"));
        if (!isWireless && !isMoreMachines) return;

        var entries = event.getEntries();
        int gtmthingsCount = 0;
        int targetCount = 0;
        int entryCount = 0;
        for (var entry : entries) {
            entryCount++;
            var stack = entry.getKey();
            var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null && id.getNamespace().equals(MOD_ID)) {
                gtmthingsCount++;
                String path = id.getPath();
                if (path.contains("digital_miner") || path.contains("huge_") || path.contains("programmablec") || path.equals("me_export_buffer")) {
                    targetCount++;
                    System.out.println("[GTMThings CREATIVE TAB DIAG] TARGET " + id + " x" + stack.getCount());
                }
            }
        }
        System.out.println("[GTMThings CREATIVE TAB DIAG] tab=" + tabKey + " entries=" + entryCount + " GTMThings entries=" + gtmthingsCount + " targets=" + targetCount);
    }

    private void registerCovers(GTCEuAPI.RegisterEvent<ResourceLocation, CoverDefinition> event) {
        System.out.println("[GTMThings COVERS] Registering covers and items...");
        CreativeModeTabs.init();
        GTMTCovers.init();
        CustomItems.init();
        System.out.println("[GTMThings COVERS] Covers and items registered successfully");
    }
}
