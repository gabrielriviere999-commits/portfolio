#!/usr/bin/env python3
import subprocess
import sys
import platform

def get_clipboard():
    try:
        return subprocess.check_output(["pbpaste"], text=True)
    except Exception:
        print("⚠️ Presse-papier vide ou inaccessible.")
        sys.exit(1)

def set_clipboard(text):
    try:
        subprocess.run(["pbcopy"], input=text.encode("utf-8"), check=True)
    except Exception as e:
        print(f"❌ Impossible de copier dans le presse-papier : {e}")
        sys.exit(1)

if platform.system() != "Darwin":
    print("❌ Ce script est destiné à macOS.")
    sys.exit(1)

texte = get_clipboard()

if not texte.strip():
    print("⚠️ Presse-papier vide.")
    sys.exit(1)

set_clipboard(texte.lower())
print("✅ Texte en minuscules.")
