param(
    [string]$OutputDir = "build/mahjongcraft-itemmodel-pack",
    [string]$OutputZip = "build/mahjongcraft-itemmodel-pack-1.21.11.zip"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$sourceTexturesDir = Join-Path $root "legacy/fabric/src/main/resources/assets/mahjongcraft/textures/item/mahjong_tile"

if (-not (Test-Path $sourceTexturesDir)) {
    throw "Texture source not found: $sourceTexturesDir"
}

$packRoot = Join-Path $root $OutputDir
$itemsDir = Join-Path $packRoot "assets/mahjongcraft/items/tile"
$modelsDir = Join-Path $packRoot "assets/mahjongcraft/models/item/tile"
$texturesDir = Join-Path $packRoot "assets/mahjongcraft/textures/item/tile"
$vanillaModelsDir = Join-Path $packRoot "assets/minecraft/models/item"

Remove-Item -Recurse -Force $packRoot -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $itemsDir | Out-Null
New-Item -ItemType Directory -Force -Path $modelsDir | Out-Null
New-Item -ItemType Directory -Force -Path $texturesDir | Out-Null
New-Item -ItemType Directory -Force -Path $vanillaModelsDir | Out-Null

$tileKeys = @(
    "m1","m2","m3","m4","m5","m5_red","m6","m7","m8","m9",
    "p1","p2","p3","p4","p5","p5_red","p6","p7","p8","p9",
    "s1","s2","s3","s4","s5","s5_red","s6","s7","s8","s9",
    "east","south","west","north","white_dragon","green_dragon","red_dragon"
)

$displayScale = 0.72

foreach ($key in $tileKeys) {
    $src = Join-Path $sourceTexturesDir ("mahjong_tile_" + $key + ".png")
    if (-not (Test-Path $src)) {
        throw "Missing tile texture: $src"
    }

    $dst = Join-Path $texturesDir ($key + ".png")
    Copy-Item $src $dst -Force

    $rawModel = @"
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "mahjongcraft:item/tile/$key"
  },
  "display": {
    "gui": { "scale": [$displayScale, $displayScale, $displayScale] },
    "ground": { "scale": [$displayScale, $displayScale, $displayScale] },
    "fixed": { "scale": [$displayScale, $displayScale, $displayScale] },
    "thirdperson_righthand": { "scale": [$displayScale, $displayScale, $displayScale] },
    "thirdperson_lefthand": { "scale": [$displayScale, $displayScale, $displayScale] },
    "firstperson_righthand": { "scale": [$displayScale, $displayScale, $displayScale] },
    "firstperson_lefthand": { "scale": [$displayScale, $displayScale, $displayScale] },
    "head": { "scale": [$displayScale, $displayScale, $displayScale] }
  }
}
"@
    Set-Content -Path (Join-Path $modelsDir ($key + ".json")) -Value $rawModel -Encoding UTF8

    $itemDefinition = @"
{
  "model": {
    "type": "minecraft:model",
    "model": "mahjongcraft:item/tile/$key"
  }
}
"@
    Set-Content -Path (Join-Path $itemsDir ($key + ".json")) -Value $itemDefinition -Encoding UTF8
}

# Legacy fallback: custom_model_data overrides for minecraft:paper
$paperOverrides = New-Object System.Collections.Generic.List[string]
$index = 0
foreach ($key in $tileKeys) {
    $modelPath = "mahjongcraft:item/tile/$key"
    $cmd = 1000 + $index
    $paperOverrides.Add(('    {{ "predicate": {{ "custom_model_data": {0} }}, "model": "{1}" }}' -f $cmd, $modelPath))
    $index++
}
$paperModel = @"
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/paper"
  },
  "overrides": [
$(($paperOverrides -join ",`n"))
  ]
}
"@
Set-Content -Path (Join-Path $vanillaModelsDir "paper.json") -Value $paperModel -Encoding UTF8

$packMeta = @"
{
  "pack": {
    "pack_format": 75,
    "min_format": 75,
    "max_format": 75,
    "description": "MahjongCraft item_model resource pack"
  }
}
"@
Set-Content -Path (Join-Path $packRoot "pack.mcmeta") -Value $packMeta -Encoding UTF8

if ($OutputZip -and $OutputZip.Trim().Length -gt 0) {
    $zipPath = Join-Path $root $OutputZip
    if (Test-Path $zipPath) {
        Remove-Item $zipPath -Force
    }
    Compress-Archive -Path (Join-Path $packRoot "*") -DestinationPath $zipPath -Force
    Write-Host "Resource pack zip generated at: $zipPath"
} else {
    Write-Host "Resource pack generated at: $packRoot"
}
