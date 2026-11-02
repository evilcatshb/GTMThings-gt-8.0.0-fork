package com.hepdd.gtmthings.common.item;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.mui.IItemUIHolder;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.common.block.CoilBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import com.hepdd.gtmthings.api.misc.Hatch;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

import static com.hepdd.gtmthings.api.pattern.AdvancedBlockPattern.getAdvancedBlockPattern;

public class AdvancedTerminalBehavior implements IItemUIHolder {

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            Level level = context.getLevel();
            BlockPos blockPos = context.getClickedPos();
            if (!level.isClientSide() &&
                    MetaMachine.getMachine(level, blockPos) instanceof MultiblockControllerMachine controller) {
                AutoBuildSetting setting = getAutoBuildSetting(context.getPlayer().getMainHandItem());
                if (!controller.isFormed()) {
                    getAdvancedBlockPattern((com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern) controller.getDefinition().getStructurePatterns()
                            .get(MultiblockControllerMachine.DEFAULT_STRUCTURE).get())
                            .autoBuild(context.getPlayer(), controller.getDefaultPatternState(), setting);
                } else if (MetaMachine.getMachine(level, blockPos) instanceof WorkableMultiblockMachine wm && setting.isReplaceCoilMode()) {
                    getAdvancedBlockPattern((com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern) controller.getDefinition().getStructurePatterns()
                            .get(MultiblockControllerMachine.DEFAULT_STRUCTURE).get())
                            .autoBuild(context.getPlayer(), controller.getDefaultPatternState(), setting);
                    wm.onPartUnload();
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private AutoBuildSetting getAutoBuildSetting(ItemStack itemStack) {
        AutoBuildSetting s = new AutoBuildSetting();
        CompoundTag tag = itemStack.getTag();
        if (tag != null && !tag.isEmpty() && tag.contains("CoilTier")) {
            s.setCoilTier(tag.getInt("CoilTier"));
            s.setRepeatCount(tag.getInt("RepeatCount"));
            s.setNoHatchMode(tag.getInt("NoHatchMode"));
            s.setReplaceCoilMode(tag.getInt("ReplaceCoilMode"));
            s.setIsUseAE(tag.getInt("IsUseAE"));
        }
        return s;
    }

    // ── UI ───────────────────────────────────────────────────────────────────

    @Override
    public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager syncManager,
                                   UISettings settings) {
        ItemStack handItem = data.getUsedItemStack();

        // Build coil list for tooltip
        List<Component> coilLines = new ArrayList<>();
        coilLines.add(Component.translatable("item.gtmthings.advanced_terminal.setting.1.tooltip"));
        GTCEuAPI.HEATING_COILS.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getKey().getTier()))
                .forEach(e -> coilLines.add(
                        Component.literal(String.valueOf(e.getKey().getTier() + 1))
                                .append(":").append(e.getValue().get().getName())));

        return ModularPanel.defaultPanel("advanced_terminal", 200, 200)
                .child(buildSettingsFlow(handItem, coilLines, syncManager));
    }

    private ParentWidget<?> buildSettingsFlow(ItemStack handItem, List<Component> coilLines,
                                              PanelSyncManager syncManager) {
        return Flow.col()
                .coverChildren()
                .margin(4, 4)
                .childPadding(3)
                .child(new TextWidget<>(Text.lang("item.gtmthings.advanced_terminal.setting.title")))
                .child(labeledField("item.gtmthings.advanced_terminal.setting.1",
                        SyncHandlers.intNumber(() -> getCoilTier(handItem), v -> setCoilTier(v, handItem)),
                        0, GTCEuAPI.HEATING_COILS.size(), syncManager))
                .child(labeledField("item.gtmthings.advanced_terminal.setting.2",
                        SyncHandlers.intNumber(() -> getRepeatCount(handItem), v -> setRepeatCount(v, handItem)),
                        0, 99, syncManager))
                .child(labeledField("item.gtmthings.advanced_terminal.setting.3",
                        SyncHandlers.intNumber(() -> getIsBuildHatches(handItem), v -> setIsBuildHatches(v, handItem)),
                        0, 1, syncManager))
                .child(labeledField("item.gtmthings.advanced_terminal.setting.4",
                        SyncHandlers.intNumber(() -> getReplaceCoilMode(handItem), v -> setReplaceCoilMode(v, handItem)),
                        0, 1, syncManager))
                .child(labeledField("item.gtmthings.advanced_terminal.setting.5",
                        SyncHandlers.intNumber(() -> getIsUseAE(handItem), v -> setIsUseAE(v, handItem)),
                        0, 1, syncManager));
    }

    private static ParentWidget<?> labeledField(String langKey,
                                                brachy.modularui.value.sync.ValueSyncHandler<?, ?> syncHandler,
                                                int min, int max, PanelSyncManager syncManager) {
        String syncKey = langKey.replace(".", "_");
        syncManager.syncValue(syncKey, syncHandler);
        return Flow.row().coverChildren().childPadding(4)
                .child(new TextWidget(Text.lang(langKey)))
                .child(new TextFieldWidget().setNumbers(min, max).syncHandler(syncKey));
    }

    // ── NBT helpers ──────────────────────────────────────────────────────────

    private int getCoilTier(ItemStack s) {
        var t = s.getTag();
        return t != null && t.contains("CoilTier") ? t.getInt("CoilTier") : 0;
    }

    private void setCoilTier(int v, ItemStack s) {
        ensureTag(s).putInt("CoilTier", v);
    }

    private int getRepeatCount(ItemStack s) {
        var t = s.getTag();
        return t != null && t.contains("RepeatCount") ? t.getInt("RepeatCount") : 0;
    }

    private void setRepeatCount(int v, ItemStack s) {
        ensureTag(s).putInt("RepeatCount", v);
    }

    private int getIsBuildHatches(ItemStack s) {
        var t = s.getTag();
        return t != null && t.contains("NoHatchMode") ? t.getInt("NoHatchMode") : 1;
    }

    private void setIsBuildHatches(int v, ItemStack s) {
        ensureTag(s).putInt("NoHatchMode", v);
    }

    private int getReplaceCoilMode(ItemStack s) {
        var t = s.getTag();
        return t != null && t.contains("ReplaceCoilMode") ? t.getInt("ReplaceCoilMode") : 0;
    }

    private void setReplaceCoilMode(int v, ItemStack s) {
        ensureTag(s).putInt("ReplaceCoilMode", v);
    }

    private int getIsUseAE(ItemStack s) {
        var t = s.getTag();
        return t != null && t.contains("IsUseAE") ? t.getInt("IsUseAE") : 0;
    }

    private void setIsUseAE(int v, ItemStack s) {
        ensureTag(s).putInt("IsUseAE", v);
    }

    private CompoundTag ensureTag(ItemStack s) {
        if (!s.hasTag()) s.setTag(new CompoundTag());
        return s.getTag();
    }

    // ── AutoBuildSetting ─────────────────────────────────────────────────────

    @Setter
    @Getter
    public static class AutoBuildSetting {

        @Getter
        @Setter
        private int coilTier = 0, repeatCount = 0, noHatchMode = 1, replaceCoilMode = 0, isUseAE = 0;

        public List<ItemStack> apply(BlockInfo[] blockInfos) {
            List<ItemStack> candidates = new ArrayList<>();
            if (blockInfos == null) return candidates;
            if (Arrays.stream(blockInfos).anyMatch(i -> i.getBlockState().getBlock() instanceof CoilBlock)) {
                int tier = Math.min(coilTier - 1, blockInfos.length - 1);
                if (tier == -1) {
                    for (int i = 0; i < blockInfos.length - 1; i++) candidates.add(blockInfos[i].getItemStackForm());
                } else {
                    candidates.add(blockInfos[tier].getItemStackForm());
                }
                return candidates;
            }
            for (BlockInfo info : blockInfos) {
                if (info.getBlockState().getBlock() != Blocks.AIR) candidates.add(info.getItemStackForm());
            }
            return candidates;
        }

        public boolean isPlaceHatch(BlockInfo[] blockInfos) {
            if (this.noHatchMode == 0) return true;
            if (blockInfos != null && blockInfos.length > 0) {
                var b = blockInfos[0].getBlockState().getBlock();
                return !(b instanceof MetaMachineBlock mmb) || !Hatch.Set.contains(mmb);
            }
            return true;
        }

        public boolean isReplaceCoilMode() {
            return replaceCoilMode == 1;
        }
    }
}
