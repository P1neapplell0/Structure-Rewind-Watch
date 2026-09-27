package com.p1nero.structure_rewind_watch;

import com.p1nero.structure_rewind_watch.client.RewindTimeline;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StructureRewindWatchMod.MODID)
@PrefixGameTestTemplate(false)
public class AnimationGameTests {
    @GameTest(template = "empty")
    public static void pauseAndContinuousRotation(GameTestHelper helper) {
        RewindTimeline clock = new RewindTimeline();
        clock.start();
        for (int i = 0; i < 120; i++) clock.tick(false);
        double before = clock.seconds(.25f);
        for (int i = 0; i < 200; i++) clock.tick(true);
        helper.assertTrue(clock.seconds(.25f) == before, "Pause must freeze winding");
        helper.assertTrue(RewindTimeline.rotation(6) < RewindTimeline.rotation(5), "Hands continue after five seconds");
        helper.assertTrue(RewindTimeline.rotation(7) - RewindTimeline.rotation(6) == -840, "Waiting phase keeps rotating");
        clock.finish(.25f);
        for (int i = 0; i < 200; i++) clock.tick(true);
        helper.assertTrue(clock.ending(.25f) == 0, "Pause must freeze fade too");
        clock.tick(false);
        helper.assertTrue(clock.ending(.25f) > 0, "Resume continues fade");
        helper.succeed();
    }
}
