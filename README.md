# MahjongCraft Paper (Target: Paper 1.21.11)

This repository is being rewritten from a Fabric mod into a Paper plugin.
Current state is a server-playable foundation with texture-pack + ItemDisplay tile rendering.

## Implemented now

- 4-player table flow: create, join, start, turn-based draw/discard
- ItemDisplay board rendering for discards/melds
- Win detection:
- Standard hand (4 melds + 1 pair)
- Seven pairs
- Thirteen orphans
- Actions:
- `/mahjong riichi`
- `/mahjong tsumo`
- `/mahjong ron`
- `/mahjong pon`
- `/mahjong kan`
- `/mahjong chii <tileA> <tileB>`
- `/mahjong pass`
- Claim priority:
- ron > pon/kan > chii
- Scoring:
- han/fu evaluation (simplified yaku set)
- riichi pot, honba, dealer continuation/rotation
- Hand GUI with `item_model` resource-pack rendering

## Build

```powershell
./gradlew build
```

Output jar:

- `build/libs/mahjongcraft-paper-<version>.jar`

## Commands

- `/mahjong create`
- `/mahjong join <tableId>`
- `/mahjong leave`
- `/mahjong start`
- `/mahjong hand`
- `/mahjong claim`
- `/mahjong gui`
- `/mahjong discard <handIndex>`
- `/mahjong pay <amount>`
- `/mahjong riichi`
- `/mahjong tsumo`
- `/mahjong ron`
- `/mahjong pon`
- `/mahjong kan`
- `/mahjong chii <tileA> <tileB>`
- `/mahjong pass`
- `/mahjong status`

## Generate item_model resource pack

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\generate-itemmodel-resourcepack.ps1
```

Default output:

- `build/mahjongcraft-itemmodel-pack`

Generated files include:

- `assets/mahjongcraft/items/tile/*.json`
- `assets/mahjongcraft/models/item/tile/*.json`
- `assets/mahjongcraft/textures/item/tile/*.png`

## Notes

- This is a Paper plugin rewrite, not a Fabric client mod port.
- Active plugin source set:
- `src/paper/java`
- `src/paper/resources`
- Old Fabric assets/source are archived under:
- `legacy/fabric/src/main`
- Optional integrations:
- `PacketEvents` (packet layer bootstrap)
- `Vault` + any economy plugin (for `/mahjong pay` pot betting and winner payout)
- Mahjong hand analysis library:
- `io.github.ssttkkl:mahjong-utils-jvm`
- Fully migrated for shanten/hora/han/fu/yaku/point calculation.
- Message system:
- MiniMessage format for all outgoing chat messages
- i18n bundles: `src/paper/resources/lang/messages_en_us.properties`, `src/paper/resources/lang/messages_zh_cn.properties`
