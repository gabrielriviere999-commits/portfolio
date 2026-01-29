#!/usr/bin/env python3
import csv
import sys
import textwrap
import platform
import ctypes
import ctypes.wintypes as wt

# 🔧 Séparateur CSV
sep = ";"

# 📐 LARGEUR MAXIMALE DU TABLEAU
MAX_TABLE_WIDTH = 120

# ==================== API WINDOWS (UNICODE) ==================== #

user32 = ctypes.WinDLL("user32", use_last_error=True)
kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)

CF_UNICODETEXT = 13

def get_clipboard_unicode():
    if not user32.OpenClipboard(None):
        raise RuntimeError("Impossible d'ouvrir le presse-papier")

    handle = user32.GetClipboardData(CF_UNICODETEXT)
    if not handle:
        user32.CloseClipboard()
        return ""

    ptr = kernel32.GlobalLock(handle)
    if not ptr:
        user32.CloseClipboard()
        return ""

    text = ctypes.wstring_at(ptr)

    kernel32.GlobalUnlock(handle)
    user32.CloseClipboard()

    return text

def set_clipboard_unicode(text):
    data = text.encode("utf-16-le")
    size = len(data) + 2  # +2 pour le \0 final

    hMem = kernel32.GlobalAlloc(0x0002, size)
    ptr = kernel32.GlobalLock(hMem)
    ctypes.memmove(ptr, data, len(data))
    kernel32.GlobalUnlock(hMem)

    if not user32.OpenClipboard(None):
        raise RuntimeError("Impossible d'ouvrir le presse-papier")

    user32.EmptyClipboard()
    user32.SetClipboardData(CF_UNICODETEXT, hMem)
    user32.CloseClipboard()

# ==================== TABLEAU ASCII ==================== #

def lire_lignes_csv(texte, sep=","):
    reader = csv.reader(texte.splitlines(), delimiter=sep)
    lignes = [list(row) for row in reader if row]
    if not lignes:
        return []
    max_cols = max(len(l) for l in lignes)
    return [l + [""] * (max_cols - len(l)) for l in lignes]

def largeurs_colonnes(lignes):
    return [max(len(str(row[c])) for row in lignes) for c in range(len(lignes[0]))]

def ajuster_largeurs_dynamique(largeurs, max_width):
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
    return textwrap.wrap(texte, width=largeur, replace_whitespace=False, drop_whitespace=False) or [""]

def generer_tableau(lignes, max_width):
    if not lignes:
        return ""
    largeurs = largeurs_colonnes(lignes)
    largeurs = ajuster_largeurs_dynamique(largeurs, max_width)
    sep_line = separateur_ligne(largeurs)
    resultat = [sep_line]
    for ligne in lignes:
        cellules = [wrap_cell(cell, w) for cell, w in zip(ligne, largeurs)]
        hauteur = max(len(c) for c in cellules)
        for i in range(hauteur):
            row = "| "
            for cell_lines, w in zip(cellules, largeurs):
                texte = cell_lines[i] if i < len(cell_lines) else ""
                row += texte.ljust(w) + " | "
            resultat.append(row.rstrip())
        resultat.append(sep_line)
    return "\n".join(resultat)

# ==================== MAIN ==================== #

if platform.system() != "Windows":
    print("❌ Ce script est destiné à Windows.")
    sys.exit(1)

texte = get_clipboard_unicode()
lignes = lire_lignes_csv(texte, sep=sep)

if not lignes:
    print("⚠️ Presse-papier vide ou pas de CSV détecté.")
    sys.exit(1)

tableau = generer_tableau(lignes, MAX_TABLE_WIDTH)
set_clipboard_unicode(tableau)

print("✅ Tableau généré et copié dans le presse-papier (Unicode parfait, sans PowerShell) !")
