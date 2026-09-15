# HUD and daily-info addon API

This API supports UI Info Suite-style overlays without reading StardewCraft's private HUD classes,
player NBT or server singletons. It is included in the next build, not the previously published JAR.
The new `api.v1.client` types are **experimental** under the repository's maturity policy; importing
from `api.v1` does not yet promise a frozen binary contract. This document describes the implemented
contract. Server and client must run the matching new StardewCraft version (payload protocol 26).

## Festival names

`StardewFestivalClientSessionSnapshot.displayName()` returns a Minecraft `Component`:

```java
StardewFestivalClientSessions.find(festivalId).ifPresent(session -> {
    Component name = session.displayName(); // Pass directly to your GUI or tooltip.
    String plainName = name.getString();    // If your overlay needs a String.
});
```

Built-in festival names follow the client's language using the existing calendar translations.
Addon festivals use `display_name` from the synchronized festival definition. If the definition
has not arrived or was removed, the namespaced festival ID is the fallback. Resolve the name again
after a definition reload; do not permanently cache the plain string across language changes.
This addition keeps the existing snapshot constructor and session payload format unchanged.

## Daily information

Call `StardewClientDailyInfo.current()` on the client. It returns
`Optional<StardewDailyInfoSnapshot>`; empty means no server snapshot has arrived, not neutral luck,
no birthdays or sunny weather. The cache clears on login/logout. Do not persist it between worlds.

The server checks once per 20 server ticks, builds shared calendar information once per check,
and sends a small replacement payload **only when information changes**. Initial login, tool
purchase/pickup, learning a recipe or watching TV, day changes, weather changes and birthday data reloads appear within that interval
(about one second at 20 TPS). No addon request packets, per-frame polling of the server, or full
player-NBT download is needed. The existing gameplay luck initializer can issue its normal player
sync when today's luck is first rolled; the addon path does not roll a second independent value.

| Requested indicator | Snapshot access | Meaning |
| --- | --- | --- |
| Today's luck | `dailyLuck()`, `luckLevel()` | Local player's authoritative daily value, excluding temporary luck buffs. TV thresholds; `ZERO` distinguishes the exact-zero fortune. |
| Tomorrow's weather | `tomorrowWeather()` | Valley forecast, including when the player is in the mines or another dimension. `Rain` and `Storm` are separate values. |
| Berry season | `berrySeason()` | `NONE`, `SALMONBERRY`, `BLACKBERRY`; uses the actual bush harvesting calendar. Season does not guarantee an individual bush still has berries. |
| Bookseller | `booksellerToday()` | The server world's scheduled visit today, using its seeded schedule. |
| Clint's upgrade | `toolUpgrade()` | Optional `StardewToolUpgradeSnapshot`: result item ID, overnight settlements remaining, estimated ready date and `readyForPickup()`. Empty after collection or with no order. |
| Birthdays | `birthdayNpcIds()` | All birthdays today, including multiple NPCs on one day and namespaced addon NPCs in the birthday data pack. |
| Queen of Sauce | `queenOfSauce()` | Optional `StardewQueenOfSauceSnapshot`: today’s recipe ID, rerun flag, whether this player knows the recipe, and whether they watched today. Empty when the cooking channel is off air. |
| Traveling Cart | `travelingCartToday()` | Forest visit day, using the merchant's actual schedule. Does not mean the shop is currently open or its entity/chunk is loaded. Night Market visits are not forest visits. |

`StardewCalendarDate` has year >= 1, season 0/1/2/3 (spring/summer/fall/winter), day 1–28.
`plusDays` crosses seasons and years. `date()` labels the snapshot, which can lag the clock by one
sync interval. `expectedReadyDate()` is an estimate from today's date and remaining overnight
settlements, not a real-world timestamp; when ready it reports today. Offline time/admin date
changes may differ from actually processing a player's overnight settlement.

Weather values currently include `Sun`, `Rain`, `Storm`, `Snow`, `WindSpring`, `WindFall`, `Festival`.
Keep an unknown-value fallback for future weather types. The API deliberately sends the server
forecast rather than exposing a world seed or guessing from local time.

### Queen of Sauce

```java
StardewClientDailyInfo.current().ifPresent(info -> {
    info.queenOfSauce().ifPresent(show -> {
        boolean showReminder = show.canLearnRecipe();
        String recipeId = show.recipeId(); // TV cooking unlock ID, e.g. "stir_fry"; not an item ID
        boolean rerun = show.rerun();
        // Draw your icon/tooltip using these values.
    });
});
```

There are two distinct empty states: `current().isEmpty()` means not synchronized, whereas an
empty `queenOfSauce()` on a received snapshot means no cooking broadcast today. Sundays air a
regular episode; Wednesdays rerun an episode after the first week. The two-year schedule, missing
recipe preference for reruns, and already-watched recipe pinning come from the same selector as
the television. Reruns are personal: do not share one player's result with other players.

`recipeKnown()` reports recipe ownership, including learning it elsewhere. `watchedToday()` reports
watching this day's cooking program; knowing a recipe alone does not imply it was watched today.
`canLearnRecipe()` is true only when both flags are false. Reading this API never marks a show as
watched, unlocks a recipe or opens the TV. After watching, the snapshot retains today's chosen
recipe rather than selecting another unknown recipe. A later off-air day clears the optional show.
Watching/learning changes become visible within the normal 20-tick sync interval.

The previous `StardewDailyInfoSnapshot` constructor remains available for addon-created snapshots;
it supplies empty cooking data. The network always uses the complete new snapshot. This addition
remains experimental, like the other daily-info types, and requires matching client/server builds.

`playerId()` identifies the recipient. Each player receives only their own luck/tool order/cooking broadcast state.
Snapshots, tool records and birthday lists are immutable. To draw the upgraded tool, construct an
`ItemStack` from `resultItemId()` and use `GuiGraphics.renderItem`; the record contains the target
item type, not a mutable inventory stack. Resolve birthday names/portraits through the existing
`StardewNpcDisplays.resolve(npcId)` / `StardewNpcDisplay` API and its translation key. Do not hardcode
English NPC names. This information is calendar data; it does not filter NPCs by whether the player
has met/unlocked them.

## Follow the main HUD

Subscribe to `StardewHudRenderEvent` on the **NeoForge game bus**, in a `Dist.CLIENT`-only class.
The event fires after the main date/time/money HUD and attached currency have drawn, with the pose
restored to Minecraft GUI coordinates. It does not fire for F1-hidden HUDs, spectators, unsupported
dimensions, the HUD layout editor, or festival states that hide the main HUD. Existing festival
exceptions which keep its currency visible are respected. It is not a final-after-every-other-mod
render hook.

Use `event.hud()` for the exact `StardewHudSnapshot` used by this render. Outside the event,
`StardewClientHud.mainHud()` returns the current geometry and `visible()` flag, or empty without a
player/world. Both the renderer and query share the visibility predicate. Read it every frame so
window resize, GUI scale, dragging, anchors and configured HUD scale are reflected immediately.

- `screenWidth()` / `screenHeight()` are Minecraft GUI dimensions.
- `allocatedBounds()` is the layout editor's reserved main group, including space below the clock.
  It is **not** the visible background height and is not a union of every independent HUD overlay.
- `clockBounds()` is the date/time/weather panel's actual scaled rectangle. Money extends below it.
- `scale()` converts the HUD's native sprite pixels to Minecraft GUI units. Bounds are already
  scaled; do not multiply them by `Window.getGuiScale()` or `StardewGuiViewport.REFERENCE_SCALE`.
- `StardewHudSnapshot.Bounds.right()` / `bottom()` give screen edges directly. Pick an available
  side of the HUD and clamp your own overlay to the screen; the HUD may be dragged to any edge.

Use native 16×16 ItemStack rendering for actual items. Push/pop your own pose, balance scissor
operations, and never retain the event's `GuiGraphics` after the callback. These are HUD coordinates,
not the design-canvas coordinates used by StardewCraft's full-screen menus.

The compilable example is
[`ExampleDailyInfoHud.java`](../examples/stardewcraft-addon/src/main/java/com/example/stardewaddon/ExampleDailyInfoHud.java).
It draws the current upgrade's actual item beside the main HUD, follows its scale, chooses another
side near screen edges and clamps to the viewport. It demonstrates alignment, not a complete UI Info
Suite addon. `queenOfSauceToLearn()` also demonstrates a read-only cooking reminder filter; the eight indicators' layout, icons and tooltips remain the addon author's choice.

## Verification scope

The dedicated headless `stardewcraft_daily_info` GameTests cover packet round trips for distinct
players, immutable/replaced cache contents and clearing, actual upgrade-state extraction and pickup,
year-boundary estimates, multi-NPC/addon birthdays and TV luck thresholds. Cooking checks cover
off-air days, the first Wednesday, Sunday/two-year boundaries, distinct per-player reruns, exact TV
payload agreement, read-only queries, known/watched flags and watched-recipe pinning. These do not claim a
live two-client multiplayer session or visual rendering validation. No extra game client is required.

Local headless geometry checks also passed for GUI scales 1–6, 25/100/150/200% HUD scaling and a
1921×1081 framebuffer, including scaled clock bounds and hidden-state snapshots. These developer
checks live in ignored `src/test` and are not release evidence. The example compiled against the
current main classes without building a JAR. API classification, maturity review and existing binary
compatibility checks passed. A live client rendering/multiplayer session has not been run.
