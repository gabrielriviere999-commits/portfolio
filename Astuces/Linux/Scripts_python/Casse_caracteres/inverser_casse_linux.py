#!/usr/bin/env python3
import subprocess
import shutil
import sys

def get_clipboard():
    if shutil.which("wl-paste"):
        return subprocess.check_output(["wl-paste"]).decode("utf-8")
    elif shutil.which("xclip"):
        return subprocess.check_output(
            ["xclip", "-selection", "clipboard", "-o"]
        ).decode("utf-8")
    else:
        print("❌ Aucun outil presse-papier trouvé (xclip / wl-clipboard).")
        sys.exit(1)

def set_clipboard(text):
    if shutil.which("wl-copy"):
        subprocess.run(["wl-copy"], input=text.encode("utf-8"))
    elif shutil.which("xclip"):
        subprocess.run(
            ["xclip", "-selection", "clipboard"],
            input=text.encode("utf-8")
        )

texte = get_clipboard()

if not texte.strip():
    print("⚠️ Presse-papier vide.")
    sys.exit(1)

resultat = texte.swapcase()
set_clipboard(resultat)

print("✅ Casse inversée.")
