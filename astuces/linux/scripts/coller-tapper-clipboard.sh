#!/bin/bash
# coller_tapper_clipboard.sh

echo "Clique sur la fenêtre cible (xdotool attend ton clic, 5s max)..."
win_id=$(timeout 5 xdotool selectwindow)
[ -z "$win_id" ] && echo "Aucune fenêtre sélectionnée dans le délai imparti." && exit 1

sleep 1
xdotool windowfocus "$win_id"

# Lire le presse-papiers
if command -v xclip >/dev/null 2>&1; then
    clipboard=$(xclip -o -selection clipboard)
elif command -v xsel >/dev/null 2>&1; then
    clipboard=$(xsel --clipboard)
else
    echo "Ni xclip ni xsel n'est installé."
    exit 1
fi

# Compter le nombre de lignes
total_lines=$(printf "%s\n" "$clipboard" | wc -l)
current_line=0

# Lecture ligne par ligne et frappe
while IFS= read -r ligne || [ -n "$ligne" ]; do
    current_line=$((current_line + 1))
    xdotool type --window "$win_id" --clearmodifiers --delay 50 -- "$ligne"
    # Ajouter Return seulement si ce n'est pas la dernière ligne
    if [ $current_line -lt $total_lines ]; then
        xdotool key --window "$win_id" Return
    fi
done <<< "$clipboard"
