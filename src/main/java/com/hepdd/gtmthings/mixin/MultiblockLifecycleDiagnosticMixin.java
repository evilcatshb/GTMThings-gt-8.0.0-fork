package com.hepdd.gtmthings.mixin;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.PowerSubstationMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.SortedSet;

/** Diagnostic-only tracing. This mixin never changes multiblock state. */
@Mixin(MultiblockControllerMachine.class)
abstract class MultiblockControllerLifecycleDiagnosticMixin {

    @Inject(method = "onLoad", at = @At("HEAD"), remap = false)
    private void diag$onLoad(CallbackInfo ci) {
        if ((Object) this instanceof PowerSubstationMachine controller) logController("CONTROLLER.onLoad", controller, null);
    }

    @Inject(method = "onUnload", at = @At("HEAD"), remap = false)
    private void diag$onUnload(CallbackInfo ci) {
        if ((Object) this instanceof PowerSubstationMachine controller) logController("CONTROLLER.onUnload", controller, null);
    }

    @Inject(method = "onPartUnload", at = @At("HEAD"), remap = false)
    private void diag$onPartUnload(CallbackInfo ci) {
        if ((Object) this instanceof PowerSubstationMachine controller) logController("CONTROLLER.onPartUnload", controller, null);
    }

    @Inject(method = "checkAndFormStructure", at = @At("HEAD"), remap = false)
    private void diag$checkAndFormStructureHead(CallbackInfo ci) {
        if ((Object) this instanceof PowerSubstationMachine controller) logControllerWithCaller("CONTROLLER.checkAndFormStructure HEAD", controller, null);
    }

    @Inject(method = "checkAndFormStructure", at = @At("TAIL"), remap = false)
    private void diag$checkAndFormStructureTail(CallbackInfo ci) {
        if ((Object) this instanceof PowerSubstationMachine controller) logController("CONTROLLER.checkAndFormStructure TAIL", controller, null);
    }

    @Inject(method = "formStructure", at = @At("HEAD"), remap = false)
    private void diag$formStructureHead(String name, CallbackInfo ci) {
        if ((Object) this instanceof PowerSubstationMachine controller) logControllerWithCaller("CONTROLLER.formStructure HEAD name=" + name, controller, name);
    }

    @Inject(method = "formStructure", at = @At("TAIL"), remap = false)
    private void diag$formStructureTail(String name, CallbackInfo ci) {
        if ((Object) this instanceof PowerSubstationMachine controller) logController("CONTROLLER.formStructure TAIL name=" + name, controller, name);
    }

    @Inject(method = "invalidateStructure()V", at = @At("HEAD"), remap = false)
    private void diag$invalidateStructure(CallbackInfo ci) {
        if ((Object) this instanceof PowerSubstationMachine controller) logController("CONTROLLER.invalidateStructure()", controller, MultiblockControllerMachine.DEFAULT_STRUCTURE);
    }

    @Inject(method = "invalidateStructure(Ljava/lang/String;)V", at = @At("HEAD"), remap = false)
    private void diag$invalidateStructureNamed(String name, CallbackInfo ci) {
        if ((Object) this instanceof PowerSubstationMachine controller) logController("CONTROLLER.invalidateStructure(" + name + ")", controller, name);
    }

    @Unique
    private static void logControllerWithCaller(String event, MultiblockControllerMachine controller, String structureName) {
        logController(event, controller, structureName);
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        StringBuilder caller = new StringBuilder();
        for (int i = 3; i < st.length && i < 10; i++) {
            String c = st[i].getClassName();
            if (c.startsWith("com.gregtechceu.gtceu") || c.startsWith("com.hepdd.gtmthings")) {
                if (caller.length() > 0) caller.append(" <- ");
                caller.append(st[i].getClassName()).append("#").append(st[i].getMethodName()).append(":").append(st[i].getLineNumber());
            }
        }
        GTCEu.LOGGER.info("[GTMThings MultiblockLifecycleDiag] {} CALLER_CHAIN={}", event, caller);
    }

    @Unique
    private static void logController(String event, MultiblockControllerMachine controller, String structureName) {
        if (!(controller.getLevel() instanceof ServerLevel serverLevel)) return;
        PatternState pattern = controller.getPatternState(structureName == null ? MultiblockControllerMachine.DEFAULT_STRUCTURE : structureName);
        var parts = controller.getParts();
        if (parts.isEmpty()) log(event, controller, null, pattern, serverLevel);
        else for (MultiblockPartMachine part : parts) log(event, controller, part, pattern, serverLevel);
    }

    @Unique
    private static void log(String event, MultiblockControllerMachine controller, MultiblockPartMachine part,
                            PatternState pattern, ServerLevel serverLevel) {
        BlockPos controllerPos = controller.getBlockPos();
        BlockPos partPos = part == null ? null : part.getBlockPos();
        GTCEu.LOGGER.info("[GTMThings MultiblockLifecycleDiag] {} controllerPos={} partPos={} partClass={} patternState={} formed={} parts.size()={} serverLevel.isLoaded(partPos)={} serverLevel.isLoaded(controllerPos)={}",
                event, controllerPos, partPos == null ? "<none>" : partPos,
                part == null ? "<none>" : part.getClass().getName(),
                pattern == null ? "<none>" : pattern.getState().name(), controller.isFormed(),
                controller.getParts().size(), partPos != null && serverLevel.isLoaded(partPos),
                serverLevel.isLoaded(controllerPos));
    }
}

@Mixin(MultiblockPartMachine.class)
abstract class MultiblockPartLifecycleDiagnosticMixin {

    @Shadow
    protected SortedSet<MultiblockControllerMachine> controllers;

    @Inject(method = "onUnload", at = @At("HEAD"), remap = false)
    private void diag$onUnload(CallbackInfo ci) {
        MultiblockPartMachine part = (MultiblockPartMachine) (Object) this;
        if (!(part.getLevel() instanceof ServerLevel serverLevel)) return;
        boolean logged = false;
        synchronized (controllers) {
            for (MultiblockControllerMachine controller : controllers) {
                if (controller instanceof PowerSubstationMachine) {
                    logPart("PART.onUnload", part, controller, serverLevel);
                    logged = true;
                }
            }
        }
        if (!logged) logPart("PART.onUnload", part, null, serverLevel);
    }

    @Inject(method = "addedToController", at = @At("HEAD"), remap = false)
    private void diag$addedToController(MultiblockControllerMachine controller, String structureName, CallbackInfo ci) {
        if (controller instanceof PowerSubstationMachine && controller.getLevel() instanceof ServerLevel serverLevel)
            logPart("PART.addedToController", (MultiblockPartMachine) (Object) this, controller, serverLevel);
    }

    @Inject(method = "removedFromController", at = @At("HEAD"), remap = false)
    private void diag$removedFromController(MultiblockControllerMachine controller, CallbackInfo ci) {
        if (controller instanceof PowerSubstationMachine && controller.getLevel() instanceof ServerLevel serverLevel)
            logPart("PART.removedFromController", (MultiblockPartMachine) (Object) this, controller, serverLevel);
    }

    @Unique
    private static void logPart(String event, MultiblockPartMachine part,
                                MultiblockControllerMachine controller, ServerLevel serverLevel) {
        PatternState pattern = controller == null ? null : controller.getPatternState(MultiblockControllerMachine.DEFAULT_STRUCTURE);
        BlockPos controllerPos = controller == null ? null : controller.getBlockPos();
        BlockPos partPos = part.getBlockPos();
        GTCEu.LOGGER.info("[GTMThings MultiblockLifecycleDiag] {} controllerPos={} partPos={} partClass={} patternState={} formed={} parts.size()={} serverLevel.isLoaded(partPos)={} serverLevel.isLoaded(controllerPos)={}",
                event, controllerPos == null ? "<none>" : controllerPos, partPos, part.getClass().getName(),
                pattern == null ? "<none>" : pattern.getState().name(), controller != null && controller.isFormed(),
                controller == null ? -1 : controller.getParts().size(), serverLevel.isLoaded(partPos),
                controllerPos != null && serverLevel.isLoaded(controllerPos));
    }
}

/** MetaMachine declares onLoad; MultiblockPartMachine inherits it. */
@Mixin(MetaMachine.class)
abstract class MultiblockPartOnLoadDiagnosticMixin {

    @Inject(method = "onLoad", at = @At("TAIL"), remap = false)
    private void diag$onLoad(CallbackInfo ci) {
        if (!((Object) this instanceof MultiblockPartMachine part)) return;
        if (!(part.getLevel() instanceof ServerLevel serverLevel)) return;
        for (MultiblockControllerMachine controller : part.getControllers()) {
            if (controller instanceof PowerSubstationMachine) {
                PatternState pattern = controller.getPatternState(MultiblockControllerMachine.DEFAULT_STRUCTURE);
                GTCEu.LOGGER.info("[GTMThings MultiblockLifecycleDiag] PART.onLoad controllerPos={} partPos={} partClass={} patternState={} formed={} parts.size()={} serverLevel.isLoaded(partPos)={} serverLevel.isLoaded(controllerPos)={}",
                        controller.getBlockPos(), part.getBlockPos(), part.getClass().getName(),
                        pattern == null ? "<none>" : pattern.getState().name(), controller.isFormed(),
                        controller.getParts().size(), serverLevel.isLoaded(part.getBlockPos()),
                        serverLevel.isLoaded(controller.getBlockPos()));
            }
        }
    }
}
