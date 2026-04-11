#!/bin/bash
# Script pour ajuster la transparence de la fenêtre active
WIN_ID=$(xprop -root _NET_ACTIVE_WINDOW | awk '{print $5}')
TYPE=$(xprop -id $WIN_ID _NET_WM_WINDOW_TYPE)

# Exclusions
if [[ $TYPE == *"DOCK"* ]] || [[ $TYPE == *"DESKTOP"* ]] || \
   [[ $TYPE == *"UTILITY"* ]] || [[ $TYPE == *"MENU"* ]] || \
   [[ $TYPE == *"TOOLBAR"* ]] || [[ $TYPE == *"NOTIFICATION"* ]] || \
   [[ $TYPE == *"SPLASH"* ]]; then
    exit 0
fi

# Exemple : 0xffffffff = opaque, 0xcccccccc ≈ 80%, 0x99999999 ≈ 60%
xprop -id $WIN_ID -f _NET_WM_WINDOW_OPACITY 32c -set _NET_WM_WINDOW_OPACITY 0xffffffff
