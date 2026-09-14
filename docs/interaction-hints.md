# Crosshair interaction hints

The crosshair-side hint is resolved on the server. It describes the action
available on the block or entity currently under the player's crosshair;
it does not perform the interaction.

## Built-in semantics

| Type | Typical use |
| --- | --- |
| `GRAB` | Open, operate, pet, sit, travel, or collect a special object |
| `GIFT` | Give a gift, quest item, or festival gift to an NPC |
| `TALK` | Talk to an NPC or invite another player |
| `LOOK` | Read a map-authored text point or lost book, or open a pet's management page |
| `HARVEST` | Harvest a mature crop, forage, fruit, berry, produce, or machine output |

`TALK` and `LOOK` support a `done` state. Pending hints use the supplied
floating icon; completed hints use the supplied `_done` texture, stay still,
and render at reduced opacity. The other semantic types are static.

## Pets

Pet hints use the existing crosshair-side sprites and server query; they do
not add a separate HUD or synchronize a second copy of petting state.
For a member of the pet's farm, ordinary right-click uses `GRAB` before that
player has petted it today. The first interaction pets it; subsequent
interactions use `LOOK` and open management with that pet selected.
Opening the menu does not repeat friendship, gifts, or petting feedback.

The daily record is per player: another member still gets `GRAB` until
they pet it, and the next day restores `GRAB`. Sneak-right-click continues
to open management without using the daily petting opportunity. A held
hat for a cat/dog or Butterfly Powder takes precedence and uses `GRAB`;
powder still requires confirmation. Missing records and players outside
the pet's farm receive no built-in pet hint.

These hints remain read-only, respect add-on overrides and the `none` tag,
and refresh through the existing query interval (10 client ticks).

2026-09-14 verification: `./gradlew classes runGameTestServer
-PgameTestNamespaces=stardewcraft_pets` passed all 11 pet tests. The new
interaction regression covers all 12 breeds, the per-player and next-day
hint transitions, first petting versus subsequent menu payloads (including
the selected pet ID and codec round-trip), permissions, sneak access, hats,
and powder confirmation. The existing HUD renderer and GUI coordinates
are unchanged; no client was launched for visual acceptance.

## Data-pack tags

A data pack can assign a static semantic to block or entity types:

- `stardewcraft:interaction_hints/grab`
- `stardewcraft:interaction_hints/gift`
- `stardewcraft:interaction_hints/talk`
- `stardewcraft:interaction_hints/look`
- `stardewcraft:interaction_hints/harvest`
- `stardewcraft:interaction_hints/none`

Use the tag under `tags/block` for blocks and `tags/entity_type` for entity
types. `none` suppresses built-in resolution. Static tags do not manufacture
per-player completion state; use the API for a dynamic `TALK` or `LOOK`
result.

## Add-on API

Register a server-side, mutation-free provider during add-on setup:

The extension surface consists of `StardewInteractionHints`,
`StardewInteractionHintProvider`, `StardewInteractionHintContext`,
`StardewInteractionHintDecision`, `StardewInteractionHint`, and
`StardewInteractionHintType`.

```java
StardewInteractionHints.register(
    ResourceLocation.fromNamespaceAndPath("example", "notice_board"),
    100,
    context -> {
        if (!isNoticeBoard(context)) {
            return StardewInteractionHintDecision.pass();
        }
        boolean read = hasRead(context.player());
        return StardewInteractionHintDecision.show(
            new StardewInteractionHint(
                StardewInteractionHintType.LOOK,
                read,
                ResourceLocation.fromNamespaceAndPath(
                    "example", "notice_board")));
    });
```

Providers run by descending priority and then by identifier. A provider may
return:

- `pass()` to allow later providers and built-in resolution;
- `hide()` to deliberately suppress the hint;
- `show(hint)` to supply the semantic, completion state, and stable identity.

Provider code must only inspect state. Opening screens, consuming items,
advancing quests, or changing friendship belongs in the real interaction
handler.

## Resolution order

1. Add-on providers.
2. The `none` data-pack tag.
3. Precise built-in probes for stateful project systems.
4. Static semantic data-pack tags.
5. A menu hint when the current block state exposes a menu provider.

This order lets an add-on describe dynamic behavior precisely while still
allowing data packs to cover simple content without Java code. The resolver
does not infer an action merely because a block class declares a right-click
method: forwarding and conditional handlers may still return `PASS`.
