package com.p1nero.structure_rewind_watch;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue COOLDOWN = B.comment("Cooldown in seconds / 冷却秒数；OP level 2+ bypasses cooldown.")
            .defineInRange("cooldownSeconds", 1200, 0, 107374182);
    public static final ModConfigSpec.IntValue SEARCH_RADIUS = B.comment("Distance in blocks from player to structure bounding box / 结构搜索距离（格）")
            .defineInRange("searchRadius", 10, 0, 128);
    public static final ModConfigSpec.IntValue EFFECT_RADIUS = B.comment("Players within this distance of the structure see the transition / 特效影响范围（格）")
            .defineInRange("effectRadius", 64, 0, 512);
    public static final ModConfigSpec.IntValue CHUNKS_PER_TICK = B.comment("Structure chunks placed per server tick / 每 tick 重置区块数")
            .defineInRange("chunksPerTick", 1, 1, 16);
    public static final ModConfigSpec.IntValue MAX_CHUNKS = B.comment("Reject structures larger than this chunk count / 最大结构区块数")
            .defineInRange("maxStructureChunks", 1024, 1, 16384);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELIST = B.comment("Full-match Java regex of structure IDs. Empty allows all. / 白名单，空为全部允许。例如 minecraft:.*")
            .defineListAllowEmpty("structureWhitelist", List.of(), Config::validRegex);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST = B.comment("Full-match Java regex. Blacklist takes priority. / 黑名单优先，例如 minecraft:(stronghold|fortress)")
            .defineListAllowEmpty("structureBlacklist", List.of(), Config::validRegex);
    public static final ModConfigSpec.BooleanValue RESTORE_TEMPLATE_ENTITIES = B.comment("Restore entities stored in structure templates during rewind / 回溯时恢复模板中保存的实体")
            .define("restoreTemplateEntities", true);
    public static final ModConfigSpec.BooleanValue RESET_EXISTING_ENTITIES = B.comment("Reset matching structure entities; false only restores missing entities. Players and tamed/owned creatures are preserved. / 重置匹配的结构实体；false 仅补充缺失实体")
            .define("resetExistingStructureEntities", true);
    public static final ModConfigSpec.IntValue INHIBITOR_RADIUS = B.comment("Rewind inhibitor radius in blocks, measured in 3D; affects owners and OPs too. / 回溯抑制半径（格），摆放者和管理员同样受限")
            .defineInRange("inhibitorRadius", 100, 1, 512);
    public static final ModConfigSpec SPEC = B.build();

    static boolean validRegex(Object value) {
        if (!(value instanceof String s) || s.length() > 256) return false;
        try { Pattern.compile(s); return true; } catch (PatternSyntaxException e) { return false; }
    }

    public static boolean allowed(String id) {
        return BLACKLIST.get().stream().noneMatch(id::matches)
                && (WHITELIST.get().isEmpty() || WHITELIST.get().stream().anyMatch(id::matches));
    }
}
