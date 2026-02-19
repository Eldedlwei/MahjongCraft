# MahjongCraft Mod -> Paper Plugin Parity Plan

## Reality Boundary

Exact 1:1 parity with a Fabric client mod is not physically possible on pure Paper because:

- Fabric client screens/widgets cannot run on vanilla clients.
- Mod entity renderers/models are client-side code and cannot be loaded by Paper.
- Custom packet channels used by mod UI logic are not available as-is.

What is achievable:

- Rules parity: full Riichi flow and scoring behavior.
- Gameplay parity: same turn logic, claims, settlement, round progression.
- Visual parity (approximate): resource-pack + ItemDisplay + packet-level per-player illusion.

This document defines "Paper parity" as feature-equivalent gameplay + high-fidelity visual approximation.

## Target Parity Definition

1. Rule Parity
- Full hand actions: draw/discard/chii/pon/ankan/minkan/kakan/riichi/ron/tsumo/kyuushu.
- Exhaustive draw variants and continuation rules.
- Furiten / chankan / rinshan / haitei / houtei / ippatsu logic.
- Full yaku/han/fu/point by mahjong-utils.

2. Match Flow Parity
- East/South game lengths, dealer continuation, honba, riichi sticks.
- Multiplayer reconnection/leave handling.
- Bot behavior parity (or explicitly documented reduced bot mode).

3. Visual Parity (Paper-compatible)
- Mahjong table scene rendered in world.
- Per-player concealed hand visibility via packet-level entity virtualization.
- River/meld/riichi markers/dora indicators layout parity.
- Score settlement and yaku settlement screens replaced by Adventure GUI/chat HUD equivalents.

4. Data/Config/Localization Parity
- Rule editor options parity.
- i18n parity for all user-facing text.
- Persistent match and table state compatibility where required.

## Current Status (Gap Summary)

- Implemented:
- Core table lifecycle, claims priority baseline, ItemDisplay board, Vault pot, MiniMessage + i18n, mahjong-utils integration.

- Missing for parity:
- Full claim variants (ankan/kakan/chankan edge cases).
- Full round-end reasons and draw-resolution rules.
- Complete mahjong-utils option mapping.
- Per-player hidden information rendering (packet-scoped).
- Rule editor UX parity with mod.
- Settlement presentation parity with mod screens.

## Execution Milestones

1. Engine Completion
- Replace remaining simplified flow branches with full Riichi state machine.
- Add full draw/end condition matrix.

2. Scoring Completion
- Drive all scoring and yaku display from mahjong-utils Hora result.
- Add deterministic test fixtures.

3. Visual Completion
- Introduce packet-scoped rendering channel (PacketEvents) for concealed hand privacy.
- Rebuild board layout transforms to match mod coordinates.

4. UX Completion
- Replace command-heavy interaction with inventory/Adventure-based action menus.
- Add rule editing and settlement views.

5. Validation
- Build parity test checklist against original mod behaviors.
- Record pass/fail matrix per feature.

## Acceptance Criteria

- No simplified fallback paths in gameplay/scoring engine.
- All parity checklist items marked pass, or explicitly documented as Paper platform limitation.
- Multiplayer session test for 4 players completes a full game without desync.

