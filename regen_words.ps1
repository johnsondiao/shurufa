# Regenerate cn_words.txt with syllable separators (xi'an 西安).
# ASCII-only source: PS 5.1 parses BOM-less UTF-8 scripts as ANSI.

$ErrorActionPreference = 'Stop'

$root   = 'D:\vibecoding\shurufa'
$assets = Join-Path $root 'PersonalIME\PersonalIME\app\src\main\assets'
$idioms = Join-Path $root '_data\phrase-pinyin-data\zdic_cybs.txt'
$large  = Join-Path $root '_data\phrase-pinyin-data\large_pinyin.txt'

# ---------- tone stripping ----------
$toneMap = @{}
foreach ($pair in @(
    @(0x0101,'a'), @(0x00E1,'a'), @(0x01CE,'a'), @(0x00E0,'a'),
    @(0x0113,'e'), @(0x00E9,'e'), @(0x011B,'e'), @(0x00E8,'e'), @(0x00EA,'e'),
    @(0x012B,'i'), @(0x00ED,'i'), @(0x01D0,'i'), @(0x00EC,'i'),
    @(0x014D,'o'), @(0x00F3,'o'), @(0x01D2,'o'), @(0x00F2,'o'),
    @(0x016B,'u'), @(0x00FA,'u'), @(0x01D4,'u'), @(0x00F9,'u'),
    @(0x00FC,'v'), @(0x01D6,'v'), @(0x01D8,'v'), @(0x01DA,'v'), @(0x01DC,'v'),
    @(0x0144,'n'), @(0x0148,'n'), @(0x01F9,'n')
)) { $toneMap[[char][int]$pair[0]] = $pair[1] }

# Convert "yǐ jīng" -> "yi'jing" (syllables joined with apostrophe).
function Convert-Pinyin([string]$py) {
    $syls = New-Object 'System.Collections.Generic.List[string]'
    foreach ($syl in ($py -split '\s+')) {
        if ($syl.Length -eq 0) { continue }
        $sb = New-Object System.Text.StringBuilder
        $ok = $true
        foreach ($ch in $syl.ToCharArray()) {
            if ($toneMap.ContainsKey($ch)) { [void]$sb.Append($toneMap[$ch]) }
            elseif ($ch -ge 'a' -and $ch -le 'z') { [void]$sb.Append($ch) }
            elseif ($ch -ge 'A' -and $ch -le 'Z') { [void]$sb.Append([char]($ch + 32)) }
            else { $ok = $false; break }
        }
        if (-not $ok -or $sb.Length -eq 0) { return $null }
        $syls.Add($sb.ToString())
    }
    if ($syls.Count -eq 0) { return $null }
    return [string]::Join("'", $syls)
}

function Test-PureCjk([string]$w) {
    if ($w.Length -eq 0) { return $false }
    foreach ($ch in $w.ToCharArray()) {
        $v = [int]$ch
        if ($v -lt 0x4E00 -or $v -gt 0x9FFF) { return $false }
    }
    return $true
}

# ---------- regenerate cn_words.txt ----------
$seen = New-Object 'System.Collections.Generic.HashSet[string]'
$out = New-Object 'System.Collections.Generic.List[string]'
$stat = @{ idioms = 0; w2 = 0; w3 = 0; w4 = 0 }

function Add-Entry([string]$pinyin, [string]$word, [int]$freq) {
    if ($null -eq $pinyin) { return }
    $key = $pinyin + '|' + $word
    if (-not $script:seen.Add($key)) { return }
    $script:out.Add($pinyin + ' ' + $word + ' ' + $freq)
}

$sr = New-Object System.IO.StreamReader($idioms, [System.Text.Encoding]::UTF8)
while ($null -ne ($line = $sr.ReadLine())) {
    $i = $line.IndexOf(':')
    if ($i -lt 1) { continue }
    $w = $line.Substring(0, $i).Trim()
    if (-not (Test-PureCjk $w)) { continue }
    $py = Convert-Pinyin $line.Substring($i + 1).Trim()
    if ($null -ne $py) { $stat.idioms++; Add-Entry $py $w 48 }
}
$sr.Close()

$sr = New-Object System.IO.StreamReader($large, [System.Text.Encoding]::UTF8)
while ($null -ne ($line = $sr.ReadLine())) {
    if ($line.StartsWith('#')) { continue }
    $i = $line.IndexOf(':')
    if ($i -lt 1) { continue }
    $w = $line.Substring(0, $i).Trim()
    if (-not (Test-PureCjk $w)) { continue }
    $n = $w.Length
    if ($n -lt 2 -or $n -gt 4) { continue }
    $py = Convert-Pinyin $line.Substring($i + 1).Trim()
    if ($null -eq $py) { continue }
    if ($n -eq 2) { $f = 50; $stat.w2++ }
    elseif ($n -eq 3) { $f = 50; $stat.w3++ }
    else { $f = 46; $stat.w4++ }
    Add-Entry $py $w $f
}
$sr.Close()

$wordsPath = Join-Path $assets 'cn_words.txt'
$sw = New-Object System.IO.StreamWriter($wordsPath, $false, (New-Object System.Text.UTF8Encoding($false)))
foreach ($l in $out) { $sw.WriteLine($l) }
$sw.Close()

Write-Host ("cn_words.txt: " + $out.Count + " entries (idioms " + $stat.idioms + ", 2-char " + $stat.w2 + ", 3-char " + $stat.w3 + ", 4-char " + $stat.w4 + ")")

# ---------- sanity checks ----------
$chk = [System.IO.File]::ReadAllLines($wordsPath, [System.Text.Encoding]::UTF8)
$xian = [string][char]0x897F + [char]0x5B89   # xi an
$shebei = [string][char]0x8BBE + [char]0x5907 # she bei
$okXian = $chk | Where-Object { $_ -eq "xi'an $xian 50" }
$badXian = $chk | Where-Object { $_ -eq "xian $xian 50" }
$okShebei = $chk | Where-Object { $_ -eq "she'bei $shebei 50" }
Write-Host ("sanity xi'an entry: " + $(if ($okXian) { 'OK' } else { 'MISSING' }))
Write-Host ("sanity no 'xian' form for same word: " + $(if ($badXian) { 'UNEXPECTED PRESENT' } else { 'OK' }))
Write-Host ("sanity she'bei entry: " + $(if ($okShebei) { 'OK' } else { 'MISSING' }))
