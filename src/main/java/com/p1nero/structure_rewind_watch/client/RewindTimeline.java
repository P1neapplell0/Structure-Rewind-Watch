package com.p1nero.structure_rewind_watch.client;

/** Pure tick clock: no wall time, so both winding and fade stop with the singleplayer simulation. */
public final class RewindTimeline {
    private int ticks;
    private double finishedAt = -1;
    public void start() { ticks = 0; finishedAt = -1; }
    public void tick(boolean paused) { if (!paused) ticks++; }
    public double seconds(float partialTick) { return (ticks + Math.max(0, Math.min(1, partialTick))) / 20.0; }
    public void finish(float partialTick) { if (finishedAt < 0) finishedAt = seconds(partialTick); }
    public double ending(float partialTick) { return finishedAt < 0 ? -1 : Math.max(0, seconds(partialTick) - finishedAt); }
    public static float rotation(double seconds) {
        double windup = Math.min(seconds, 5);
        // Smooth velocity transition: accelerate for 5 seconds, then maintain 840 degrees/second.
        return (float) (-windup * 90 - windup * windup * 75 - Math.max(0, seconds - 5) * 840);
    }
}
