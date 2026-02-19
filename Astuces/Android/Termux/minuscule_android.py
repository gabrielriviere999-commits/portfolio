#!/usr/bin/env python3
import subprocess
import sys

# Lire le presse-papier Android
texte = subprocess.check_output(
    ["termux-clipboard-get"]
).decode("utf-8")

if not texte.strip():
    print("⚠️ Presse-papier vide.")
    sys.exit(1)

# Mettre en minuscules
resultat = texte.lower()

# Remettre dans le presse-papier
subprocess.run(
    ["termux-clipboard-set"],
    input=resultat.encode("utf-8")
)

print("✅ Texte mis en minuscules et copié.")
