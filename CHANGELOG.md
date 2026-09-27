# 1.0.0 正式发布准备

之前版本号仅用于未发布开发测试，正式版本统一为 1.0.0。

- 修复指针在 5 秒后停止旋转，等待结构恢复时持续倒转。
- 改用客户端游戏 tick 时钟，单人暂停同时暂停动画和渐隐。
- 播放期间禁用常规 HUD，GUI 最后绘制特效，保留暂停菜单。
- 增加面向玩家的模组介绍，文件名区分加载器和 Minecraft 版本。
- 新增 Forge 1.20.1 / 47.4.0 移植分支，保留 NeoForge 1.21.1 版本。

以下为未发布开发迭代记录。

# 1.1.0

- 新增回溯抑制器方块和物品，默认 100 格抑制范围，可配置。
- 方块实体每 20 tick 通知同维度附近玩家；缓存已知坐标，不扫描世界，不强制加载区块。
- 记录并持久化摆放者 UUID，仅摆放者/权限等级 2+ 管理员可拆除，包含创造模式。
- 防爆、禁止实体破坏和活塞搬移，怀表重置保留抑制器。
- 全透明中心的金色像素边框，中心全亮自转怀表模型，中英双语 Tooltip。
- 验证距离、所有权、重叠来源、配置、激活阻止和吟唱取消。

# 1.0.2

- 修正线条厚度单侧偏移，针身与对称箭头共用中轴，针身不再伸到箭头尖端之外。
- 移除按区域同类实体数量抵扣的逻辑，改用生成位置和持久化来源标记匹配。
- 支持补回模板实体、恢复匹配实体的初始状态；新增两项实体恢复配置。
- 保护玩家、宠物和骑乘关系；确认新实体成功加入世界后才移除旧实体。
- 回溯期间临时加载结构区块，等待实体数据就绪；完成/异常时释放加载票据。
- 添加模板实体恢复、重复回溯和实体安全回归测试，保留 1.0.1 的整合包启动兼容修复。

# 1.0.1

修复与整合包中已有区块后处理方法的启动冲突。

用户提供的 `crash-2026-09-27_19.06.13-client.txt` 和 `latest.log` 中的直接原因：

```text
InvalidMixinException: markPosForPostprocessing(...) in
structure_rewind_watch.mixins.json:LevelChunkMixin
cannot overwrite method in net.minecraft.world.level.chunk.LevelChunk
because @Overwrite is required by the parent configuration
```

1.0.0 向 `LevelChunk` 添加了原版继承自 `ChunkAccess` 的同名方法。其他模组也添加此方法时，它会变成一次未声明的覆盖，从而在启动时被 Mixin 拒绝。报告中的整合包安装了 `respawningstructures-1.21.1-4.4.jar`，其实现也添加了此方法。

1.0.1 不再添加或覆盖该方法：在 `ChunkAccess` 的原版方法中注入，并在 `LevelChunk` 已存在覆盖方法时进行可选注入。仅服务端当前回溯任务处理自己的后处理队列；其他时间完整保留原版/其他模组行为。不通过关闭 Mixin 校验绕过问题。

验证：常规 GameTest 与兼容性 GameTest 均包含四项测试；兼容性环境通过开发专用 Mixin 预先添加同名方法，验证启动、结构/地下宝箱恢复、冷却与 OP 行为，以及回溯之外保留原方法行为。开发测试类和配置不进入发布 jar。

```sh
./gradlew build runGameTestServer
./gradlew runGameTestServer -PrewindCompatibilityTest
```

这验证了报告中的方法冲突场景，不代表已在用户的完整 Windows 整合包上运行。安装时将旧版 1.0.0 替换成 1.0.1，不要同时保留两版。
