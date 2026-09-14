# 宠物附属 API（0.6.1，实验接口）

2026-09-14：宠物种类与品种改为公开注册表。本体只登记原有 5 猫、5 狗、2 乌龟；附属可独立登记新品种，并接入问卷、命名领取、管理、水碗、照顾与原生骨骼播放器。自定义宠物应在独立附属中提供注册和美术资源。

0.6.1 首次发布此接口，使用完整命名空间 ID 和当前请求格式，不提供开发期间旧 `cat0` 等 ID 或旧请求格式的迁移。以下五个类型列入 `compatibility/experimental-api-v1.txt`，尚未承诺稳定二进制兼容。

## 公开入口与注册时机

| 类型 | 职责 |
| --- | --- |
| `StardewPets` | `registerSpecies`、`registerBreed`；查询单个定义或不可变、有序的定义列表 |
| `StardewPetSpeciesDefinition` | 碰撞宽高、戴帽能力、上床概率、行为图、赠礼、声音 |
| `StardewPetBreedDefinition` | 品种 ID、所属种类、译文键、模型/纹理/图标、开局/购买资格、价格、步幅 |
| `StardewPetFeedback` | 行为起始音、按动画时间触发的声音与地面声、循环规则和抚摸回应重复延迟 |
| `StardewPetBehavior` | 不可变的动画时长表、状态、加权转移及姿态退出动作；包含 `State` 与 `Choice` |

在附属的公共 `@Mod` 构造阶段注册，客户端和专用服务器执行相同声明。先注册种类，再注册品种；也可以把新品种挂到已有种类。只换毛色或兼容外形时复用种类；新的行为、尺寸或声音使用新的种类。附属不需要引用本体 `PetVariant`、`PetService` 或继承实体。

ID 使用 `ResourceLocation.fromNamespaceAndPath("your_addon", "your_pet")`，例如 `raccoon_pet:classic`。名称是译文键，不从路径猜测。所有注册不可变，重复 ID、未注册种类及冻结后的注册会报错。本体原有品种保持展示顺序，其后按附属完整 ID 排序。`freeze()` 与 `fingerprint()` 用于本体加载和连接生命周期；附属完成注册后无需自行调用。

完整的独立工程见 [examples/pet-addon](../examples/pet-addon/README.md)。示例用公开接口创建新种类和品种，暂时复用橘猫资源来验证接入，不是浣熊模型。工程不属于本体 source set。

```sh
./gradlew classes
./gradlew -p examples/pet-addon classes
```

示例默认链接本体当前编译目录，日常验证不用构建 JAR。独立开发时可传 `-PstardewcraftJar=/absolute/path/to/matching-stardewcraft.jar`。客户端和服务端都安装匹配的附属与本体。

## 选择与运行规则

- `initial=true`：出现在开局问卷及初始宠物选择页，免费命名领取。新农场问卷按五列自动增加行并使用既有滚动区域；独立选择/购买页每页 12 项。
- `adoptable=true`：出现在后续领养购买页，服务端再次验证资格、解锁条件、空碗及 `price`。两个资格独立；给粉丝的初始宠物可设为 `initial=true, adoptable=false`。
- 种类的 `width/height` 单位为 Minecraft 方块，用于实体尺寸和安全生成检查。`walkStride/sprintStride` 是一次完整循环前进的模型单位数，16 模型单位等于一格；导航速度与客户端步态按这一约定匹配。
- 现有不可伤害/捕捉、每日抚摸、准星旁手形提示、抚摸后再右击管理、改名、碗绑定、好感及送走规则由共享运行时承接。帽子取决于 `wearsHat`，无需每个附属重新实现交互。
- `bedChance`、`giftChance` 为 0–1；`Gift(query,count,friendship,weight)` 的好感门槛为 0–1000。礼物支持现有物品 ID/限定物品 ID，以及本体已有的 `LOCATION_FISH`、`RANDOM_ITEMS` 形式；不是任意脚本回调。
- 声音 ID 指向已注册的 SoundEvent。`ambientSound` 是 `BARK` 指向的叫声，`contentSound` 是抚摸满意声音；`null` 静音。`voiceVolume/voicePitch` 分别控制音量与音高。品种可通过 `barkOverride` 替换叫声，并用品种 `voicePitch` 乘以种类音高。原有构造器仍可调用，新增参数默认不覆盖叫声、音高为 1。

问卷与管理请求传递完整命名空间 ID，名字允许冒号。领取、支付、每日抚摸及菜单会话由服务端验证，附属不能靠客户端界面绕过资格或重复领取。

## 模型与动画资源契约

品种声明完整资源路径，例如 `raccoon_pet:pet_native/classic.json.gz`、`raccoon_pet:textures/entity/pet/classic.png`、`raccoon_pet:textures/gui/pet/classic.png`。这些文件放在附属对应的 `assets/raccoon_pet/` 目录，不需要复制到本体命名空间。

模型为 GZIP 压缩的原生宠物 JSON，承载 `rig`、`clips`、`hat` 等字段；不是直接读取 `.bbmodel`。可参照本体 `pet_native/cat0.json.gz` 的结构和作者侧 `建模试作/宠物制作/全量交付/v3/tools/export-pet.js` 的原生导出。该作者脚本对现有模型有头骨/部件命名假设，新骨架导出时需核对锚点，运行时不依赖作者目录。

| 部分 | 必须满足 |
| --- | --- |
| `rig` | `version=1`；父骨骼先于子骨骼；局部位置、ZYX 旋转角度与逆绑定矩阵；所有数值有限 |
| 面与蒙皮 | 四边形，`texture=0`；顶点含模型坐标、归一化 UV、有效骨骼索引与正权重，权重和为 1；单张动物纹理 |
| `clips` | 名称到 `length/loop/tracks` 的映射；位置/旋转/缩放轨道；关键帧时间严格递增且在片段内；数值关键帧、线性插值或有意的保持 |
| 基础片段 | `idle`、`walk`、`blink`、`sleep_enter`、`sleep`、`sleep_exit`；行为图所引用的其他片段也必须存在 |
| 时长 | 模型片段与行为图声明的秒数一致，加载时误差容限为 `1e-5` |
| 戴帽 | `wearsHat=true` 时必须提供有效的 `hat.bone/position/scale`；不能把锚点留在世界原点 |
| 图标 | 单独的原生 16×16 PNG，界面整数放大；纹理与模型可由多个品种共享 |

实际纹理由品种的 `texture` 指定，模型 JSON 的同名字段不覆盖它。`species` 保留模型自身的种类标记。播放器在 0.12 秒的片段混合期间对所有种类（含附属）统一修正低于地面的表面，不再只对猫开启；完整片段仍须由作者保证接地，不能用该修正掩盖错误姿态。模型空间 Y=0 为脚底基准。

方块体型、统一像素密度、单块耳朵配弯曲骨骼和连续蒙皮均可保留。移动只有统一 `walk`；方向由三维导航控制。曲线可在制作时使用，导出前烘焙成足够密的线性关键帧；不能把原版二维帧序直接变成全身定格切换。

`State` 的字段语义：`clip` 是当前片段，`entry/exit` 可为空；`duration` 是秒数，负值表示无固定时限；`minDuration/maxDuration` 或 `minLoops/maxLoops` 非负时覆盖固定时长，循环次数优先。带 `entry` 的时长包含进入片段。`end/timeout/nearby/random` 是带权转移列表，`outside=true` 的选择在室内排除。`chance` 是原版每 60 Hz 帧的随机概率，运行时折算到 MC 更新频率。`sound` 为空则无起始音，`content` 选满意声音，`BARK` 选种类叫声或品种覆盖，其余非空值使用完整 SoundEvent ID。

行为图要求入口状态 `Walk`，`Sleep` 是运行时保留的睡眠目标。导航移动片段限 `walk/sprint`；`pounce` 可作非导航扑跃片段。`postureExits` 声明夜间切换前需要从坐姿/伏卧等退出的动作，避免直接跳到睡眠姿态。可先复制兼容的原有行为图，再依据新宠物的已制作动作调整；不要复制了猫行为却漏掉洗脸等所需片段。

## 声音与表情

`StardewPetFeedback(repeatContentTicks, states)` 可作为种类构造器的最后一个参数。旧构造器默认 `EMPTY`。`states` 的键必须是行为图中的状态名；`Track(start, length, loop, frames)` 的长度与 `Frame(time, cue, terrain)` 的时间单位均为秒，进入姿态也计入状态时间。例：猫 `Flop` 的趴下长度为 0.8 秒，在第 0.6 秒播放落地声，趴稳后不重复；狗喘气每 0.4 秒重复，抚摸后的第二声延迟 8 个 MC 刻。

`Cue(sound, voice, rangeFromBorder, range)` 区分动物叫声与动作音：叫声受 Minecraft 的“友好生物”音量控制，动作音受“方块”音量控制。普通走路声另遵循原版的背景音乐播放时静音规则，舔毛使用同一音频但不受这个额外规则影响。正数 `range` 保留原版按轴的附近格数限制；正数 `rangeFromBorder` 由客户端视锥扩展对应方块距离后判断，适配 3D 摄像机。声音仍有 Minecraft 空间衰减和 16 格服务端发送上限。发送与计时由服务端负责，客户端只做本地音量、可见范围和短延迟播放。

共享 emote 气泡使用已有原版图集：抚摸为 20（爱心），睡眠为 24，缺碗的次日反馈为 28。宠物不再发送原版 Minecraft 爱心粒子。

## 连接检查与验证边界

进入世界前，客户端与服务端比较完整种类、品种、行为和资源路径声明的确定性摘要；不一致会显示已翻译的明确提示并断开，避免问卷两端显示不同品种。摘要不校验 PNG/模型文件的实际字节内容，也不代表远程下载或热重载注册表。客户端资源重载时单独校验模型结构、动作及图标尺寸，报告缺失或不匹配资源。

本轮验证：核心编译和 12 语言检查；12 项宠物定向 GameTest，其中新增注册附属种类/品种、超过一页的选择、长 ID 与冒号名字编码、领取/购买防重放、资格/价格、尺寸、抚摸后菜单、当前记录保存读取、连接摘要处理和冻结校验；独立附属示例编译；`checkApiMaturityClassification` 和 `checkApiMaturityReview` 通过。专用服务器以全新隔离世界运行，达到 `Done`，未出现客户端类误加载。API 成熟度清单保留实验状态。专用服务器就绪后以 TERM 结束本次测试，所有维度保存完成；Gradle 对这次主动停止报告退出码 143，因此这里记录的是启动通过，不称 `runServer` 命令零退出。

GameTest 的连接验证调用真实编解码与处理器，未进行两个真实客户端的网络连接。未启动额外游戏客户端；GUI 各比例的画面、悬停/点击/裁剪一致性以及独立附属的游戏内模型仍需现场验收。橘猫示例只验证 API；实际浣熊工程、模型及动作现见 [独立浣熊附属](../addons/raccoon-pet/README.md)，其中另列本次原生姿态对照与资源检查。未打包安装 JAR。
