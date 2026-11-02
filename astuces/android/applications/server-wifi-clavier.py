# ============================================================
#  Serveur clavier / souris WiFi pour l'application Android
#  "Clavier Bluetooth" (com.gabriel.clavierbluetooth).
#
#  A lancer sur le PC :
#      python server_wifi.py
#
#  Fonctionne sur Windows (SendInput) ET sur Linux (X11/XTest).
#  Aucune dependance : uniquement la bibliotheque standard de
#  Python 3 (socket + ctypes).
#
#  MULTI-CLIENTS : plusieurs telephones/tablettes peuvent se
#  connecter en meme temps. Chaque client a son propre fil
#  d'execution ; les touches maintenues sont tracees par
#  client, et la deconnexion de l'un ne relache que SES
#  touches (pas celles des autres).
#
#  Sur Linux :
#    - necessite une session X11 (ou XWayland, variable
#      DISPLAY definie). Wayland pur n'a pas d'API globale
#      pour simuler des touches.
#    - libX11 et libXtst doivent etre presentes (elles le
#      sont sur quasiment toutes les distributions).
#
#  Il affiche l'adresse IP a entrer dans l'application, puis
#  ecoute sur le port 9798.
#
#  Protocole (une commande par ligne, terminee par \n) :
#      HELLO                      -> le serveur repond "OK"
#      MouseMove dx,dy            deplacement relatif de la souris
#      MouseScroll n              molette (n positif = vers le haut)
#      MouseDown b / MouseUp b    b = 1 gauche, 2 droit, 3 milieu
#      SpecialKey Down <nom>      enfonce une touche
#      SpecialKey Up <nom>        relache une touche
#      SpecialKey Tap <nom>       appui puis relachement
#      TypeString <texte>         tape un texte
#      PING                       ignoré (maintien de connexion)
#      Quit                       ferme la connexion
#
#  CORRECTION "APPUIS LONGS" :
#  Ni SendInput (Windows) ni XTest (Linux) n'engendrent
#  l'auto-repeat du systeme : un seul "SpecialKey Down" = un
#  seul caractere dans un champ de texte. Les jeux fonctionnent
#  car ils interrogent l'etat des touches en boucle. Ce serveur
#  rejoue donc lui-meme les touches maintenues (delai initial
#  puis repetition), comme le ferait un vrai clavier.
# ============================================================

import socket
import sys
import platform
import ctypes
import threading
import time

PORT = 9798

SYSTEME = platform.system()

# ------------------------------------------------------------
# Drapeaux "souris" internes (communs aux deux plateformes)
# ------------------------------------------------------------

MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_MIDDLEDOWN = 0x0020
MOUSEEVENTF_MIDDLEUP = 0x0040
MOUSEEVENTF_WHEEL = 0x0800

BOUTONS = {
    # b -> (drapeau enfonce, drapeau relache)
    1: (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP),
    2: (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP),
    3: (MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP),
}

# ------------------------------------------------------------
# Couche plateforme : toutes les fonctions envoyer_* sont
# definies ici, le reste du serveur est commun.
# ------------------------------------------------------------

if SYSTEME == "Windows":

    import ctypes.wintypes as wintypes

    user32 = ctypes.windll.user32

    INPUT_MOUSE = 0
    INPUT_KEYBOARD = 1

    KEYEVENTF_KEYUP = 0x0002
    KEYEVENTF_UNICODE = 0x0004
    KEYEVENTF_SCANCODE = 0x0008

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

    # Table des touches : nom -> code de touche virtuel
    # (meme noms que le client Android WifiClient.java)
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

    def envoyer_touche(vk, relache):
        """Enfonce (relache=False) ou relache (relache=True) une
        touche a partir de son code de touche virtuel (VK)."""
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

    def envoyer_unicode(caractere):
        """Tape un caractere unicode (touches sans VK, accents...)."""
        code = ord(caractere) & 0xFFFF

        for drapeaux in (KEYEVENTF_UNICODE,
                         KEYEVENTF_UNICODE | KEYEVENTF_KEYUP):
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

elif SYSTEME == "Linux":

    print("Mode Linux : simulation clavier/souris via X11/XTest.")

    # libX11 et libXtst, chargees dynamiquement (paquets presents
    # par defaut sur les distributions avec un serveur X).
    try:
        libX11 = ctypes.CDLL("libX11.so.6")
        libXtst = ctypes.CDLL("libXtst.so.6")
    except OSError as e:
        sys.exit("Impossible de charger libX11/libXtst : " + str(e)
                 + "\nInstalle libx11-6 et libxtst6.")

    libX11.XOpenDisplay.restype = ctypes.c_void_p
    libX11.XOpenDisplay.argtypes = [ctypes.c_char_p]
    libX11.XFlush.argtypes = [ctypes.c_void_p]
    libX11.XFlush.restype = None
    libX11.XSync.argtypes = [ctypes.c_void_p, ctypes.c_int]
    libX11.XSync.restype = None
    libX11.XStringToKeysym.restype = ctypes.c_ulong
    libX11.XStringToKeysym.argtypes = [ctypes.c_char_p]
    libX11.XKeysymToKeycode.restype = ctypes.c_ubyte
    libX11.XKeysymToKeycode.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
    libX11.XWarpPointer.restype = None
    libX11.XWarpPointer.argtypes = [ctypes.c_void_p] + \
        [ctypes.c_ulong] * 8

    libXtst.XTestFakeKeyEvent.restype = ctypes.c_int
    libXtst.XTestFakeKeyEvent.argtypes = [
        ctypes.c_void_p, ctypes.c_ubyte, ctypes.c_int, ctypes.c_ulong]
    libXtst.XTestFakeButtonEvent.restype = ctypes.c_int
    libXtst.XTestFakeButtonEvent.argtypes = [
        ctypes.c_void_p, ctypes.c_uint, ctypes.c_int, ctypes.c_ulong]

    affichage = libX11.XOpenDisplay(None)
    if not affichage:
        sys.exit(
            "Impossible d'ouvrir l'affichage X11 (variable DISPLAY).\n"
            "Wayland pur n'est pas supporte : il faut une session X11\n"
            "ou passer par XWayland."
        )

    def vider_tampon():
        libX11.XFlush(affichage)

    # Codes keysym X11 (cf. /usr/include/X11/keysymdef.h)
    XK_ISO_Level3_Shift = 0xFE03   # AltGr
    XK_VoidSymbol = 0xFFFFFF

    # Table des touches : nom -> keysym X11
    # (meme noms que le client Android WifiClient.java)
    TOUCHES = {
        "Return": 0xFF0D, "BackSpace": 0xFF08, "Tab": 0xFF09,
        "Space": 0x20, "Esc": 0xFF1B, "Caps": 0xFFE5,
        "Shift": 0xFFE1, "Ctrl": 0xFFE3, "Alt": 0xFFE9,
        "Win": 0xFFE7,                     # Super_L
        "Menu": XK_ISO_Level3_Shift,       # Menu = AltGr sous X11
        "Left": 0xFF51, "Up": 0xFF52, "Right": 0xFF53, "Down": 0xFF54,
        "Insert": 0xFF63, "Delete": 0xFFFF, "Home": 0xFF50, "End": 0xFF57,
        "PgU": 0xFF55, "PgD": 0xFF56,
        "NumLock": 0xFF7F,
        "F1": 0xFFBE, "F2": 0xFFBF, "F3": 0xFFC0, "F4": 0xFFC1,
        "F5": 0xFFC2, "F6": 0xFFC3, "F7": 0xFFC4, "F8": 0xFFC5,
        "F9": 0xFFC6, "F10": 0xFFC7, "F11": 0xFFC8, "F12": 0xFFC9,
    }

    # Chiffres et lettres : sous X11 le keysym correspond au
    # CARACTERE produit ; XKeysymToKeycode trouve ensuite la
    # touche physique selon la disposition REELLE du PC. Le
    # resultat suit donc la configuration du clavier Linux
    # (AZERTY, QWERTY, etc.), sans aucune table de scan.
    for _i in range(10):
        TOUCHES[str(_i)] = 0x30 + _i
    for _c in "abcdefghijklmnopqrstuvwxyz":
        TOUCHES[_c] = ord(_c)

    # Ponctuation : keysyms = codes des caracteres directement
    for _c in "-=[]\\;',./`#":
        TOUCHES[_c] = ord(_c)

    # Pas de codes de scan sous X11 : les keysyms suffisent.
    SCAN_LETTRES = {}

    _cache_keycodes = {}

    def keycode_de(keysym):
        """Retourne le keycode correspondant au keysym selon la
        disposition courante du serveur X, avec un petit cache."""
        if keysym not in _cache_keycodes:
            _cache_keycodes[keysym] = libX11.XKeysymToKeycode(
                affichage, keysym)
        return _cache_keycodes[keysym]

    def envoyer_par_nom(nom, relache):
        """Enfonce/relache une touche via XTest, a partir de son
        nom du protocole. La disposition du clavier Linux est
        appliquee automatiquement par le serveur X."""
        if nom in TOUCHES:
            keysym = TOUCHES[nom]
        elif len(nom) == 1:
            keysym = libX11.XStringToKeysym(nom.encode("utf-8"))
        else:
            print("Touche inconnue :", nom)
            return

        if keysym in (0, XK_VoidSymbol):
            print("Pas de keysym pour :", nom)
            return

        keycode = keycode_de(keysym)
        if keycode == 0:
            print("Pas de touche physique pour :", nom,
                  "(absente de la disposition du clavier ?)")
            return

        libXtst.XTestFakeKeyEvent(
            affichage, keycode, 0 if relache else 1, 0)
        vider_tampon()

    def envoyer_unicode(caractere):
        """Tape un caractere par son keysym unicode (0x0100xxxx).
        Limitation X11 : il faut que ce caractere soit produit par
        une touche de la disposition courante, sinon rien."""
        code = ord(caractere)
        for keysym in (0x01000000 | code, code):
            keycode = keycode_de(keysym)
            if keycode != 0:
                libXtst.XTestFakeKeyEvent(affichage, keycode, 1, 0)
                libXtst.XTestFakeKeyEvent(affichage, keycode, 0, 0)
                vider_tampon()
                return
        print("Caractere non disponible sur cette disposition :",
              repr(caractere))

    def envoyer_souris(drapeaux, dx=0, dy=0, donnees=0):
        """Souris via XWarpPointer (deplacement relatif),
        XTestFakeButtonEvent (boutons et molette)."""
        if drapeaux & MOUSEEVENTF_MOVE:
            # dest_fenetre = None -> deplacement RELATIF de (dx, dy).
            libX11.XWarpPointer(affichage, 0, 0, 0, 0, 0, 0, dx, dy)

        if drapeaux & MOUSEEVENTF_WHEEL:
            # Boutons molette X11 : 4 = vers le haut, 5 = vers le bas.
            bouton = 4 if donnees > 0 else 5
            crans = max(1, abs(donnees) // 120)
            for _ in range(crans):
                libXtst.XTestFakeButtonEvent(affichage, bouton, 1, 0)
                libXtst.XTestFakeButtonEvent(affichage, bouton, 0, 0)

        # Boutons X11 : 1 = gauche, 2 = milieu, 3 = droit.
        if drapeaux & MOUSEEVENTF_LEFTDOWN:
            libXtst.XTestFakeButtonEvent(affichage, 1, 1, 0)
        if drapeaux & MOUSEEVENTF_LEFTUP:
            libXtst.XTestFakeButtonEvent(affichage, 1, 0, 0)
        if drapeaux & MOUSEEVENTF_RIGHTDOWN:
            libXtst.XTestFakeButtonEvent(affichage, 3, 1, 0)
        if drapeaux & MOUSEEVENTF_RIGHTUP:
            libXtst.XTestFakeButtonEvent(affichage, 3, 0, 0)
        if drapeaux & MOUSEEVENTF_MIDDLEDOWN:
            libXtst.XTestFakeButtonEvent(affichage, 2, 1, 0)
        if drapeaux & MOUSEEVENTF_MIDDLEUP:
            libXtst.XTestFakeButtonEvent(affichage, 2, 0, 0)

        vider_tampon()

    def envoyer_touche(vk, relache):
        """Non utilise sous Linux (les VK sont specifiques a
        Windows) ; presente pour la compatibilite."""
        pass

else:
    sys.exit("Systeme non supporte : " + str(SYSTEME)
             + " (seuls Windows et Linux le sont).")


def taper_touche(nom):
    envoyer_par_nom(nom, False)
    envoyer_par_nom(nom, True)


# ------------------------------------------------------------
# Auto-repeat des touches maintenues
#
# Ni SendInput ni XTest ne declenchent l'auto-repeat du systeme
# (celui-ci vient du materiel/driver d'un vrai clavier). Les
# champs de texte n'ont donc qu'UN appui. On rejoue donc
# nous-memes les touches encore enfoncees.
#
# Les touches sont tracees PAR CLIENT : chaque client connecte
# possede ses propres touches maintenues, et la deconnexion de
# l'un ne relache que les siennes.
# ------------------------------------------------------------

DELAI_REPETITION = 0.50      # delai initial avant les repetitions (s)
INTERVALLE_REPETITION = 0.04 # intervalle entre repetitions (~25/s)

verrou_touches = threading.Lock()
# nom -> {"client": identite, "debut": heure, "derniere": heure}
touches_maintenues = {}


def maintenir_touche(nom, client):
    """Enregistre une touche comme etant maintenue par CLIENT
    (deja envoyee une fois en 'Down' par l'app)."""
    if nom in SCAN_LETTRES or nom in TOUCHES:
        with verrou_touches:
            maintenant = time.monotonic()
            touches_maintenues[nom] = {
                "client": client,
                "debut": maintenant,
                "derniere": maintenant,
            }


def relacher_touche(nom):
    """Retire une touche des touches maintenues (relachee)."""
    with verrou_touches:
        touches_maintenues.pop(nom, None)


def relacher_touche_client(client):
    """Relache physiquement toutes les touches maintenues par
    ce client uniquement (utilise quand SA connexion se coupe :
    les touches des autres clients restent en place)."""
    with verrou_touches:
        noms = [n for n, e in touches_maintenues.items()
                if e["client"] == client]
        for n in noms:
            del touches_maintenues[n]
    for nom in noms:
        try:
            envoyer_par_nom(nom, True)
        except Exception as e:
            print("Erreur au relachement de", nom, ":", e)


def fil_auto_repeat():
    """Fils en arriere-plan : rejoue TOUTES les touches maintenues
    (quel que soit le client qui les tient), d'abord apres
    DELAI_REPETITION puis toutes les INTERVALLE_REPETITION
    secondes, comme un vrai clavier."""
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

        # Injection hors du verrou (peut etre lent).
        for nom in a_rejouer:
            try:
                envoyer_par_nom(nom, False)  # re-"Down" = repetition
            except Exception as e:
                print("Erreur de repetition sur", nom, ":", e)


threading.Thread(target=fil_auto_repeat, daemon=True).start()


# ------------------------------------------------------------
# Traitement d'une commande (commun Windows / Linux,
# execute dans le fil du client qui a envoye la commande)
# ------------------------------------------------------------

def traiter(ligne, client):
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
            # Enregistre la touche pour l'auto-repeat, au nom
            # de CE client.
            maintenir_touche(nom, client)

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
                    envoyer_par_nom("Shift", False)
                    taper_touche(c.lower())
                    envoyer_par_nom("Shift", True)
                else:
                    envoyer_unicode(c)

        else:
            print("Commande inconnue :", ligne)

    except Exception as e:
        print("Erreur sur '", ligne, "' :", e)


# ------------------------------------------------------------
# Serveur (commun Windows / Linux, multi-clients)
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


# Nombre de clients actuellement connectes (affichage console).
verrou_compteur = threading.Lock()
compteur_clients = 0


def gerer_client(conn, addr):
    global compteur_clients

    client = addr[0] + ":" + str(addr[1])

    with verrou_compteur:
        compteur_clients += 1
        total = compteur_clients

    print("Connecte a", client, "(", total, "client(s) actif(s) )")

    # Delai maximum entre deux messages du client. L'application
    # envoie un "PING" toutes les 600 ms quand on n'utilise pas
    # le clavier : au-dela de 5 secondes sans rien recevoir, on
    # considere que la connexion est perdue.
    DELAI_PING = 5.0
    conn.settimeout(DELAI_PING)

    f = conn.makefile("r", encoding="utf-8", newline="\n")

    premiere = f.readline().strip()
    if premiere != "HELLO":
        print("Poignee de main invalide, deconnexion.")
        conn.close()
        with verrou_compteur:
            compteur_clients -= 1
        return

    try:
        conn.sendall(b"OK\n")
    except Exception:
        pass
    print("Application connectee :", client,
          "( toucher = souris, boutons = clavier )")

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
            traiter(ligne, client)

    except socket.timeout:
        perdu = True
        print("Client injoignable (aucun message depuis",
              int(DELAI_PING), "s), connexion consideree perdue :",
              client)

    except ConnectionResetError:
        perdu = True
        print("Connexion interrompue par le client :", client)

    except Exception as e:
        perdu = True
        print("Erreur de connexion", client, ":", e)

    try:
        conn.close()
    except Exception:
        pass

    # Relache UNIQUEMENT les touches maintenues par CE client :
    # les autres clients connectes ne sont pas perturbes.
    relacher_touche_client(client)

    with verrou_compteur:
        compteur_clients -= 1
        total = compteur_clients

    if perdu:
        print("Connexion perdue :", client, "-",
              total, "client(s) restant(s)")
    else:
        print("Deconnecte :", client, "-",
              total, "client(s) restant(s)")


def main():
    ip = adresse_ip_locale()

    serveur = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    serveur.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    try:
        serveur.bind(("", PORT))
    except OSError as e:
        print("Impossible d'ouvrir le port", PORT, ":", e)
        sys.exit(1)

    serveur.listen(8)

    print("=" * 52)
    print("  Serveur clavier/souris - Clavier Bluetooth")
    print("  Systeme :", SYSTEME, "(multi-clients)")
    print("  Entre cette adresse IP dans l'application :")
    print()
    print("      " + ip)
    print()
    print("  Port :", PORT)
    print("  Plusieurs appareils peuvent se connecter")
    print("  en meme temps.")
    print("  Ctrl+C pour arreter.")
    print("=" * 52)

    while True:
        try:
            conn, addr = serveur.accept()
        except KeyboardInterrupt:
            print("\nArret du serveur.")
            return

        # Un fil par client : les autres restent connectes meme
        # si celui-ci bloque ou se deconnecte.
        fil = threading.Thread(
            target=gerer_client, args=(conn, addr), daemon=True)
        fil.start()


if __name__ == "__main__":
    main()