#!/usr/bin/env python3
import subprocess
import shutil
import sys
import platform

def get_clipboard():
    if shutil.which("wl-paste"):
        return subprocess.check_output(["wl-paste"]).decode("utf-8")
    elif shutil.which("xclip"):
        return subprocess.check_output(
            ["xclip", "-selection", "clipboard", "-o"]
        ).decode("utf-8")
    elif shutil.which("xsel"):
        return subprocess.check_output(
            ["xsel", "--clipboard", "--output"]
        ).decode("utf-8")
    else:
        print("❌ Aucun outil presse-papier trouvé (xclip / xsel / wl-clipboard).")
        sys.exit(1)

def set_clipboard(text):
    if shutil.which("wl-copy"):
        subprocess.run(["wl-copy"], input=text.encode("utf-8"))
    elif shutil.which("xclip"):
        subprocess.run(
            ["xclip", "-selection", "clipboard"],
            input=text.encode("utf-8")
        )
    elif shutil.which("xsel"):
        subprocess.run(
            ["xsel", "--clipboard", "--input"],
            input=text.encode("utf-8")
        )

if platform.system() != "SunOS":
    print("❌ Ce script est destiné à Solaris.")
    sys.exit(1)

texte = get_clipboard()

if not texte.strip():
    print("⚠️ Presse-papier vide.")
    sys.exit(1)

set_clipboard(texte.swapcase())
print("✅ Casse inversée.")
