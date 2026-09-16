# 新建筑与动物运行时：附属接入契约

适用当前工作区重置后的系统。旧存档迁移不在本次范围；原公开扩展入口继续参与新账本。

## 动物定义与购买

- 首选 `data/<namespace>/stardewcraft/farm_animals/*.json`。完整 JSON 决定实体、住宅族、
  最低等级、成熟、生产、职业、吃草、饼干、售价、解锁条件和购买随机变体；日结使用
  同一个 `AnimalDayReducer`，界面从服务端快照读取。
- `animal_type_id` 推荐 `myaddon:goose`。内置短 ID 保持不变；`cow` 对应账本的
  `white_cow`。未知但合法的 ID 和附属 NBT 保留，缺失定义期间停止其默认行为，恢复后继续。
- JSON `purchase_price` 是 SDV 原始数据值，商店售价是其两倍；负一不可购买。
  Java `StardewAnimalShopEntry.price` 已经是最终商店价，不再乘二。
- 没有完整 JSON 时，`StardewAnimalTypes`、`StardewAnimalShopEntries` 仍可接入。
  不套用鸡的照料/生产参数；应由 `StardewAnimalDailyHandlers` 承担状态推进。
- 定义的实体类型必须能创建 `BaseCoopAnimalEntity`。该实体保留自身渲染器、模型及动画，
  新账本负责住所、导航、交互和投影，关闭旧账本 AI，世界存档不重复保存投影。
- 服务端购买重新验证会话、数据修订、解锁条件、真实价格、最终随机品种、容量及安全落点。
  返回 UI 的 `AllowedSpecies` 与这些入住规则一致，不能通过客户端伪造价格或资格。

## 生命周期、产物与附属状态

- JSON `egg_items` 首先匹配；之后调用 `StardewAnimalIncubation` 的有序 resolver。
  `artisan` 的 `incubator` 配方时长优先；无配方则使用 JSON `incubation_time`。
  没有有效正数时长、住宅不匹配或实体不可投影时不消耗输入。孵化领取再次检查权限与容量。
- `StardewAnimalDailyHandlers` 在每日开始阶段之后、默认生产之前执行。
  `SKIP_DEFAULT_PRODUCTION` 跳过默认产物；完整定义的基本日结继续执行。
- `StardewAnimalReproductionRules` 在成年、玩家开关、住宅能力和容量检查后执行。
  `StardewTruffleFoundHandlers` 进入新挖掘路径；替换结果会阻止默认产物。
  `StardewAnimalQueryDefinitions` 决定出售价格与繁殖开关的可见资格。
- `produce_stats` 的物品/标签过滤保持生效。日结与自动收集使用统计最终值日志，
  恢复不会重复计数；工具收取使用实际获得的产物计数。
- 完整命名空间产物及 ItemStack 组件、数量均保存。收集器、玩家收取、搬家和日志恢复
  保留自定义名、品质等组件；无法解析的产物保留记录，不当作空物品收走。
- `StardewAnimalPersistentData` 支持 UUID 和不透明 long 句柄；新动物句柄为负数。
  优先使用 `context.entityId()` 或上下文状态方法。禁止将句柄交给旧内部账本。
  每日回调对当前动物的 facade 写入和上下文写入共享暂存记录，和产物一起提交。
- 回调内自行发聊天、生成任意世界实体或写其他模组文件不属于可重放事务；需要事务语义时，
  使用上下文提供的产物/状态操作。恢复已准备好的批次不会重跑回调。

## Data Map 优先级

`StardewAgricultureDataApi` 的有序 provider 优先于对应 Data Map。

- `BUILDING_DATA.capacity` 覆盖当前等级容量；非空 `accepted_animals` 按真实配置的
  EntityType ID 进一步限制入住。购买、孵化、搬家、出生及管理 UI 共用此结果。
- `ANIMAL_DATA` 在动物的实体投影已加载时覆盖普通产物和生产间隔。其购买价、成熟天数、
  建筑类型仍是描述性字段；无法在购买前安全调用依赖实体的 provider。
  内置动物虽共用投影类，Data Map 仍按定义的 EntityType 查找。
  要使离线日结与在线行为完全一致，请使用完整 JSON，而不是依赖已加载实体的 provider。
- 住宅容量、自动喂食、怀孕资格以及设施数量继续读取
  `stardewcraft/animal_buildings`。其中旧封闭检查、门与空气体积、上下扫描范围不恢复：
  自建空间由 `farm_building_prefabs.self_build` 固定，只按固定范围内设施判定，不要求屋顶；设施能力仍由附属模组接口提供。

## 建筑族

代码附属调用 `StardewBuildingFamilies.register(family, binding)`。Binding 明确提供：

1. 独立管理器方块 Supplier，其物品使用 `managerItem(family, properties)` 工厂；
2. 蓝图 Supplier，使用 `blueprintItem` 工厂；
3. 各升级目标等级的许可 Supplier，使用 `upgradeItem` 工厂；
4. 各等级 `Display(nameKey, texture, width, height)`，在客户端也注册相同素材信息。

仍以 `StardewBuildingBlueprints` 或建筑目录数据文件登记商品；初始商品 ID 必须等于
family，升级商品的 result_item 指向相应许可。builder 可以是任意注册目录，不限罗宾；
附属在调用目录打开 API 前负责自己的 NPC/交互访问规则。
此处 builder 表示售卖入口；本系统施工表现仍使用罗宾施工替身。

`data/<namespace>/farm_building_prefabs/<path>.json` 的 ID 即 family。结构位于
`data/<namespace>/structure/<path>.nbt`，格式沿用本体 prefab 数据：完整 air cells、无实体、
manager 坐标、animal_spawn、相对 anchor 的 bounds、reservation、连续 tiers 与 self_build。
所有 tier 必须包含自己的管理器，且全部位于共同预留空间内。购买前验证所有结构与许可，
错误绑定不会退回鸡舍蓝图。自建范围 radius 0–16、height 1–32，升级不改变该空间。

等级数从数据决定，允许单级及四级以上；单级预览只有一个外框。动态等级画像使用统一
目录画法，不用命名空间判断是否可显示。多级预览、施工、原生部件保护、移动与拆除仍走
公共生命周期。每种待升级模板需要有效正面施工标志位置和室内施工站位。
缺失建筑族或已用等级被删除时暂停操作并保护预留区，保留记录，避免错误升级/拆除。

筒仓和鱼塘的预制资源仍按用户决定暂缓，本次不伪造模板或开放不可用的购买选项。

## 设施扩展

`StardewAnimalFacilities.register(id, priority, provider)` 按优先级降序、注册 ID 排序。
不负责某方块时返回 null；同一个位置第一个非 null 适配器负责全部角色。

- Role：TROUGH、AUTOMATIC_TROUGH、HOPPER、INCUBATOR、AUTO_PETTER、COLLECTOR、HEATER。
  `units(role)` 返回该逻辑设施贡献的数量；多方块设施只能由主方块贡献。
  自动槽同时满足普通喂食位需求，不要重复报告两种角色。
- `hay()` 返回实际干草份数；自动喂食最多填至自动槽提供的份数并受住宅容量限制。
- `snapshot`、`withHay`、`withInventory` 必须是纯计划，不改世界。
  `apply` 必须将状态设为计划中的最终值，重复应用效果相同，不能再次减一或追加库存。
- 收集器返回槽位列表，可覆盖 `slotLimit` 和 `canInsert`；放不下完整产物时不移除产物。
- 升级同类型方块保留运行时 NBT；不同方块类型需用 `registerUpgrade` 声明转换。
  转换在世界修改前对副本执行。没有可用目标时拒绝升级，保留原设施数据。
- 角色适配不自动实现附属机器的孵化计时、交互和渲染；这些仍由该机器负责。

新入口保持 experimental，不因一次编译或测试通过就提升为稳定 API。签名门禁之外，
`AnimalAddonRuntimeGameTests` 使用外部命名空间、公开 API、实际商店/投影/账本/设施流程
验证行为；它不依赖本地源文件、tmp 资产或未追踪测试代码。
