# ============================================================
#  Serveur clavier / souris WiFi pour l'application Android
#  "Clavier Bluetooth" (com.gabriel.clavierbluetooth).
#
#  A lancer sur le PC Windows :
#      python server_wifi.py
#
#  Il affiche l'adresse IP a entrer dans l'application, puis
#  ecoute sur le port 9798. Aucune dependance : uniquement la
#  bibliotheque standard de Python 3 (socket + ctypes).
#
#  Protocole (une commande par ligne, terminee par \n) :
#      HELLO                      -> le serveur repond "OK"
#      MouseMove dx,dy            deplacement relatif de la souris
#      MouseScroll n              molette (n positif = vers le haut)
#      MouseDown b / MouseUp b    b = 1 gauche, 2 droit, 3 milieu
#      SpecialKey Down <nom>      enfonce une touche
#      SpecialKey Up <nom>        relache une touche
#      SpecialKey Tap <nom>       appui puis relachement
#      TypeString <texte>         tape un texte (unicode)
#      PING                       ignoré (maintien de connexion)
#      Quit                       ferme la connexion
#
#  CORRECTION "APPUIS LONGS" :
#  SendInput n'engendre PAS d'auto-repeat sous Windows : un
#  seul "SpecialKey Down" = un seul caractere dans un champ de
#  texte. Les jeux fonctionnent car ils interrogent l'etat des
#  touches en boucle. Ce serveur rejoue donc lui-meme les
#  touches maintenues (delai initial puis repetition), comme
#  le ferait un vrai clavier.
# ============================================================

import socket
import sys
import platform
import ctypes
import ctypes.wintypes as wintypes
import threading
import time

PORT = 9798

# ------------------------------------------------------------
# API Windows (SendInput)
# ------------------------------------------------------------

if platform.system() != "Windows":
    print("Attention : ce serveur est concu pour Windows.")

user32 = ctypes.windll.user32

INPUT_MOUSE = 0
INPUT_KEYBOARD = 1

KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004
KEYEVENTF_SCANCODE = 0x0008

MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_MIDDLEDOWN = 0x0020
MOUSEEVENTF_MIDDLEUP = 0x0040
MOUSEEVENTF_WHEEL = 0x0800
MOUSEEVENTF_ABSOLUTE = 0x8000


class MOUSEINPUT(ctypes.Structure):
    _fields_ = [
        ("dx", wintypes.LONG),
        ("dy", wintypes.LONG),
        ("mouseData", wintypes.DWORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.POINTER(wintypes.ULONG)),
    ]


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [
        ("wVk", wintypes.WORD),
        ("wScan", wintypes.WORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.POINTER(wintypes.ULONG)),
    ]


class _INPUTUNION(ctypes.Union):
    _fields_ = [
        ("mi", MOUSEINPUT),
        ("ki", KEYBDINPUT),
    ]


class INPUT(ctypes.Structure):
    _anonymous_ = ("u",)
    _fields_ = [
        ("type", wintypes.DWORD),
        ("u", _INPUTUNION),
    ]


def _pointer_nul():
    return ctypes.cast(
        ctypes.pointer(wintypes.ULONG(0)),
        ctypes.POINTER(wintypes.ULONG),
    )


def envoyer_touche(vk, relache):
    """Enfonce (relache=False) ou relache (relache=True) une touche
    a partir de son code de touche virtuel (VK)."""
    scan = user32.MapVirtualKeyW(vk, 0)  # MAPVK_VK_TO_VSC
    drapeaux = KEYEVENTF_KEYUP if relache else 0

    entree = INPUT()
    entree.type = INPUT_KEYBOARD
    entree.ki = KEYBDINPUT(
        vk & 0xFFFF, scan & 0xFFFF, drapeaux, 0, _pointer_nul()
    )
    user32.SendInput(1, ctypes.byref(entree), ctypes.sizeof(INPUT))


def envoyer_touche_scan(scan, relache):
    """Enfonce/relache une touche par son CODE DE SCAN physique
    (position reelle de la touche sur le clavier, comme le
    ferait un vrai clavier USB/HID). Windows applique ensuite
    la disposition AZERTY du PC, comme avec le mode Bluetooth."""
    drapeaux = KEYEVENTF_SCANCODE | (KEYEVENTF_KEYUP if relache else 0)

    entree = INPUT()
    entree.type = INPUT_KEYBOARD
    entree.ki = KEYBDINPUT(0, scan & 0xFFFF, drapeaux, 0, _pointer_nul())
    user32.SendInput(1, ctypes.byref(entree), ctypes.sizeof(INPUT))


# Codes de scan des 26 lettres (noms = touches HID en position
# QWERTY US, comme dans WifiClient.java) :
#   qwertyuiop -> 0x10-0x19, asdfghjkl -> 0x1E-0x26, zxcvbnm -> 0x2C-0x32
SCAN_LETTRES = {}
for _i, _c in enumerate("qwertyuiop"):
    SCAN_LETTRES[_c] = 0x10 + _i
for _i, _c in enumerate("asdfghjkl"):
    SCAN_LETTRES[_c] = 0x1E + _i
for _i, _c in enumerate("zxcvbnm"):
    SCAN_LETTRES[_c] = 0x2C + _i


def envoyer_unicode(caractere):
    """Tape un caractere unicode (touches sans VK, accents, etc.)."""
    code = ord(caractere) & 0xFFFF

    for drapeaux in (KEYEVENTF_UNICODE, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP):
        entree = INPUT()
        entree.type = INPUT_KEYBOARD
        entree.ki = KEYBDINPUT(0, code, drapeaux, 0, _pointer_nul())
        user32.SendInput(1, ctypes.byref(entree), ctypes.sizeof(INPUT))


def envoyer_par_nom(nom, relache):
    """Envoie une touche a partir de son nom du protocole.
    Les lettres passent par le code de scan (position physique),
    tout le reste par le code de touche virtuel."""
    if nom in SCAN_LETTRES:
        envoyer_touche_scan(SCAN_LETTRES[nom], relache)
    elif nom in TOUCHES:
        envoyer_touche(TOUCHES[nom], relache)
    elif len(nom) == 1:
        envoyer_unicode(nom)
    else:
        print("Touche inconnue :", nom)


def envoyer_souris(drapeaux, dx=0, dy=0, donnees=0):
    entree = INPUT()
    entree.type = INPUT_MOUSE
    entree.mi = MOUSEINPUT(dx, dy, donnees & 0xFFFFFFFF,
                           drapeaux, 0, _pointer_nul())
    user32.SendInput(1, ctypes.byref(entree), ctypes.sizeof(INPUT))


# ------------------------------------------------------------
# Auto-repeat des touches maintenues
#
# SendInput ne declenche PAS l'auto-repeat de Windows (celui-ci
# vient du materiel/driver d'un vrai clavier). Les champs de
# texte et le deplacement du curseur n'ont donc qu'UN appui.
# On rejoue donc nous-memes les touches encore enfoncees.
# ------------------------------------------------------------

DELAI_REPETITION = 0.50      # delai initial avant les repetitions (s)
INTERVALLE_REPETITION = 0.04 # intervalle entre repetitions (~25/s)

verrou_touches = threading.Lock()
# nom -> {"debut": heure d'appui, "derniere": heure du dernier rejou}
touches_maintenues = {}


def maintenir_touche(nom):
    """Enregistre une touche comme etant maintenue (deja envoyee
    une fois en 'Down' par l'app)."""
    if nom in SCAN_LETTRES or nom in TOUCHES:
        with verrou_touches:
            maintenant = time.monotonic()
            touches_maintenues[nom] = {
                "debut": maintenant,
                "derniere": maintenant,
            }


def relacher_touche(nom):
    """Retire une touche des touches maintenues (relachee)."""
    with verrou_touches:
        touches_maintenues.pop(nom, None)


def relacher_toutes_touches():
    """Relache physiquement toutes les touches encore maintenues
    (utilise quand la connexion se coupe)."""
    with verrou_touches:
        noms = list(touches_maintenues.keys())
        touches_maintenues.clear()
    for nom in noms:
        try:
            envoyer_par_nom(nom, True)
        except Exception as e:
            print("Erreur au relachement de", nom, ":", e)


def fil_auto_repeat():
    """Fils en arriere-plan : rejoue les touches maintenues,
    d'abord apres DELAI_REPETITION, puis toutes les
    INTERVALLE_REPETITION secondes, comme un vrai clavier."""
    while True:
        time.sleep(0.01)
        maintenant = time.monotonic()
        a_rejouer = []

        with verrou_touches:
            for nom, etat in touches_maintenues.items():
                if (maintenant - etat["debut"] >= DELAI_REPETITION and
                        maintenant - etat["derniere"] >= INTERVALLE_REPETITION):
                    etat["derniere"] = maintenant
                    a_rejouer.append(nom)

        # Injection hors du verrou (SendInput peut etre lent).
        for nom in a_rejouer:
            try:
                envoyer_par_nom(nom, False)  # re-"Down" = repetition
            except Exception as e:
                print("Erreur de repetition sur", nom, ":", e)


threading.Thread(target=fil_auto_repeat, daemon=True).start()


# ------------------------------------------------------------
# Table des touches : nom -> code de touche virtuel
# (meme noms que le client Android WifiClient.java)
# ------------------------------------------------------------

TOUCHES = {
    "Return": 0x0D, "BackSpace": 0x08, "Tab": 0x09, "Space": 0x20,
    "Esc": 0x1B, "Caps": 0x14,
    "Shift": 0x10, "Ctrl": 0x11, "Alt": 0x12,
    "Win": 0x5B, "Menu": 0xA5,   # Menu = AltGr (VK_RMENU)
    "Left": 0x25, "Up": 0x26, "Right": 0x27, "Down": 0x28,
    "Insert": 0x2D, "Delete": 0x2E, "Home": 0x24, "End": 0x23,
    "PgU": 0x21, "PgD": 0x22,
    "NumLock": 0x90,
    "F1": 0x70, "F2": 0x71, "F3": 0x72, "F4": 0x73, "F5": 0x74,
    "F6": 0x75, "F7": 0x76, "F8": 0x77, "F9": 0x78, "F10": 0x79,
    "F11": 0x7A, "F12": 0x7B,
}

# Chiffres et lettres
for _i in range(10):
    TOUCHES[str(_i)] = 0x30 + _i
for _c in "abcdefghijklmnopqrstuvwxyz":
    TOUCHES[_c] = ord(_c.upper())

# Ponctuation : codes VK reels pour les jeux
TOUCHES.update({
    "-": 0xBD, "=": 0xBB, "[": 0xDB, "]": 0xDD, "\\": 0xDC,
    ";": 0xBA, "'": 0xDE, ",": 0xBC, ".": 0xBE, "/": 0xBF,
    "`": 0xC0, "#": 0xBF,
})

BOUTONS = {
    # b -> (drapeau enfonce, drapeau relache)
    1: (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP),
    2: (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP),
    3: (MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP),
}


# ------------------------------------------------------------
# Traitement d'une commande
# ------------------------------------------------------------

def taper_touche(nom):
    envoyer_par_nom(nom, False)
    envoyer_par_nom(nom, True)


def traiter(ligne):
    if ligne == "PING" or ligne == "":
        return

    if ligne == "Quit":
        return

    try:
        if ligne.startswith("MouseMove "):
            coords = ligne[len("MouseMove "):].split(",")
            envoyer_souris(MOUSEEVENTF_MOVE,
                           int(coords[0]), int(coords[1]))

        elif ligne.startswith("MouseScroll "):
            n = int(ligne[len("MouseScroll "):])
            envoyer_souris(MOUSEEVENTF_WHEEL, donnees=n * 120)

        elif ligne.startswith("MouseDown "):
            b = int(ligne[len("MouseDown "):])
            if b in BOUTONS:
                envoyer_souris(BOUTONS[b][0])

        elif ligne.startswith("MouseUp "):
            b = int(ligne[len("MouseUp "):])
            if b in BOUTONS:
                envoyer_souris(BOUTONS[b][1])

        elif ligne.startswith("SpecialKey Down "):
            nom = ligne[len("SpecialKey Down "):]
            envoyer_par_nom(nom, False)
            # Enregistre la touche pour l'auto-repeat.
            maintenir_touche(nom)

        elif ligne.startswith("SpecialKey Up "):
            nom = ligne[len("SpecialKey Up "):]
            envoyer_par_nom(nom, True)
            relacher_touche(nom)

        elif ligne.startswith("SpecialKey Tap "):
            taper_touche(ligne[len("SpecialKey Tap "):])

        elif ligne.startswith("TypeString "):
            for c in ligne[len("TypeString "):]:
                if c in TOUCHES:
                    taper_touche(c)
                elif c.isupper() and c.lower() in TOUCHES:
                    envoyer_touche(TOUCHES["Shift"], False)
                    taper_touche(c.lower())
                    envoyer_touche(TOUCHES["Shift"], True)
                else:
                    envoyer_unicode(c)

        else:
            print("Commande inconnue :", ligne)

    except Exception as e:
        print("Erreur sur '", ligne, "' :", e)


# ------------------------------------------------------------
# Serveur
# ------------------------------------------------------------

def adresse_ip_locale():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def gerer_client(conn, addr):
    print("Connecte a", addr[0])

    # Delai maximum entre deux messages du client. L'application
    # envoie un "PING" toutes les 600 ms quand on n'utilise pas
    # le clavier : au-dela de 5 secondes sans rien recevoir, on
    # considere que la connexion est perdue (plus besoin de
    # relancer le script : le serveur se remet en attente).
    DELAI_PING = 5.0
    conn.settimeout(DELAI_PING)

    # Aucune touche ne doit rester "maintenue" d'une connexion
    # precedente.
    relacher_toutes_touches()

    f = conn.makefile("r", encoding="utf-8", newline="\n")

    premiere = f.readline().strip()
    if premiere != "HELLO":
        print("Poignee de main invalide, deconnexion.")
        conn.close()
        return

    conn.sendall(b"OK\n")
    print("Application connectee ! (toucher = souris, boutons = clavier)")

    perdu = False

    try:
        while True:
            ligne = f.readline()

            if ligne == "":
                # Le client a ferme proprement la connexion (FIN TCP).
                break

            ligne = ligne.rstrip("\r\n")
            if ligne == "Quit":
                break
            traiter(ligne)

    except socket.timeout:
        perdu = True
        print("Client injoignable (aucun message depuis",
              int(DELAI_PING), "s), connexion consideree perdue.")

    except ConnectionResetError:
        perdu = True
        print("Connexion interrompue par le client.")

    except Exception as e:
        perdu = True
        print("Erreur de connexion :", e)

    try:
        conn.close()
    except Exception:
        pass

    # Si la connexion s'est coupee avec des touches encore
    # maintenues (app fermee brutalement, WiFi perdu...), on les
    # relache pour ne pas laisser une touche bloquee sur le PC.
    relacher_toutes_touches()

    if perdu:
        print("Connexion perdue. En attente d'une nouvelle application...")
    else:
        print("Deconnecte. En attente d'une nouvelle application...")


def main():
    ip = adresse_ip_locale()

    serveur = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    serveur.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    try:
        serveur.bind(("", PORT))
    except OSError as e:
        print("Impossible d'ouvrir le port", PORT, ":", e)
        sys.exit(1)

    serveur.listen(1)

    print("=" * 52)
    print("  Serveur clavier/souris - Clavier Bluetooth")
    print("  Entre cette adresse IP dans l'application :")
    print()
    print("      " + ip)
    print()
    print("  Port :", PORT)
    print("  Ctrl+C pour arreter.")
    print("=" * 52)

    while True:
        try:
            conn, addr = serveur.accept()
        except KeyboardInterrupt:
            print("\nArret du serveur.")
            return
        gerer_client(conn, addr)


if __name__ == "__main__":
    main()