param(
    [string]$OutputDir = "build/mahjongcraft-itemmodel-pack"
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

Remove-Item -Recurse -Force $packRoot -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $itemsDir | Out-Null
New-Item -ItemType Directory -Force -Path $modelsDir | Out-Null
New-Item -ItemType Directory -Force -Path $texturesDir | Out-Null

$tileKeys = @(
    "m1","m2","m3","m4","m5","m5_red","m6","m7","m8","m9",
    "p1","p2","p3","p4","p5","p5_red","p6","p7","p8","p9",
    "s1","s2","s3","s4","s5","s5_red","s6","s7","s8","s9",
    "east","south","west","north","white_dragon","green_dragon","red_dragon"
)

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

$packMeta = @"
{
  "pack": {
    "pack_format": 64,
    "description": "MahjongCraft item_model resource pack"
  }
}
"@
Set-Content -Path (Join-Path $packRoot "pack.mcmeta") -Value $packMeta -Encoding UTF8

Write-Host "Resource pack generated at: $packRoot"
