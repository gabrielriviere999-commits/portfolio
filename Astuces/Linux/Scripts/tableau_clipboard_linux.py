#!/usr/bin/env python3
import subprocess
import csv
import sys
import textwrap

# 🔧 Séparateur CSV
sep = ";"   # ",", "\t", etc.

# 📐 LARGEUR MAXIMALE DU TABLEAU
MAX_TABLE_WIDTH = 120   # jamais dépasser cette largeur


def lire_lignes_csv(texte, sep=","):
    reader = csv.reader(texte.splitlines(), delimiter=sep)
    lignes = [list(row) for row in reader if row]

    if not lignes:
        return []

    max_cols = max(len(l) for l in lignes)
    return [l + [""] * (max_cols - len(l)) for l in lignes]


def largeurs_colonnes(lignes):
    return [
        max(len(str(row[c])) for row in lignes)
        for c in range(len(lignes[0]))
    ]


def ajuster_largeurs_dynamique(largeurs, max_width):
    """Largeur dynamique : s'adapte au contenu, mais ≤ max_width"""
    nb = len(largeurs)
    bordures = 3 * nb + 1
    espace = max_width - bordures

    if espace <= nb:
        return [1] * nb

    total_naturel = sum(largeurs)

    if total_naturel <= espace:
        return largeurs[:]

    nouvelles = [max(1, int(w * espace / total_naturel)) for w in largeurs]

    diff = espace - sum(nouvelles)
    i = 0
    while diff != 0:
        idx = i % nb
        if diff > 0:
            nouvelles[idx] += 1
            diff -= 1
        elif nouvelles[idx] > 1:
            nouvelles[idx] -= 1
            diff += 1
        i += 1

    return nouvelles


def separateur_ligne(largeurs):
    return "+" + "+".join("-" * (w + 2) for w in largeurs) + "+"


def wrap_cell(cell, largeur):
    texte = str(cell)
    if not texte:
        return [""]
    return textwrap.wrap(
        texte,
        width=largeur,
        replace_whitespace=False,
        drop_whitespace=False
    ) or [""]


def generer_tableau(lignes, max_width):
    if not lignes:
        return ""

    largeurs = largeurs_colonnes(lignes)
    largeurs = ajuster_largeurs_dynamique(largeurs, max_width)

    sep_line = separateur_ligne(largeurs)
    resultat = [sep_line]

    for ligne in lignes:
        cellules = [
            wrap_cell(cell, w)
            for cell, w in zip(ligne, largeurs)
        ]

        hauteur = max(len(c) for c in cellules)

        for i in range(hauteur):
            row = "| "
            for cell_lines, w in zip(cellules, largeurs):
                texte = cell_lines[i] if i < len(cell_lines) else ""
                row += texte.ljust(w) + " | "
            resultat.append(row.rstrip())

        resultat.append(sep_line)

    return "\n".join(resultat)


def get_clipboard():
    try:
        return subprocess.check_output(
            ["xclip", "-selection", "clipboard", "-o"]
        ).decode("utf-8")
    except FileNotFoundError:
        print("❌ xclip non installé. Installe-le avec : sudo apt install xclip")
        sys.exit(1)
    except subprocess.CalledProcessError:
        print("⚠️ Presse-papier vide.")
        sys.exit(1)


def set_clipboard(texte):
    try:
        subprocess.run(
            ["xclip", "-selection", "clipboard", "-i"],
            input=texte.encode("utf-8"),
            check=True
        )
    except Exception as e:
        print(f"❌ Impossible de copier dans le presse-papier : {e}")
        sys.exit(1)


if __name__ == "__main__":
    texte = get_clipboard()
    lignes = lire_lignes_csv(texte, sep=sep)
    tableau = generer_tableau(lignes, MAX_TABLE_WIDTH)
    set_clipboard(tableau)
    print(f"✅ Tableau généré et copié dans le presse-papier (max {MAX_TABLE_WIDTH}).")
