param(
    [string]$PackRoot = "build/mahjongcraft-itemmodel-pack"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$packPath = Join-Path $root $PackRoot

$modelsDir = Join-Path $packPath "assets/mahjongcraft/models/item/tile"
$texturesDir = Join-Path $packPath "assets/mahjongcraft/textures/item/tile"
$paperItem = Join-Path $packPath "assets/minecraft/items/paper.json"

$errors = @()
$itemIds = @()

if (-not (Test-Path $modelsDir)) { $errors += "Missing models dir: $modelsDir" }
if (-not (Test-Path $texturesDir)) { $errors += "Missing textures dir: $texturesDir" }

if (Test-Path $modelsDir) {
    Get-ChildItem -Path $modelsDir -Filter *.json | ForEach-Object {
        $id = $_.BaseName
        $itemIds += $id

        $modelJson = Join-Path $modelsDir ("$id.json")
        $texPng = Join-Path $texturesDir ("$id.png")

        if (-not (Test-Path $texPng)) { $errors += "Missing texture: $texPng" }

        try {
            $modelData = Get-Content $modelJson -Raw | ConvertFrom-Json
            $texRef = $modelData.textures.layer0
            $expectedTex = "mahjongcraft:item/tile/$id"
            if ($texRef -ne $expectedTex) { $errors += "Texture ref mismatch in ${modelJson}: ${texRef} != ${expectedTex}" }
        } catch {
            $errors += "Invalid JSON: $modelJson"
        }
    }
}

if (-not (Test-Path $paperItem)) {
    $errors += "Missing paper item definition: $paperItem"
} else {
    try {
        $paperData = Get-Content $paperItem -Raw | ConvertFrom-Json
        $entryModels = @()
        foreach ($entry in $paperData.model.entries) { if ($entry.model.model) { $entryModels += $entry.model.model } }
        $missing = @()
        foreach ($id in $itemIds) {
            $want = "mahjongcraft:item/tile/$id"
            if ($entryModels -notcontains $want) { $missing += $want }
        }
        if ($missing.Count -gt 0) { $errors += "paper.json missing entries for $($missing.Count) items" }
    } catch {
        $errors += "Invalid JSON: $paperItem"
    }
}

Write-Host "ITEM_IDS:"
$itemIds | Sort-Object | ForEach-Object { Write-Host $_ }
Write-Host ""
Write-Host "ERRORS:"
if ($errors.Count -eq 0) {
    Write-Host "(none)"
} else {
    $errors | ForEach-Object { Write-Host $_ }
    exit 1
}
