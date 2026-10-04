Automate Alex's Mobs' Transmutation Table without clicking.

Open the table, mark the items you want, and the mod handles the rest: it keeps transmuteing until one of the three candidates is an item you marked, then stops without consuming it.

![Transmutator Automator](https://raw.githubusercontent.com/leyuezhida/transmutator-automator/main/publish/icon-512x512.png)

## Why it transmutes continuously

Most automation for this table only clicks when the target is already showing. That approach quietly makes things worse.

The table's weights are cumulative, and removing an item costs `log10(count)^4`:

| Items consumed | Weight lost |
|---|---|
| 1 | **0** |
| 10 | 4 |
| 100 | 8 |

Logarithms climb fast. When only the target is clicked, whichever items happen to be consumed have their weights pressed down every round — so the target becomes *rarer*, not more common.

This mod transmutes continuously instead, which keeps the amount consumed under your control and lets the target actually come up.

It also avoids candidates that would grow the stack. The server computes `newCount = floor(count * newMax / oldMax)`, so a higher stack limit multiplies what you hold: one snowball (limit 16) can become four sticks (limit 64). Every such click inflates the weight penalty, so candidates whose stack limit exceeds the current item's are skipped when another option is available. If all three would grow it, the first is taken — a stalled loop produces nothing at all.

## Features

- **Keeps transmuteing** until a marked item appears, then stops without consuming it
- **Target picker** layered over the table, with search by Chinese or English name
- **Live candidate preview** — the panel shows the three current candidates and outlines your target in green, so you can see whether it will be consumed before it happens
- **Avoids stack-growing candidates** to keep the weight disturbance small
- **Pauses on low experience** and resumes by itself once you have enough, without losing state
- **34 localised strings** across English and Simplified Chinese, log messages included
- **Adjustable interval** from 1 to 20 ticks
- **No world writes** — nothing is persisted, removing the mod leaves nothing behind

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.22 or later |
| Alex's Mobs | 1.22 or later |

**Install on both client and server.** This mod is client-only, but Alex's Mobs is required on both sides.

## How to use

1. Open a Transmutation Table.
2. Press **+ Add** in the panel on the left and pick the items you want from the selector.
3. Put **one** item in the table.
4. Turn on the automation toggle.

The panel also shows the current interval and how many transmutations have run, plus why it stopped if it did.

### Commands

Commands are kept as a secondary path for scripts or when the panel is inconvenient:

```
/ta mark <item>       add a target
/ta unmark <item>     remove a target
/ta list              list current targets
/ta clear             clear all targets
/ta status            show status
/ta interval <ticks>  set the transmute interval
```

## Notes

Breaking is instantaneous, which keeps the behaviour predictable and avoids half-finished states.
The item in the slot is consumed — that is the table's own rule — so the loop stops on its own when nothing is left to transmute.
This mod reads Alex's Mobs internals, so a major update of that mod may require an update here.

## Credits

Licensed under the MIT License.
The icon is derived from Alex's Mobs' transmutation table render, used under MIT.
Thanks to Alex's Mobs and its authors.
