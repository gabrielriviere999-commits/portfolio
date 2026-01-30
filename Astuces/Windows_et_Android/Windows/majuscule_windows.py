#!/usr/bin/env python3
import subprocess
import sys
import platform
import tempfile
import os

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
    except:
        print("⚠️ Impossible de lire le presse-papier via VBScript.")
        sys.exit(1)
    finally:
        os.remove(path)

def set_clipboard(text):
    p = subprocess.Popen(["clip"], stdin=subprocess.PIPE, text=True)
    p.communicate(text)

if platform.system() != "Windows":
    print("❌ Ce script est destiné à Windows.")
    sys.exit(1)

texte = get_clipboard()

if not texte.strip():
    print("⚠️ Presse-papier vide.")
    sys.exit(1)

set_clipboard(texte.upper())
print("✅ Texte en MAJUSCULES.")
