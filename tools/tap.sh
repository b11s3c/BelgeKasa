#!/bin/zsh
# Usage: tap.sh "<text or content-desc regex>" — taps the center of the first matching node.
A=~/Library/Android/sdk/platform-tools/adb
$A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
B=$($A shell cat /sdcard/u.xml | tr '>' '\n' | grep -E "(text|content-desc)=\"$1\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+')
[ -z "$B" ] && { echo "not found: $1"; exit 1; }
set -- ${(f)B}
x=$(( ($1 + $3) / 2 )); y=$(( ($2 + $4) / 2 ))
$A shell input tap $x $y; echo "tapped $x $y"
