# 多人日结一致性与离线追赶实施计划

## 文件职责

- `DailySettlementContext`、`StardewTimeManager`：冻结完整的结算维度在线玩家名单。
- `DailySettlementBarrier`、`DailySettlementReadyPublisher`：区分个人 READY 与仅日期同步 READY，并发布动态锁定受众。
- `DailySettlementEvents`、`OvernightReadyAckPayload`：处理晚登录、旧 pending 优先和严格 ACK。
- `PlayerDailySettlementService`：继续提供个人结算的持久化幂等阶段。
- `OfflineFarmCatchUpService`：拥有农场级分帧追赶任务、预算、逐日游标和生命周期。
- `FarmChunkManager`、`FarmDailyProcessHelper`：立即释放正常条目租约，仅保留释放失败项供根清理重试。
- `server-tps-baseline-protocol.md`：中文实服验证流程。

## 实施顺序

1. 在 `DailySettlementBarrierTest`、`DailySettlementLifecycleContractTest` 和 `DailySettlementReadyPublisherTest` 中增加失败测试，证明完整受众、动态晚登录、READY 类型和 ACK 规则。
2. 修改结算受众捕获、屏障和 READY 发布，使无农场在线玩家正常个人结算，真正晚加入者只做日期同步。
3. 增加重连测试，证明旧 pending 优先，确认后可加入仍活跃的当前屏障；晕倒 payload 只能由匹配日的真实 `PassOutResult` 生成一次。
4. 在农场测试中建立纯任务模型，覆盖按条目预算运行、每日提交 cursor、共享 owner 去重和失败恢复。
5. 新增 `OfflineFarmCatchUpService`，将登录同步调用替换为排队；在服务端 tick 推进，在日结活跃时暂停，并在停止事件清理。
6. 修改访问守卫，使离线追赶锁和日结锁共同阻止服务端移动、传送、交互、容器与普通 C2S payload。
7. 先写区块租约失败测试，再恢复条目级释放；释放异常留给根清理重试且不让业务条目重放。
8. 将性能基线协议完整翻译为中文，并记录新增追赶指标和推荐观察阈值。
9. 依次运行结算定向测试、农场定向测试、全部 `675+` 测试及 `gradlew build`，记录 JAR SHA-256 和剩余警告。

## 完成标准

- 星露谷结算维度内所有日结开始时在线玩家获得完整且仅一次的个人结算。
- 真正晚加入者不会获得未参与日期的奖励或晕倒惩罚，但在 READY 前不能操作星露谷世界。
- 离线追赶不再占用单个登录 tick，已提交日期不会重复生长。
- 正常条目租约在条目结束时释放，只有释放失败租约留到根清理。
- 全量测试和构建通过，工作树只包含本次有意修改。

