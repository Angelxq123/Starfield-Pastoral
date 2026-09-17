# 多人传送、睡眠和日结定向审查

基线：`68d6fe311`。范围仅包括巫师塔/女巫小屋、相关世界传送、睡眠投票、日结及直接调用点，不是全库审查。未修改生产代码。以下依据代码调用链和项目使用的 NeoForge 21.1.217 Minecraft 源码确认；未进行多客户端实服复现或 TPS 采样，不能据此量化卡顿毫秒数。

本次运行 5 个相关测试类：`ClientOvernightFlowTest`、`DailySettlementLifecycleContractTest`、`DailySettlementCoordinatorTest`、`DailySettlementReadyPublisherTest`、`FarmEntryTeleportQueueTest`。109 项全部通过，零失败/错误/跳过。通过仅说明既有用例未回归，不覆盖上述完整多人交错场景，也不验证真实区块加载耗时。

## 优先修复

### 1. [P1] 日结中途掉线可能丢失收益并破坏恢复状态

位置：`src/main/java/com/stardew/craft/time/settlement/PlayerDailySettlementService.java:123`。

触发：参与者快照已建立，但玩家在自己的个人结算工作项执行前掉线。`settlePlayer` 保存待结算记录后因离线返回；COMMIT 的清理工作项仍遍历所有参与者，`finishPreparedSettlement` 只检查记录存在，没有检查完成阶段或 `completedPayload`。生产清理路径随后消费出货、昏倒记录并清除待升级技能，将阶段提升至 12。重连时恢复逻辑因此跳过尚未执行的个人收益与恢复操作。若内存里保留空 fallback，待确认记录也可能无法正常解除。

建议：只有已经完成个人结算、持有对应日期完整结果的记录才能清理。未完成的离线记录留待登录补算。补充“快照后掉线→COMMIT→重连”测试，验证金币、升级、体力和锁状态。

### 2. [P1] 塔出口直接改区块追踪记录，会跳过实际区块发送

位置：`src/main/java/com/stardew/craft/interior/CrossDimensionTeleporter.java:616`，相关入口 `:611`。

`restoreDefaultChunkTrackingView` 在传送后直接调用 `ServerPlayer.setChunkTrackingView`。底层 setter 仅替换字段，不发送区块、中心点或卸载消息。尤其从塔内同维度传送至农场时，如果客户端有效视距与服务器一致，后续 `ChunkMap.updateChunkTracking` 看见“目标中心和视距已经相同”就返回，可能漏发新目的地的区块，表现为空白地形或区块不刷新。入口设置视距 2 也没有改变真实的 `getPlayerViewDistance`，下一次正常更新会恢复实际视距，并不会稳定限制加载范围。

建议：通过区块追踪器的正式差量更新路径调整视野，不能把“期望视图”直接写成“已经发送的视图”；如需室内独立视距，必须同时处理有效视距计算和客户端通知。测试同维度塔→农场、跨维度返回，以及客户端视距小于/等于服务器的情况。

### 3. [P1] 别人的投票可覆盖自己的睡眠确认框

位置：`src/main/java/com/stardew/craft/network/overnight/ClientOvernightUiGateway.java:13`。

触发：A、B 都打开睡觉确认框，A 先确认，B 尚未确认。服务端在打开确认框前已经让 B `isSleeping()`；A 的投票进度广播到所有玩家，客户端据此将 B 视为已投票者，用等待画面替换其确认框，但 B 实际没有投票。票数停在 1/2，全员睡眠又可能暂停时钟，无法自然等到 2AM。

建议：明确区分“床上待确认”和“服务端已接受睡眠投票”。只有后者才能切入等待界面；进度广播不能覆盖未提交的确认框。

### 4. [P1] 玩家跨维度离开后，剩余睡眠者的投票门槛不会重算

位置：`src/main/java/com/stardew/craft/event/DimensionEventHandler.java:503`，相关 `SleepVoteTracker.java:322`。

触发：100% 门槛，A 已投票、B 未投票；B 回到主世界但不掉线。参与人数实际减少，但维度切换没有重新评估门槛；定期睡眠处理只恢复能量，也没有重算。唯一剩余的 A 处于睡眠，时钟会暂停，投票不能自动完成。AFK 状态变化也缺少相同处理。

建议：维度、在线和 AFK 状态统一触发资格重算与进度广播；有未完成投票时增加低频复核，避免遗漏事件永久卡住。

## 性能优化

### 5. [P2] 传送预加载队列仍同步加载区块

位置：`src/main/java/com/stardew/craft/interior/CrossDimensionTeleporter.java:215`；同类代码 `event/DimensionEventHandler.java:71`。

返回塔外和农场入口队列在入队时调用 `setChunkForced(..., true)`。项目所用 `ServerLevel.setChunkForced` 在首次添加时先执行 `getChunk`，再注册强制加载，因此等待队列建立前已经同步加载目标区块。冷区块、磁盘延迟或多人同时传送时，所有玩家仍共用这段主线程阻塞。塔预热的同类调用发生在服务启动，也并非真正异步。

建议：使用独立、可释放的临时 region ticket 请求区块，后续 tick 只检查 `getChunkNow`，并限制每 tick 完成传送的时间和数量。已有 `InteriorPortalTickets` 可作为参考；避免借用全局 `/forceload` 状态。

### 6. [P2] 换季大扫描仍是单个原子日结任务

位置：`src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java:449`。

换季阶段同步恢复公共区域并执行 `WildWeedsBlock.refreshLoadedWeedsForSeason`。杂草刷新按玩家周围 21×21 个已加载区块扫描，每区块 256 列、每列最多 5 个位置；单个完整范围最多约 56 万次方块检查。玩家分散在不同农场时去重帮助有限。预算只能在工作项之间检查，不能中断这个原子步骤。公共区域恢复还可能读取未加载位置。

建议：改为区块/位置游标工作项，保持跨 tick 去重和区块租约；后续可维护杂草位置索引，减少空扫。

### 7. [P2] 结果发布绕过预算，READY 发布漏计日结耗时

位置：`src/main/java/com/stardew/craft/time/settlement/DailySettlementCoordinator.java:141`，相关 `DailySettlementReadyPublisher.java:225`。

个人计算虽有分帧，结果准备和发送仍一次遍历全员；`notifyPlayerResults` 的成本没有加入预算消耗，`notifyReady` 在 `metrics.endTick` 之后执行。人数和出货数据增长时，发布阶段会形成新的同步峰值，且 READY 成本不体现在日结 tick 指标里。

建议：把结果准备、持久化和发送按玩家拆进预算工作项，只保留轻量的全局 READY 切换；计时覆盖最终发布。

## 其他直接相关问题

### 8. [P2] AFK 投票者只退出分母，仍留在分子

位置：`src/main/java/com/stardew/craft/event/SleepVoteTracker.java:286`，相关 `:302`。

三人 A/B/C，A 投票后等待至 AFK，B/C 活跃；B 随后投票。当前算法可能得到 2 票/2 活跃人数，在 100% 规则下跳过尚未同意的 C。分子和分母应使用同一资格集合，已投票且仍有效的睡眠者不能仅从分母消失。

### 9. [P2] 晕倒动画延迟期间撤票，不会取消已排定的推进

位置：`src/main/java/com/stardew/craft/event/DimensionEventHandler.java:344`。

一人处于晕倒动画，另一人确认睡眠达到门槛后会排定延迟推进。屏障尚未开始时撤票仍可成功，但延迟回调不复核票数，直接清票并推进。建议为可取消的睡眠推进保存版本/令牌并在回调重验，或明确锁定该阶段；2AM 强制推进应独立处理。

### 10. [P2] 接近主世界巫师塔时仍可能触发整座结构同步扫描与加载

位置：`src/main/java/com/stardew/craft/interior/WizardTowerStructureHandler.java:149`，相关 `:163`。

发现附近结构后，为寻找已有传送门和最低门方块，连续遍历结构包围盒。与地形转换不同，这两个扫描没有已加载区块检查，跨区块结构可能触发同步加载；每次重启清空缓存又会重做。建议按结构去重排队、分批扫描已加载区块，并持久保存版本化入口位置。

## 建议实施顺序

先修 1–4 的掉线恢复、区块发送和睡眠状态，再统一传送 ticket 队列（5），随后分帧换季与结果发布（6–7）。睡眠重构时一并修复 8–9。实际性能收益应使用多人同时入塔/返回、分散农场换季、结算中掉线重连的实服场景采样验证。
