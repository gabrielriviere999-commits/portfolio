#!/bin/bash

generate_index() {
    local dir="$1"
    dir="${dir%/}"
    local output="$dir/liste-fichiers.html"

    echo "Génération : $output"

    {
        echo "<!DOCTYPE html>"
        echo "<html><head><meta charset='utf-8'>"
        echo "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        echo "<title>Liste des fichiers - ${dir}</title>"
        echo "<style>"
        echo "body { background:#C0C0C0; color:black; font-family:sans-serif; font-size:18px; line-height:1.5; }"

        # Liens : inline pour retrait suspendu
        echo "a {"
        echo "  color:blue;"
        echo "  display:inline;"
        echo "  word-wrap:break-word;"
        echo "  white-space:normal;"
        echo "  max-width:100%;"
        echo "  font-size:18px;"
        echo "  text-decoration: none;"
        echo "}"

        echo "a:hover {"
        echo "  text-decoration: underline;"
        echo "}"

        # Puces propres
        echo "ul { margin:0; padding-left:1.2em; list-style-position:outside; }"
        echo "li { margin:4px 0; font-size:18px; }"

        # Ajout du label [Dossier] via CSS
        echo "li.folder::before { content:'[Dossier] '; color:#555; }"

        echo "h1 { font-size:22px; }"

        # CSS du bouton Win98 → DANS <style>, pas après
        echo ".win98-btn { padding:2px 6px; background:#C0C0C0; border:2px solid #fff; border-right-color:#404040; border-bottom-color:#404040; font-family:monospace; font-size:14px; text-decoration:none; display:inline-block; color:black; }"
        echo ".win98-btn:active { border:2px solid #404040; border-right-color:#fff; border-bottom-color:#fff; }"
        echo ".win98-btn:hover { background:#E0E0E0; }"

        echo "</style>"
        echo "</head><body>"
        echo "<h1>Contenu de : ${dir}</h1>"
        echo "<ul>"
    } > "$output"

    # Lien retour
    if [ "$dir" != "." ]; then
        echo "<li><a href=\"../liste-fichiers.html\">← Retour</a></li>" >> "$output"
    fi

    # Parcours
        # --- Dossiers en premier (triés par nom) ---
    for item in $(printf '%s\n' "$dir"/*/ 2>/dev/null | sort -f); do
        [ -d "$item" ] || continue
        name=$(basename "$item")

        echo "<li><a href=\"$name/liste-fichiers.html\">[Dossier]&nbsp;$name</a></li>" >> "$output"
        generate_index "$item"
    done

    # --- Fichiers ensuite (triés par nom) ---
    for item in $(printf '%s\n' "$dir"/* 2>/dev/null | sort -f); do
        [ -e "$item" ] || continue
        [ -d "$item" ] && continue

        name=$(basename "$item")

        # Ignorer le fichier généré
        if [ "$name" = "liste-fichiers.html" ]; then
            continue
        fi

        {
            echo "<li>"
            echo "  <a href=\"$name\">$name</a><br>"
            echo "  <a href=\"$name\" download class=\"win98-btn\">Télécharger</a>"
            echo "</li>"
        } >> "$output"
    done

    {
        echo "</ul>"
        echo "</body></html>"
    } >> "$output"
}

generate_index "."
