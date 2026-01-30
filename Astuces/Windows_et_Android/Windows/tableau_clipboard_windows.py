#!/usr/bin/env python3
import subprocess
import sys
import platform
import tempfile
import os
import csv
import textwrap

SEP = ";"
MAX_WIDTH = 120

def get_clipboard():
    vbs = r"""
Set objHTML = CreateObject("htmlfile")
WScript.StdOut.Write objHTML.ParentWindow.ClipboardData.GetData("text")
"""
    with tempfile.NamedTemporaryFile(delete=False, suffix=".vbs") as f:
        f.write(vbs.encode("utf-8"))
        path = f.name

    try:
        return subprocess.check_output(["cscript", "//nologo", path], text=True)
    except Exception:
        print("⚠️ Impossible de lire le presse-papier via VBScript.")
        sys.exit(1)
    finally:
        os.remove(path)

def set_clipboard(text):
    try:
        p = subprocess.Popen(["clip"], stdin=subprocess.PIPE, text=True)
        p.communicate(text)
    except Exception:
        print("⚠️ Impossible d'écrire dans le presse-papier via clip.exe.")
        sys.exit(1)

def lire_lignes_csv(texte, sep=SEP):
    reader = csv.reader(texte.splitlines(), delimiter=sep)
    lignes = [list(row) for row in reader if row]
    if not lignes:
        return []
    max_cols = max(len(l) for l in lignes)
    return [l + [""] * (max_cols - len(l)) for l in lignes]

def largeurs_colonnes(lignes):
    return [max(len(str(row[c])) for row in lignes) for c in range(len(lignes[0]))]

def ajuster_largeurs(largeurs, max_width):
    nb = len(largeurs)
    bordures = 3 * nb + 1
    espace = max_width - bordures
    if espace <= nb:
        return [1] * nb
    total = sum(largeurs)
    if total <= espace:
        return largeurs[:]
    new = [max(1, int(w * espace / total)) for w in largeurs]
    diff = espace - sum(new)
    i = 0
    while diff != 0:
        idx = i % nb
        if diff > 0:
            new[idx] += 1
            diff -= 1
        elif new[idx] > 1:
            new[idx] -= 1
            diff += 1
        i += 1
    return new

def wrap_cell(cell, w):
    t = str(cell)
    if not t:
        return [""]
    return textwrap.wrap(t, width=w, replace_whitespace=False, drop_whitespace=False) or [""]

def generer_tableau(lignes, max_width=MAX_WIDTH):
    if not lignes:
        return ""
    largeurs = ajuster_largeurs(largeurs_colonnes(lignes), max_width)
    sep_line = "+" + "+".join("-" * (w + 2) for w in largeurs) + "+"
    out = [sep_line]
    for ligne in lignes:
        cells = [wrap_cell(c, w) for c, w in zip(ligne, largeurs)]
        h = max(len(c) for c in cells)
        for i in range(h):
            row = "| "
            for cell, w in zip(cells, largeurs):
                txt = cell[i] if i < len(cell) else ""
                row += txt.ljust(w) + " | "
            out.append(row.rstrip())
        out.append(sep_line)
    return "\n".join(out)

if platform.system() != "Windows":
    print("❌ Ce script est destiné à Windows.")
    sys.exit(1)

texte = get_clipboard()

if not texte.strip():
    print("⚠️ Presse-papier vide.")
    sys.exit(1)

lignes = lire_lignes_csv(texte, sep=SEP)
if not lignes:
    print("⚠️ Pas de CSV détecté.")
    sys.exit(1)

tableau = generer_tableau(lignes)
set_clipboard(tableau)

print("✅ Tableau généré et copié dans le presse-papier (VBScript + clip).")
