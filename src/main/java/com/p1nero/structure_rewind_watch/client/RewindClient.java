package com.p1nero.structure_rewind_watch.client;

import com.p1nero.structure_rewind_watch.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@Mod(value = StructureRewindWatchMod.MODID, dist = Dist.CLIENT)
public final class RewindClient {
    private static long sequence;
    private static final RewindTimeline TIMELINE = new RewindTimeline();
    private static boolean active, success;
    private static int lastBeat;
    private static final String[] NUMERALS = {"XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI"};

    public RewindClient(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(StructureRewindWatchMod.INHIBITOR_ENTITY.get(), InhibitorRenderer::new));
        RewindPayload.receiver = RewindClient::receive;
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) active = false;
            if (active) TIMELINE.tick(mc.isPaused());
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, false,
                RenderGuiEvent.Pre.class, event -> { if (isActive()) event.setCanceled(true); });
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, false,
                net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre.class,
                event -> { if (isActive()) event.setCanceled(true); });
    }

    private static void receive(RewindPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.phase() == 0) {
            sequence = payload.sequence(); TIMELINE.start();
            lastBeat = -1; active = true;
            if (mc.player != null) mc.player.playSound(SoundEvents.BEACON_ACTIVATE, 0.7f, 0.65f);
        } else if (active && sequence == payload.sequence()) {
            TIMELINE.finish(mc.getTimer().getGameTimeDeltaPartialTick(false)); success = payload.phase() == 1;
            if (mc.player != null) mc.player.playSound(success ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.65f);
        }
    }

    public static boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        return active && mc.level != null && mc.player != null;
    }

    public static boolean behindMenu() {
        Minecraft mc = Minecraft.getInstance();
        return mc.isPaused() || mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen;
    }

    /** Called after GUI layers/screens/toasts have queued their rendering, with the GUI projection active. */
    public static void renderOverlay(float partialTick) {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        double time = TIMELINE.seconds(partialTick);
        double ending = TIMELINE.ending(partialTick);
        if (ending > 1.7 || time > 180) { active = false; return; }
        GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        // Finish any pending item/HUD batches and discard their depth before drawing the overlay.
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.clear(256, Minecraft.ON_OSX);
        float fade = ending < 0 ? 1 : (float) Math.max(0, 1 - ending / 1.7);
        float entrance = (float) Math.min(1, time / 0.8);
        float opacity = fade * entrance;
        int beat = (int) (time * 3);
        if (beat != lastBeat && ending < 0 && time < 4.7) {
            lastBeat = beat; mc.player.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.16f, (float) (1.5 - Math.min(time, 4.7) * 0.16));
        }
        int width = g.guiWidth(), height = g.guiHeight();
        float blackout = (float) Math.min(1, time / 4.4) * fade;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(0, 0, width, height, color(0x02040b, blackout));
        g.fillGradient(0, 0, width, height, color(0x202e48, opacity * .45f), color(0x000000, opacity * .75f));
        float scale = Math.min(width / 420f, height / 330f);
        g.pose().translate(width / 2f, height / 2f - 12 * scale, 0);
        g.pose().scale(scale, scale, 1);
        // Wide low-opacity rings form a halo around a crisp astronomical dial.
        for (int i = 12; i > 0; i--) circle(g, 106 + i * 1.5f, 0, 360, 2, color(0xf5d58a, opacity * .012f));
        circle(g, 108, 0, 360, 1, color(0xffe8b0, opacity));
        circle(g, 101, 0, 360, .7f, color(0xd6af62, opacity * .6f));
        circle(g, 76, 0, 360, .6f, color(0xd6af62, opacity * .45f));
        for (int i = 0; i < 4; i++) {
            circle(g, 119, (float) (-time * 16 + i * 90), 55, 1.2f, color(0xe6c57e, opacity * .7f));
            circle(g, 128, (float) (time * 9 + i * 90), 28, .7f, color(0xd4eaff, opacity * .35f));
        }
        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians(i * 6 - 90);
            float inner = i % 5 == 0 ? 91 : 97;
            line(g, (float) Math.cos(angle) * inner, (float) Math.sin(angle) * inner,
                    (float) Math.cos(angle) * 102, (float) Math.sin(angle) * 102,
                    i % 5 == 0 ? 1.5f : .7f, color(0xffe3a3, opacity * (i % 5 == 0 ? 1 : .55f)));
        }
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30 - 90);
            g.drawCenteredString(mc.font, NUMERALS[i], (int) (Math.cos(angle) * 84), (int) (Math.sin(angle) * 84) - 4, color(0xfbe7bf, opacity));
        }
        // Negative angles produce counter-clockwise motion; speed builds during windup.
        float rotation = RewindTimeline.rotation(time);
        for (int trail = 7; trail >= 1; trail--) hand(g, rotation + trail * 8, 69, 1.2f, color(0xf1d69d, opacity * .035f));
        hand(g, rotation, 69, 2, color(0xfff4d8, opacity));
        hand(g, rotation / 5 - 65, 46, 3, color(0xe8c67b, opacity));
        circle(g, 5, 0, 360, 2, color(0xffffff, opacity));
        for (int i = 0; i < 42; i++) {
            double angle = i * 2.39996 - time * (.15 + i % 4 * .07);
            double radius = 135 + 30 * Math.sin(i * 12.7 + time * .6);
            float x = (float) (Math.cos(angle) * radius), y = (float) (Math.sin(angle) * radius);
            float twinkle = (float) (.15 + .45 * Math.pow(Math.sin(time * 2 + i), 2));
            line(g, x - 1.5f, y, x + 1.5f, y, .7f, color(0xffe7ac, opacity * twinkle));
            line(g, x, y - 1.5f, x, y + 1.5f, .7f, color(0xffe7ac, opacity * twinkle));
        }
        String key = ending >= 0 ? (success ? "complete" : "failed") : (time >= 5 ? "restoring" : "title");
        g.drawCenteredString(mc.font, Component.translatable("effect.structure_rewind_watch." + key), 0, 145, color(0xffeac4, opacity));
        g.pose().popPose();
        g.flush();
    }

    private static void hand(GuiGraphics g, float angle, float length, float thickness, int color) {
        g.pose().pushPose();
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        // Symmetric spearhead; stop the shaft at its base so it cannot protrude past the tip.
        line(g, 0, 10, 0, -length + 8, thickness, color);
        line(g, -4, -length + 10, 0, -length, 1, color);
        line(g, 4, -length + 10, 0, -length, 1, color);
        line(g, -4, -length + 10, 4, -length + 10, .7f, color);
        g.pose().popPose();
    }

    private static void circle(GuiGraphics g, float radius, float start, float arc, float thickness, int color) {
        for (float a = start; a < start + arc; a += 3) {
            double x = Math.toRadians(a), y = Math.toRadians(Math.min(a + 3, start + arc));
            line(g, (float) Math.cos(x) * radius, (float) Math.sin(x) * radius,
                    (float) Math.cos(y) * radius, (float) Math.sin(y) * radius, thickness, color);
        }
    }

    private static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, int color) {
        g.pose().pushPose();
        g.pose().translate(x1, y1, 0);
        g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(y2 - y1, x2 - x1)));
        g.pose().scale((float) Math.hypot(x2 - x1, y2 - y1), thickness, 1);
        // Center the rectangle on the mathematical line rather than offsetting its full width.
        g.pose().translate(0, -.5f, 0);
        g.fill(0, 0, 1, 1, color);
        g.pose().popPose();
    }

    private static int color(int rgb, float alpha) { return (Mth.clamp((int) (alpha * 255), 1, 255) << 24) | rgb; }
}
