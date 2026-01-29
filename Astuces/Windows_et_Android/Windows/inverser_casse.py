#!/usr/bin/env python3
import sys
import platform
import ctypes
import ctypes.wintypes as wt

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
    size = len(data) + 2

    hMem = kernel32.GlobalAlloc(0x0002, size)
    ptr = kernel32.GlobalLock(hMem)
    ctypes.memmove(ptr, data, len(data))
    kernel32.GlobalUnlock(hMem)

    if not user32.OpenClipboard(None):
        raise RuntimeError("Impossible d'ouvrir le presse-papier")

    user32.EmptyClipboard()
    user32.SetClipboardData(CF_UNICODETEXT, hMem)
    user32.CloseClipboard()

# ==================== MAIN ==================== #

if platform.system() != "Windows":
    print("❌ Ce script est destiné à Windows.")
    sys.exit(1)

texte = get_clipboard_unicode()

if not texte.strip():
    print("⚠️ Presse-papier vide.")
    sys.exit(1)

set_clipboard_unicode(texte.swapcase())
print("✅ Casse inversée.")
