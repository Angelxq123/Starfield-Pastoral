# Starfield ItemsAdder Damage Blocker

这是一个给 Youer/Paper 服务端使用的小型兼容插件。

它只处理真实发生的、被 ItemsAdder 取消的玩家 `EntityDamageByEntityEvent`。
第一次发现拦截时会打印具体监听器类、`@EventHandler` 方法和优先级；随后将每个
ItemsAdder 伤害监听器替换成追踪包装器。包装器会直接记录哪个监听器把
`cancelled` 从 `false` 改成 `true`，立即恢复攻击，并注销插件自己的探针；不会关闭：

- ItemsAdder 资源包发送
- 自定义物品和模型
- 字体、HUD 和其他图形功能

构建：

```powershell
..\..\gradlew.bat -p compatibility/itemsadder-damage-blocker clean build
```

产物：

`build/libs/itemsadder-damage-blocker-1.0.3.jar`

将产物放到服务端 `plugins` 目录，完整重启服务器。启动日志应出现：

```text
Runtime ItemsAdder damage interception is enabled.
```

插件还会兼容 ItemsAdder 4.0.14 的箱子关闭序列化问题：当 Youer 对一个箱子位置返回
非 `TileState` 状态时，只跳过 ItemsAdder 对这个异常位置的保存，不取消关箱，也不影响
正常箱子、玩家背包和实体容器。

```text
Installed ItemsAdder inventory-close TileState guard: 1 listener(s).
Skipped ItemsAdder inventory-close serialization: handler=itemsadder.m.k#a(InventoryCloseEvent)@NORMAL, player=yaaya, inventory=CHEST, location=Vector{...}, reason=TileState is null
```

第一次确认拦截时会出现：

```text
ItemsAdder blocked EntityDamageByEntityEvent: player=yaaya, target=minecraft:pig#7074/..., cause=ENTITY_ATTACK, damage=105.0, finalDamage=105.0, weapon=..., listeners=[itemsadder.m.o#onEntityDamage(EntityDamageByEntityEvent)@HIGHEST, itemsadder.m.ml#protect(EntityDamageByEntityEvent)@NORMAL]
ItemsAdder cancellation overridden for this hit; tracing handlers next tick.
Installed direct ItemsAdder damage handler tracing: 12 listener(s); damage probe unregistered.
```

之后如果某个 ItemsAdder 方法实际取消事件，会出现：

```text
ItemsAdder handler canceled EntityDamageByEntityEvent: handler=itemsadder.m.o#onEntityDamage(EntityDamageByEntityEvent)@HIGHEST, player=yaaya, target=minecraft:pig#7074/..., cause=ENTITY_ATTACK, damage=105.0, finalDamage=105.0
ItemsAdder handler cancellation overridden; hit restored.
```
