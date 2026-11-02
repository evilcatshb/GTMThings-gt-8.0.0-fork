package com.hepdd.gtmthings.client;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.hepdd.gtmthings.GTMThings;
import com.hepdd.gtmthings.common.block.machine.electric.WirelessEnergyMonitor;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;

@Mod.EventBusSubscriber(modid = GTMThings.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class ForgeClientEventHandler {

    @SubscribeEvent
    public static void onRenderWorldLast(RenderLevelStageEvent event) {
        var stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;

            if (WirelessEnergyMonitor.p > 0) {
                if (GTValues.CLIENT_TIME % 20 == 0) {
                    WirelessEnergyMonitor.p--;
                }
                PoseStack poseStack = event.getPoseStack();
                Camera camera = event.getCamera();
                BlockPos pose = WirelessEnergyMonitor.pPos;
                if (pose == null) return;
                highlightBlock(camera, poseStack, pose, pose);
            }
        }
    }

    private static void highlightBlock(Camera camera, PoseStack poseStack, BlockPos... poses) {
        Vec3 pos = camera.getPosition();
        poseStack.pushPose();
        poseStack.translate(-pos.x, -pos.y, -pos.z);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        double minX = poses[0].getX();
        double minY = poses[0].getY();
        double minZ = poses[0].getZ();
        double maxX = poses[1].getX() + 1;
        double maxY = poses[1].getY() + 1;
        double maxZ = poses[1].getZ() + 1;
        float r = 0.2f, g = 0.2f, b = 1.0f;

        // Render cube faces
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        renderCubeFace(poseStack, buffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 0.25f);
        tesselator.end();

        // Render cube frame
        buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(3);
        renderCubeFrame(poseStack, buffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, 0.0f, 0.5f);
        tesselator.end();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        poseStack.popPose();
    }

    private static void renderCubeFace(PoseStack poseStack, BufferBuilder buffer,
                                       double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ,
                                       float r, float g, float b, float a) {
        var matrix = poseStack.last().pose();
        // Bottom face (y = minY)
        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, a).endVertex();
        // Top face (y = maxY)
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, a).endVertex();
        // North face (z = minZ)
        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, a).endVertex();
        // South face (z = maxZ)
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, a).endVertex();
        // West face (x = minX)
        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, a).endVertex();
        // East face (x = maxX)
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, a).endVertex();
    }

    private static void renderCubeFrame(PoseStack poseStack, BufferBuilder buffer,
                                        double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ,
                                        float r, float g, float b, float a) {
        var matrix = poseStack.last().pose();
        var normal = poseStack.last().normal();
        // Bottom edges
        line(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        // Top edges
        line(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        // Vertical edges
        line(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void line(BufferBuilder buffer, org.joml.Matrix4f matrix,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(0, 0, 0).endVertex();
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(0, 0, 0).endVertex();
    }
}
