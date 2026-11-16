package com.gabriel.clavierbluetooth;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewConfiguration;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.IBinder;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.MotionEvent;
import android.widget.*;
import android.bluetooth.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;

public class MainActivity extends Activity {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHidService hidService;
    private boolean serviceBound = false;
    private boolean hidDisponible = false;

    private TextView statut;
    private TextView connexion;
    private Spinner listeAppareils;

    private ArrayList<BluetoothDevice> appareils;
    private ArrayAdapter<String> listeAdapter;

    private HashMap<View, KeySpec> touchesActives =
	new HashMap<View, KeySpec>();

    private LinearLayout clavier;

    /*
     * Conteneur dedie aux rangées de touches : c'est LUI SEUL
     * qui est vidé et reconstruit quand on change de
     * disposition. Le reste de l'interface (statut, sections
     * Bluetooth/WiFi, pavé tactile, sélecteurs) reste en
     * place : plus besoin de recreate(), donc plus de flash
     * ni de retour en haut de la page.
     */
    private LinearLayout zoneTouches;

    /* Actions personnalisables de la disposition GAMING. */
    private LinearLayout zoneActions;
    private ArrayList<ActionPersonnalisee> actionsPersonnalisees =
        new ArrayList<ActionPersonnalisee>();
    private boolean modeModificationActions = false;
    private static final String PREF_ACTIONS_GAMING = "actions_gaming";

    private static class ActionPersonnalisee {
        String nom;
        String touche;
        int usage;

        ActionPersonnalisee(String n, String t, int u) {
            nom = n;
            touche = t;
            usage = u;
        }
    }

    private float pavX;
    private float pavY;
    private float pavDeuxY;
    private boolean pavDeplacement;
    private boolean pavDeuxDoigts;
    private boolean pavBoutonGaucheMaintenu;
    private boolean pavBoutonDroitMaintenu;
    private boolean pavBoutonMoletteMaintenu;
    private boolean pavDoubleClicGlisse;
    private long pavDernierTap = 0;
    private float pavDernierTapX;
    private float pavDernierTapY;

    private int couleurFond = Color.rgb(35, 35, 35);
    private int couleurTouche = Color.rgb(75, 75, 75);
    private int couleurModificateur = Color.rgb(45, 80, 115);
    private int couleurSpeciale = Color.rgb(95, 65, 45);
    private int couleurTexte = Color.WHITE;

    private static final int DEMANDE_BLUETOOTH = 1;
    private static final int DEMANDE_VISIBILITE = 2;

    private static class KeySpec {
        int usage;
        int modifier;

        KeySpec(int u, int m) {
            usage = u;
            modifier = m;
        }
    }

    private BluetoothHidService.Listener serviceListener =
	new BluetoothHidService.Listener() {
		public void onStatusChanged(String message, String connectionText) {
			afficher(message);
			afficherConnexion(connectionText);
		}
	};

    private ServiceConnection connectionService = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            BluetoothHidService.LocalBinder local =
                (BluetoothHidService.LocalBinder) binder;
            hidService = local.getService();
            serviceBound = true;
            hidService.setListener(serviceListener);
            afficher(hidService.getStatus());
            afficherConnexion(hidService.getConnectionText());
            hidService.requestHid();
        }

        public void onServiceDisconnected(ComponentName name) {
            if (hidService != null) {
                hidService.setListener(null);
            }
            hidService = null;
            serviceBound = false;
            afficher("Service Bluetooth interrompu.");
            afficherConnexion("Reconnexion du service en attente.");
        }
    };

	private BroadcastReceiver fermetureReceiver =
    new BroadcastReceiver() {
        public void onReceive(
            android.content.Context context,
            Intent intent) {

            if (BluetoothHidService.ACTION_FERMER.equals(
					intent.getAction())) {

                finish();
            }
        }
    };

	private Spinner selecteurDisposition;

	private String dispositionClavier = "AZERTY";

	private static final String PREF_DISPOSITION =
    "disposition_clavier";

	/* ------------------- Mode WiFi (serveur Python) ------------------- */

	private static final String PREF_MODE_WIFI = "mode_wifi";
	private static final String PREF_IP_WIFI = "ip_wifi";

	private Spinner selecteurMode;
	private EditText champIp;
	private boolean modeWifi = false;
	private WifiClient clientWifi = new WifiClient();

	/* Sections de l'interface, affichees selon le mode choisi. */
	private LinearLayout sectionBluetooth;
	private LinearLayout sectionWifi;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		/*
		 * L'API BluetoothHidDevice (rôle clavier HID) n'existe
		 * publiquement qu'à partir d'Android 9 (API 28).
		 * L'interface reste utilisable partout ; seul le mode
		 * HID est désactivé sur les versions plus anciennes
		 * (où le service planterait et se relancerait en boucle).
		 */
		hidDisponible = android.os.Build.VERSION.SDK_INT >= 28;

		IntentFilter filtre = new IntentFilter(
			BluetoothHidService.ACTION_FERMER
		);
		registerReceiver(fermetureReceiver, filtre);

		dispositionClavier = getSharedPreferences(
			"clavier_bluetooth",
			MODE_PRIVATE
		).getString(PREF_DISPOSITION, "AZERTY");

		modeWifi = getSharedPreferences(
			"clavier_bluetooth",
			MODE_PRIVATE
		).getBoolean(PREF_MODE_WIFI, false);

        chargerActionsPersonnalisees();

		creerInterface();

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		if (bluetoothAdapter == null) {
			afficher("Bluetooth indisponible.");
			return;
		}

		if (bluetoothAdapter.isEnabled()) {
			actualiserAppareils();
		}

		if (hidDisponible) {
			Intent serviceIntent =
				new Intent(this, BluetoothHidService.class);
			startService(serviceIntent);
			bindService(serviceIntent, connectionService, BIND_AUTO_CREATE);
		}

		if (!hidDisponible) {
			afficher("Interface complète disponible, mais le clavier HID "
				+ "nécessite Android 9 minimum.");
		} else if (bluetoothAdapter.isEnabled()) {
			afficher("Démarrage du service Bluetooth...");
		} else {
			afficher("Active le Bluetooth.");
		}
	}

    /*
     * Interface générale.
     */
    private void creerInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(8, 8, 8, 8);


        ScrollView scroll = new ScrollView(this);

        clavier = new LinearLayout(this);
        clavier.setOrientation(LinearLayout.VERTICAL);
        clavier.setMotionEventSplittingEnabled(true);

        TextView titre = new TextView(this);
        titre.setText("Clavier Bluetooth");
        titre.setTextSize(22);
        titre.setTextColor(Color.WHITE);
        titre.setPadding(4, 8, 4, 8);
        clavier.addView(titre);

        statut = new TextView(this);
        statut.setTextColor(Color.rgb(100, 220, 120));
        statut.setTextSize(14);
        statut.setText("Initialisation...");
        clavier.addView(statut);

        connexion = new TextView(this);
        connexion.setTextColor(Color.WHITE);
        connexion.setTextSize(14);
        connexion.setText("Non connecté.");
        /*
         * Mode de connexion : Bluetooth HID (Android 9+) ou
         * WiFi via le serveur Python server_wifi.py sur le PC.
         * Le selecteur est tout en haut ; seule la section du
         * mode choisi est affichee.
         */
        ajouterTitreDans(clavier, "MODE DE CONNEXION");

        selecteurMode = new Spinner(this);

        String[] modes = {
            "Bluetooth HID",
            "Wi-Fi (serveur Python)"
        };

        ArrayAdapter<String> adapterMode =
            new ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            modes
        );

        adapterMode.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );

        selecteurMode.setAdapter(adapterMode);
        selecteurMode.setSelection(modeWifi ? 1 : 0);

        clavier.addView(selecteurMode);
        selecteurMode.setOnItemSelectedListener(
            new android.widget.AdapterView.OnItemSelectedListener() {

                public void onItemSelected(
                    android.widget.AdapterView<?> parent,
                    View view,
                    int position,
                    long id) {

                    boolean nouveauMode = (position == 1);

                    if (nouveauMode != modeWifi) {
                        modeWifi = nouveauMode;
                        relacherToutesLesTouches();

                        /* Quitter le WiFi proprement si connecte. */
                        if (!modeWifi && clientWifi.estConnecte()) {
                            deconnecterWifi();
                        }

                        getSharedPreferences(
                            "clavier_bluetooth",
                            MODE_PRIVATE
                        ).edit()
                            .putBoolean(PREF_MODE_WIFI, modeWifi)
                            .apply();

                        if (modeWifi) {
                            afficher(
                                "Mode Wi-Fi : lance server_wifi.py sur le PC "
                                + "puis Connecter.");
                        } else {
                            afficher("Mode Bluetooth HID selectionne.");
                        }
                    }

                    majSectionsVisibilite();
                }

                public void onNothingSelected(
                    android.widget.AdapterView<?> parent) {
                }
            }
        );

        /* ---------------- Section BLUETOOTH ---------------- */

        sectionBluetooth = new LinearLayout(this);
        sectionBluetooth.setOrientation(LinearLayout.VERTICAL);
        clavier.addView(sectionBluetooth);

        ajouterTitreDans(sectionBluetooth, "BLUETOOTH HID");

        /* Texte d'etat Bluetooth : uniquement dans cette section. */
        sectionBluetooth.addView(connexion);

        ajouterBouton(
            sectionBluetooth,
            "Activer Bluetooth",
            new View.OnClickListener() {
                public void onClick(View v) {
                    activerBluetooth();
                }
            }
        );

        ajouterBouton(
            sectionBluetooth,
            "Activer le clavier HID",
            new View.OnClickListener() {
                public void onClick(View v) {
                    demanderProfilHID();
                }
            }
        );

        ajouterBouton(
            sectionBluetooth,
            "Rendre détectable",
            new View.OnClickListener() {
                public void onClick(View v) {
                    rendreDetectable();
                }
            }
        );

        ajouterBouton(
            sectionBluetooth,
            "Actualiser les ordinateurs",
            new View.OnClickListener() {
                public void onClick(View v) {
                    actualiserAppareils();
                }
            }
        );

        appareils = new ArrayList<BluetoothDevice>();

        listeAdapter = new ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            new ArrayList<String>()
        );

        listeAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );

        listeAppareils = new Spinner(this);
        listeAppareils.setAdapter(listeAdapter);
        sectionBluetooth.addView(listeAppareils);

        ajouterBouton(
            sectionBluetooth,
            "Connecter",
            new View.OnClickListener() {
                public void onClick(View v) {
                    connecter();
                }
            }
        );

        ajouterBouton(
            sectionBluetooth,
            "Déconnecter",
            new View.OnClickListener() {
                public void onClick(View v) {
                    deconnecter();
                }
            }
        );

        /* ---------------- Section WIFI ---------------- */

        sectionWifi = new LinearLayout(this);
        sectionWifi.setOrientation(LinearLayout.VERTICAL);
        clavier.addView(sectionWifi);

        ajouterTitreDans(sectionWifi, "Wi-Fi (serveur Python)");

        String ipMemorisee = getSharedPreferences(
            "clavier_bluetooth",
            MODE_PRIVATE
        ).getString(PREF_IP_WIFI, "");

        champIp = new EditText(this);
        champIp.setHint("Adresse IP du PC (affichée par server_wifi.py)");
        champIp.setText(ipMemorisee);
        champIp.setSingleLine(true);
        sectionWifi.addView(champIp);

        ajouterBouton(
            sectionWifi,
            "Connecter",
            new View.OnClickListener() {
                public void onClick(View v) {
                    connecterWifi();
                }
            }
        );

        ajouterBouton(
            sectionWifi,
            "Déconnecter",
            new View.OnClickListener() {
                public void onClick(View v) {
                    deconnecterWifi();
                }
            }
        );

        /* Quitter reste commun aux deux modes. */
        ajouterBouton(
            clavier,
            "Quitter",
            new View.OnClickListener() {
                public void onClick(View v) {
                    quitter();
                }
            }
        );

        majSectionsVisibilite();

        ajouterTitreDans(clavier, "PAVÉ TACTILE");

        final PavetactileView pavetactile = new PavetactileView();
        LinearLayout.LayoutParams pavParams =
            new LinearLayout.LayoutParams(-1, dp(175));
        pavParams.setMargins(dp(2), dp(3), dp(2), dp(3));
        clavier.addView(pavetactile, pavParams);

        LinearLayout boutonsSouris = new LinearLayout(this);
        boutonsSouris.setOrientation(LinearLayout.HORIZONTAL);
		    ajouterBoutonSourisMaintenu(boutonsSouris, "Clic molette", 4);
        ajouterBoutonSourisMaintenu(boutonsSouris, "Clic gauche", 1);
        ajouterBoutonSourisMaintenu(boutonsSouris, "Clic droit", 2);
        clavier.addView(boutonsSouris);

		selecteurDisposition = new Spinner(this);

		String[] dispositions = {
			"AZERTY",
			"QWERTY",
			"GAMING"
		};

		ArrayAdapter<String> adapterDisposition =
			new ArrayAdapter<String>(
			this,
			android.R.layout.simple_spinner_item,
			dispositions
		);

		adapterDisposition.setDropDownViewResource(
			android.R.layout.simple_spinner_dropdown_item
		);

		selecteurDisposition.setAdapter(adapterDisposition);

		if ("QWERTY".equals(dispositionClavier)) {
			selecteurDisposition.setSelection(1);
		} else if ("GAMING".equals(dispositionClavier)) {
			selecteurDisposition.setSelection(2);
		} else {
			selecteurDisposition.setSelection(0);
		}

		clavier.addView(selecteurDisposition);
		selecteurDisposition.setOnItemSelectedListener(
			new android.widget.AdapterView.OnItemSelectedListener() {

				public void onItemSelected(
					android.widget.AdapterView<?> parent,
					View view,
					int position,
					long id) {

					String nouvelleDisposition =
						position == 1 ? "QWERTY"
						: position == 2 ? "GAMING"
						: "AZERTY";

					if (!nouvelleDisposition.equals(dispositionClavier)) {

						dispositionClavier = nouvelleDisposition;

						getSharedPreferences(
							"clavier_bluetooth",
							MODE_PRIVATE
						).edit()
							.putString(PREF_DISPOSITION, dispositionClavier)
							.apply();

						/*
						 * Relache toutes les touches : les boutons
						 * de l'ancienne disposition vont etre
						 * detruits, leurs listeners ne doivent plus
						 * rien envoyer.
						 */
						relacherToutesLesTouches();

						/*
						 * Reconstruit UNIQUEMENT la zone des
						 * touches : pas de recreate(), l'interface
						 * ne clignote pas et le ScrollView garde
						 * sa position de defilement.
						 */
						reconstruireTouches();
					}
				}

				public void onNothingSelected(
					android.widget.AdapterView<?> parent) {
				}
			}
		);

        /*
         * Zone des touches de clavier : tout ce qui suit le
         * sélecteur de disposition est construit ici, et sera
         * reconstruit en cas de changement de disposition.
         */
        zoneTouches = new LinearLayout(this);
        zoneTouches.setOrientation(LinearLayout.VERTICAL);
        zoneTouches.setMotionEventSplittingEnabled(true);
        clavier.addView(zoneTouches);

        reconstruireTouches();

        scroll.addView(clavier);
        root.addView(
            scroll,
            new LinearLayout.LayoutParams(-1, 0, 1)
        );

        setContentView(root);
    }

    /*
     * Vide et reconstruit la zone des touches pour la
     * disposition courante. Appelee au demarrage puis a chaque
     * changement de disposition (sans recreer l'Activity).
     */
    private void reconstruireTouches() {
        zoneTouches.removeAllViews();
        touchesActives.clear();

        

        if ("GAMING".equals(dispositionClavier)) {
            creerClavierGaming();
        } else {
            construireClavierComplet();
        }
    }

    /*
     * Clavier complet (AZERTY ou QWERTY) : touches de
     * fonction, chiffres, lettres, modificateurs, navigation
     * et pave numerique.
     */
    private void construireClavierComplet() {
        /*
         * Ligne des touches de fonction.
         */
        ajouterRangee(
            new String[] {
                "Esc","F1","F2","F3","F4","F5","F6",
                "F7","F8","F9","F10","F11","F12","Suppr"
            },
            new int[] {
                0x29,0x3A,0x3B,0x3C,0x3D,0x3E,0x3F,
                0x40,0x41,0x42,0x43,0x44,0x45,0x4C
            }
        );

        /*
         * Chiffres et caractères AZERTY.
         * Maj permet d'obtenir les chiffres.
         */
        if ("AZERTY".equals(dispositionClavier)) {

            ajouterRangee(
                new String[] {
                    "²","1\n&","2\né ~","3\n\" #","4\n' {",
                    "5\n( [","6\n- |","7\nè `","8\n_ \\",
                    "9\nç ^","0\nà @","°\n) ]","+\n= }","⌫"
                },
                new int[] {
                    0x35,0x1E,0x1F,0x20,0x21,
                    0x22,0x23,0x24,0x25,0x26,
                    0x27,0x2D,0x2E,0x2A
                }
            );

        } else {

            ajouterRangee(
                new String[] {
                    "~\n`","!\n1","@\n2","#\n3","$\n4",
                    "%\n5","^\n6","&\n7","*\n8",
                    "(\n9",")\n0","_\n-","+\n=","⌫"
                },
                new int[] {
                    0x35,0x1E,0x1F,0x20,0x21,
                    0x22,0x23,0x24,0x25,0x26,
                    0x27,0x2D,0x2E,0x2A
                }
            );
        }

        /*
         * Première ligne AZERTY.
         */
        LinearLayout row = nouvelleRangee();
        ajouterTouche(row, "Tab", 0x2B, 0, 1.1f);

        ajouterRangeeDans(
            row,
            new String[] {
                "AZERTY".equals(dispositionClavier)
                ? "A" : "Q",
                "AZERTY".equals(dispositionClavier)
                ? "Z" : "W",
                "E","R","T","Y","U","I","O","P"
            },
            new int[] {
                0x14,0x1A,0x08,0x15,0x17,
                0x1C,0x18,0x0C,0x12,0x13
            }
        );

        if ("AZERTY".equals(dispositionClavier)) {
            ajouterTouche(row, "¨\n^", 0x2F, 0, 0.8f);
            ajouterTouche(row, "£\n$ ¤", 0x30, 0, 0.8f);
        } else {
            ajouterTouche(row, "{\n[", 0x2F, 0, 0.8f);
            ajouterTouche(row, "}\n]", 0x30, 0, 0.8f);
        }
        ajouterFinRangee(row);

        /*
         * Deuxième ligne AZERTY.
         */
        row = nouvelleRangee();
        ajouterTouche(row, "Caps", 0x39, 0, 1.4f);

        ajouterRangeeDans(
            row,
            new String[] {
                "AZERTY".equals(dispositionClavier) ? "Q" : "A",
                "S","D","F","G","H","J","K","L",
                "AZERTY".equals(dispositionClavier) ? "M" : ":\n;"
            },
            new int[] {
                0x04,0x16,0x07,0x09,0x0A,
                0x0B,0x0D,0x0E,0x0F,0x33
            }
        );

        if ("AZERTY".equals(dispositionClavier)) {
            ajouterTouche(row, "%\nù", 0x34, 0, 0.8f);
            ajouterTouche(row, "µ\n*", 0x31, 0, 0.8f);
        } else {
            ajouterTouche(row, "\"\n'", 0x34, 0, 0.8f);
            ajouterTouche(row, "|\n\\", 0x31, 0, 0.8f);
        }
        ajouterTouche(row, "Entrée", 0x28, 0, 1.5f);
        ajouterFinRangee(row);

        /*
         * Troisième ligne AZERTY.
         */
        row = nouvelleRangee();
        ajouterTouche(row, "Maj", 0, 0x02, 1.5f);

        if ("AZERTY".equals(dispositionClavier)) {

            ajouterRangeeDans(
                row,
                new String[] {
                    ">\n<","W","X","C","V","B","N","?\n,",".\n;","/\n:","§\n!"
                },
                new int[] {
                    0x64,0x1D,0x1B,0x06,0x19,0x05,
                    0x11,0x10,0x36,0x37,0x38
                }
            );

        } else {

            ajouterRangeeDans(
                row,
                new String[] {
                    ">\n<","Z","X","C","V","B","N","M","<\n,",">\n.","?\n/"
                },
                new int[] {
                    0x64,0x1D,0x1B,0x06,0x19,0x05,
                    0x11,0x10,0x36,0x37,0x38
                }
            );
        }

        ajouterTouche(row, "Maj", 0, 0x20, 1.5f);
        ajouterFinRangee(row);

        /*
         * Ligne des modificateurs et espace.
         */
        row = nouvelleRangee();
        ajouterTouche(row, "Ctrl", 0, 0x01, 1.2f);
        ajouterTouche(row, "Win", 0, 0x08, 1.0f);
        ajouterTouche(row, "Alt", 0, 0x04, 1.0f);
        ajouterTouche(row, "Espace", 0x2C, 0, 5.0f);
        ajouterTouche(row, "AltGr", 0, 0x40, 1.0f);
        ajouterTouche(row, "Menu", 0x65, 0, 1.0f);
        ajouterTouche(row, "Ctrl", 0, 0x10, 1.2f);
        ajouterFinRangee(row);

        /*
         * Touches de navigation.
         */
        ajouterTitre("NAVIGATION");

        row = nouvelleRangee();
        ajouterTouche(row, "←", 0x50, 0, 1);
        ajouterTouche(row, "→", 0x4F, 0, 1);
        ajouterTouche(row, "↑", 0x52, 0, 1);
        ajouterTouche(row, "↓", 0x51, 0, 1);
        ajouterFinRangee(row);

        ajouterRangee(
            new String[] {
                "Inser","Orig","Pg↑","Fin","Pg↓"
            },
            new int[] {
                0x49,0x4A,0x4B,0x4D,0x4E
            }
        );

        /*
         * Pavé numérique.
         */
        ajouterTitre("PAVÉ NUMÉRIQUE");

        ajouterRangee(
            new String[] {
                "Verr Num","/","*","-"
            },
            new int[] {
                0x53,0x54,0x55,0x56
            }
        );

        ajouterRangee(
            new String[] {"7","8","9","+"},
            new int[] {0x5F,0x60,0x61,0x57}
        );

        ajouterRangee(
            new String[] {"4","5","6",""},
            new int[] {0x5C,0x5D,0x5E,0}
        );

        ajouterRangee(
            new String[] {"1","2","3","Entrée"},
            new int[] {0x59,0x5A,0x5B,0x58}
        );

        ajouterRangee(
            new String[] {"0",",","",""},
            new int[] {0x62,0x63,0,0}
        );
    }

    /*
     * Disposition GAMING : touches essentielles pour jouer
     * (Minecraft et jeux similaires). Les usages HID sont
     * positionnels : la touche "Z/W" envoie l'usage 0x1A, que
     * Windows interprète comme 'z' en AZERTY ou 'w' en QWERTY.
     * Les raccourcis du jeu fonctionnent donc dans les deux cas.
     */
    private void creerClavierGaming() {
    LinearLayout rangeeJoysticks = new LinearLayout(this);
    rangeeJoysticks.setOrientation(LinearLayout.HORIZONTAL);
    rangeeJoysticks.setGravity(android.view.Gravity.LEFT);
    rangeeJoysticks.setMotionEventSplittingEnabled(true);

    JoystickGaming joystickGauche = new JoystickGaming(this, false);
    JoystickGaming joystickDroit = new JoystickGaming(this, true);

    LinearLayout.LayoutParams joystickParams =
        new LinearLayout.LayoutParams(dp(190), dp(190));

    joystickParams.setMargins(dp(5), dp(4), dp(5), dp(6));

    rangeeJoysticks.addView(joystickGauche, joystickParams);
    rangeeJoysticks.addView(joystickDroit, joystickParams);

    zoneTouches.addView(rangeeJoysticks);

        LinearLayout row = nouvelleRangee();
        ajouterTouche(row, "Espace\nSaut", 0x2C, 0, 1.6f);
        ajouterTouche(row, "Maj\nAccroupi", 0, 0x02, 1.6f);
        ajouterTouche(row, "Ctrl\nSprint", 0, 0x01, 1.6f);
        ajouterFinRangee(row);

        construireSectionActions();

        ajouterTitre("BARRE RAPIDE (1-9)");

        ajouterRangee(
            new String[] {"1","2","3","4","5","6","7","8","9","0"},
            new int[] {
                0x1E,0x1F,0x20,0x21,0x22,
                0x23,0x24,0x25,0x26,0x27
            }
        );
    }

    /* ---------------- Actions GAMING personnalisables ---------------- */

    private void chargerActionsPersonnalisees() {
        actionsPersonnalisees.clear();
        String sauvegarde = getSharedPreferences(
            "clavier_bluetooth", MODE_PRIVATE
        ).getString(PREF_ACTIONS_GAMING, null);

        if (sauvegarde != null) {
            try {
                org.json.JSONArray tableau = new org.json.JSONArray(sauvegarde);
                for (int i = 0; i < tableau.length(); i++) {
                    org.json.JSONObject objet = tableau.getJSONObject(i);
                    actionsPersonnalisees.add(new ActionPersonnalisee(
                        objet.getString("nom"),
                        objet.getString("touche"),
                        objet.getInt("usage")
                    ));
                }
                return;
            } catch (Exception e) {
                actionsPersonnalisees.clear();
            }
        }

        // Première utilisation : reprend les actions Gaming existantes.
        ajouterActionDefaut("Inventaire", "E", 0x08);
        ajouterActionDefaut("Jeter", "A / Q", 0x14);
        ajouterActionDefaut("2e main", "F", 0x09);
        ajouterActionDefaut("Chat", "T", 0x17);
        ajouterActionDefaut("Commande", "/", 0x38);
        ajouterActionDefaut("Menu", "Esc", 0x29);
        ajouterActionDefaut("Joueurs", "Tab", 0x2B);
        ajouterActionDefaut("Cacher interface", "F1", 0x3A);
        ajouterActionDefaut("Capture d'écran", "F2", 0x3B);
        ajouterActionDefaut("Debug", "F3", 0x3C);
        ajouterActionDefaut("Camera", "F5", 0x3E);
        ajouterActionDefaut("Plein Écran", "F11", 0x44);
        ajouterActionDefaut("S'accroupir", "Shift", 0xE1);
        ajouterActionDefaut("Courir", "Ctrl", 0xE0);
        ajouterActionDefaut("Entrée", "Entrée", 0x28);
        ajouterActionDefaut("Retour arrière", "⌫", 0x2A);
        sauvegarderActionsPersonnalisees();
    }

    private void ajouterActionDefaut(String nom, String touche, int usage) {
        actionsPersonnalisees.add(new ActionPersonnalisee(nom, touche, usage));
    }

    private void sauvegarderActionsPersonnalisees() {
        org.json.JSONArray tableau = new org.json.JSONArray();
        try {
            for (ActionPersonnalisee action : actionsPersonnalisees) {
                org.json.JSONObject objet = new org.json.JSONObject();
                objet.put("nom", action.nom);
                objet.put("touche", action.touche);
                objet.put("usage", action.usage);
                tableau.put(objet);
            }
            getSharedPreferences("clavier_bluetooth", MODE_PRIVATE).edit()
                .putString(PREF_ACTIONS_GAMING, tableau.toString())
                .apply();
        } catch (Exception e) {
            afficher("Impossible de sauvegarder les actions.");
        }
    }

    private void construireSectionActions() {
        ajouterTitre("ACTIONS");

        LinearLayout entete = new LinearLayout(this);
        entete.setOrientation(LinearLayout.HORIZONTAL);
        Button boutonMode = new Button(this);
        boutonMode.setText(modeModificationActions ? "Terminer" : "Modifier les actions");
        boutonMode.setAllCaps(false);
        entete.addView(boutonMode, new LinearLayout.LayoutParams(-1, dp(44)));
        zoneTouches.addView(entete);

        boutonMode.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                modeModificationActions = !modeModificationActions;
                relacherToutesLesTouches();
                reconstruireTouches();
            }
        });

        zoneActions = new LinearLayout(this);
        zoneActions.setOrientation(LinearLayout.VERTICAL);
        zoneTouches.addView(zoneActions);
        afficherActionsPersonnalisees();
    }

    private void afficherActionsPersonnalisees() {
        if (zoneActions == null) return;
        zoneActions.removeAllViews();

        if (modeModificationActions) {
            Button ajouter = new Button(this);
            ajouter.setText("+ Ajouter une action");
            ajouter.setAllCaps(false);
            zoneActions.addView(ajouter);
            ajouter.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    afficherDialogueAction(-1);
                }
            });

            for (int i = 0; i < actionsPersonnalisees.size(); i++) {
                final int index = i;
                ActionPersonnalisee action = actionsPersonnalisees.get(i);
                LinearLayout ligne = new LinearLayout(this);
                ligne.setOrientation(LinearLayout.HORIZONTAL);
                ligne.setGravity(android.view.Gravity.CENTER_VERTICAL);

                TextView texte = new TextView(this);
                texte.setText(action.nom + "  [" + action.touche + "]");
                texte.setTextColor(Color.WHITE);
                texte.setTextSize(13);
                ligne.addView(texte, new LinearLayout.LayoutParams(0, dp(46), 1));

                ajouterPetitBouton(ligne, "↑", new View.OnClickListener() {
                    public void onClick(View v) { deplacerAction(index, -1); }
                });
                ajouterPetitBouton(ligne, "↓", new View.OnClickListener() {
                    public void onClick(View v) { deplacerAction(index, 1); }
                });
                ajouterPetitBouton(ligne, "✎", new View.OnClickListener() {
                    public void onClick(View v) { afficherDialogueAction(index); }
                });
                ajouterPetitBouton(ligne, "×", new View.OnClickListener() {
                    public void onClick(View v) { supprimerAction(index); }
                });
                zoneActions.addView(ligne);
            }
        } else {
            LinearLayout ligne = null;
            for (int i = 0; i < actionsPersonnalisees.size(); i++) {
                if (i % 6 == 0) {
                    ligne = nouvelleRangeeDans(zoneActions);
                }
                ActionPersonnalisee action = actionsPersonnalisees.get(i);
                ajouterBoutonAction(ligne, action);
            }
        }
    }

    private LinearLayout nouvelleRangeeDans(LinearLayout parent) {
        LinearLayout ligne = new LinearLayout(this);
        ligne.setOrientation(LinearLayout.HORIZONTAL);
        ligne.setMotionEventSplittingEnabled(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, dp(2), 0, dp(2));
        parent.addView(ligne, params);
        return ligne;
    }

    private void ajouterPetitBouton(LinearLayout ligne, String texte,
                                   View.OnClickListener listener) {
        Button bouton = new Button(this);
        bouton.setText(texte);
        bouton.setTextSize(12);
        bouton.setAllCaps(false);
        ligne.addView(bouton, new LinearLayout.LayoutParams(dp(42), dp(42)));
        bouton.setOnClickListener(listener);
    }

    private void ajouterBoutonAction(LinearLayout ligne,
                                     final ActionPersonnalisee action) {
        Button bouton = new Button(this);
        bouton.setText(action.touche + "\n" + action.nom);
        bouton.setTextColor(couleurTexte);
        bouton.setTextSize(11);
        bouton.setAllCaps(false);
        bouton.setPadding(0, 0, 0, 0);
        bouton.setMinWidth(0);
        bouton.setMinimumWidth(0);
        bouton.setMinHeight(0);

        // Même rendu que les touches du clavier : fond arrondi et
        // changement de couleur à l'appui, compatible Android 4.
        android.graphics.drawable.GradientDrawable fondAction =
            new android.graphics.drawable.GradientDrawable();
        fondAction.setColor(couleurTouche);
        fondAction.setCornerRadius(dp(5));

        android.graphics.drawable.GradientDrawable fondActionPresse =
            new android.graphics.drawable.GradientDrawable();
        fondActionPresse.setColor(Color.argb(220, 130, 170, 210));
        fondActionPresse.setCornerRadius(dp(5));

        android.graphics.drawable.StateListDrawable etatsAction =
            new android.graphics.drawable.StateListDrawable();
        etatsAction.addState(
            new int[] { android.R.attr.state_pressed }, fondActionPresse);
        etatsAction.addState(new int[] {}, fondAction);
        bouton.setBackgroundDrawable(etatsAction);

        bouton.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                int evenement = event.getActionMasked();
                if (evenement == MotionEvent.ACTION_DOWN ||
                    evenement == MotionEvent.ACTION_POINTER_DOWN) {
                    if (!modeModificationActions) {
                        // Les usages HID 0xE0 a 0xE7 representent
                        // les modificateurs (Ctrl, Maj, Alt, Win),
                        // pas des touches ordinaires du tableau HID.
                        int usageClavier = action.usage;
                        int masqueModificateur = 0;

                        if (usageClavier >= 0xE0 && usageClavier <= 0xE7) {
                            masqueModificateur = 1 << (usageClavier - 0xE0);
                            usageClavier = 0;
                        }

                        touchesActives.put(
                            v,
                            new KeySpec(usageClavier, masqueModificateur)
                        );
                        v.setPressed(true);
                        envoyerRapportClavier();
                    }
                    return true;
                }
                if (evenement == MotionEvent.ACTION_UP ||
                    evenement == MotionEvent.ACTION_POINTER_UP ||
                    evenement == MotionEvent.ACTION_CANCEL) {
                    touchesActives.remove(v);
                    v.setPressed(false);
                    envoyerRapportClavier();
                    return true;
                }
                return true;
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        params.setMargins(dp(1), 0, dp(1), 0);
        ligne.addView(bouton, params);
    }

    private void deplacerAction(int index, int direction) {
        int destination = index + direction;
        if (destination < 0 || destination >= actionsPersonnalisees.size()) return;
        ActionPersonnalisee action = actionsPersonnalisees.remove(index);
        actionsPersonnalisees.add(destination, action);
        sauvegarderActionsPersonnalisees();
        afficherActionsPersonnalisees();
    }

    private void supprimerAction(final int index) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Supprimer cette action ?")
            .setMessage(actionsPersonnalisees.get(index).nom)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface dialog, int which) {
                    actionsPersonnalisees.remove(index);
                    sauvegarderActionsPersonnalisees();
                    afficherActionsPersonnalisees();
                }
            }).show();
    }

    private void afficherDialogueAction(final int index) {
        final boolean nouvelle = index < 0;
        final EditText champNom = new EditText(this);
        champNom.setSingleLine(true);
        champNom.setHint("Nom du bouton (ex. Inventaire)");

        final String[] touches = {
            "A","B","C","D","E","F","G","H","I","J","K","L","M",
            "N","O","P","Q","R","S","T","U","V","W","X","Y","Z",
            "0","1","2","3","4","5","6","7","8","9",
            "F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12",
            "Entrée","Espace","Échap","Tab","Retour arrière","Flèche haut",
            "Flèche bas","Flèche gauche","Flèche droite","/"
        };
        final int[] usages = {
            0x04,0x05,0x06,0x07,0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E,0x0F,0x10,
            0x11,0x12,0x13,0x14,0x15,0x16,0x17,0x18,0x19,0x1A,0x1B,0x1C,0x1D,
            0x27,0x1E,0x1F,0x20,0x21,0x22,0x23,0x24,0x25,0x26,
            0x3A,0x3B,0x3C,0x3D,0x3E,0x3F,0x40,0x41,0x42,0x43,0x44,0x45,
            0x28,0x2C,0x29,0x2B,0x2A,0x52,0x51,0x50,0x4F,0x38
        };
        final Spinner selecteur = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_item, touches);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        selecteur.setAdapter(adapter);

        if (!nouvelle) {
            ActionPersonnalisee action = actionsPersonnalisees.get(index);
            champNom.setText(action.nom);
            for (int i = 0; i < usages.length; i++) {
                if (usages[i] == action.usage) { selecteur.setSelection(i); break; }
            }
        }

        LinearLayout contenu = new LinearLayout(this);
        contenu.setOrientation(LinearLayout.VERTICAL);
        int marge = dp(20);
        contenu.setPadding(marge, dp(8), marge, 0);
        contenu.addView(champNom);
        TextView etiquette = new TextView(this);
        etiquette.setText("Touche à envoyer :");
        contenu.addView(etiquette);
        contenu.addView(selecteur);

        new android.app.AlertDialog.Builder(this)
            .setTitle(nouvelle ? "Nouvelle action" : "Modifier l'action")
            .setView(contenu)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface dialog, int which) {
                    String nom = champNom.getText().toString().trim();
                    if (nom.length() == 0) nom = touches[selecteur.getSelectedItemPosition()];
                    int choix = selecteur.getSelectedItemPosition();
                    ActionPersonnalisee action = new ActionPersonnalisee(
                        nom, touches[choix], usages[choix]);
                    if (nouvelle) actionsPersonnalisees.add(action);
                    else actionsPersonnalisees.set(index, action);
                    sauvegarderActionsPersonnalisees();
                    afficherActionsPersonnalisees();
                }
            }).show();
    }

    /*
     * Création des rangées. Toutes les rangées de touches vont
     * dans zoneTouches (pas directement dans clavier) pour que
     * la reconstruction ne touche qu'a cette zone.
     */
    private LinearLayout nouvelleRangee() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setMotionEventSplittingEnabled(true);

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, dp(48));
        p.setMargins(0, dp(2), 0, dp(2));

        zoneTouches.addView(row, p);
        return row;
    }

    private void ajouterFinRangee(LinearLayout row) {
    }

    private void ajouterTitre(String texte) {
        ajouterTitreDans(zoneTouches, texte);
    }

    private void ajouterTitreDans(LinearLayout parent, String texte) {
        TextView titre = new TextView(this);
        titre.setText(texte);
        titre.setTextColor(Color.rgb(120, 200, 255));
        titre.setTextSize(15);
        titre.setPadding(3, 12, 3, 5);
        parent.addView(titre);
    }

    /*
     * N'affiche que la section du mode de connexion choisi :
     * tout ce qui concerne le Bluetooth en mode HID, tout ce
     * qui concerne le WiFi en mode WiFi.
     */
    private void majSectionsVisibilite() {
        if (sectionBluetooth != null) {
            sectionBluetooth.setVisibility(
                modeWifi ? View.GONE : View.VISIBLE);
        }
        if (sectionWifi != null) {
            sectionWifi.setVisibility(
                modeWifi ? View.VISIBLE : View.GONE);
        }
    }

    private void ajouterRangee(String[] labels, int[] usages) {
        LinearLayout row = nouvelleRangee();

        for (int i = 0; i < labels.length; i++) {
            if (labels[i].length() == 0 || usages[i] == 0) {
                ajouterTouche(row, "", 0, 0, 1);
            } else {
                ajouterTouche(row, labels[i], usages[i], 0, 1);
            }
        }
    }

    private void ajouterRangeeDans(
        LinearLayout row,
        String[] labels,
        int[] usages) {

        for (int i = 0; i < labels.length; i++) {
            ajouterTouche(row, labels[i], usages[i], 0, 1);
        }
    }

    /*
     * Joystick de déplacement pour le mode GAMING.
     * Les quatre touches virtuelles sont séparées afin de permettre
     * les diagonales (deux touches HID simultanément).
     */
    private class JoystickGaming extends View {

        private Paint peinture = new Paint(3);

        private float axeX;
        private float axeY;
        private float rayon;
        private float centreX;
        private float centreY;

        private View toucheAvant = new View(MainActivity.this);
        private View toucheArriere = new View(MainActivity.this);
        private View toucheGauche = new View(MainActivity.this);
        private View toucheDroite = new View(MainActivity.this);
        
        private boolean utiliserFleches;

        public JoystickGaming(
            android.content.Context context,
            boolean fleches) {

            super(context);

            utiliserFleches = fleches;

            setFocusable(true);
        }

        /*
         * Empêche le ScrollView parent de récupérer
         * les mouvements tactiles du joystick.
         */
        private void bloquerDefilementParent(boolean bloquer) {
            android.view.ViewParent parent = getParent();

            while (parent != null) {
                parent.requestDisallowInterceptTouchEvent(bloquer);
                parent = parent.getParent();
            }
        }

        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            centreX = getWidth() / 2.0f;
            centreY = getHeight() / 2.0f;
            rayon = Math.min(getWidth(), getHeight()) * 0.43f;

            peinture.setStyle(Paint.Style.FILL);
            peinture.setColor(Color.rgb(48, 55, 65));
            canvas.drawCircle(centreX, centreY, rayon, peinture);

            peinture.setStyle(Paint.Style.STROKE);
            peinture.setStrokeWidth(dp(2));
            peinture.setColor(Color.rgb(115, 145, 175));
            canvas.drawCircle(centreX, centreY, rayon, peinture);

            /* Repères directionnels. */
            peinture.setStyle(Paint.Style.FILL);
            peinture.setTextAlign(Paint.Align.CENTER);
            peinture.setTextSize(dp(19));
            peinture.setColor(Color.rgb(175, 195, 215));

            String texteHaut = utiliserFleches ? "▲" : "Z/W";
            String texteBas = utiliserFleches ? "▼" : "S";
            String texteGauche = utiliserFleches ? "◀" : "Q/A";
            String texteDroite = utiliserFleches ? "▶" : "D";

            canvas.drawText(
                texteHaut, centreX,
                centreY - rayon * 0.55f, peinture
            );

            canvas.drawText(
                texteBas, centreX,
                centreY + rayon * 0.78f, peinture
            );

            canvas.drawText(
                texteGauche, centreX - rayon * 0.62f,
                centreY + dp(7), peinture
            );

            canvas.drawText(
                texteDroite, centreX + rayon * 0.62f,
                centreY + dp(7), peinture
            );

            float limite = rayon * 0.58f;
            float px = centreX + axeX * limite;
            float py = centreY + axeY * limite;

            peinture.setColor(Color.rgb(75, 135, 190));
            canvas.drawCircle(px, py, rayon * 0.34f, peinture);

            peinture.setStyle(Paint.Style.STROKE);
            peinture.setStrokeWidth(dp(2));
            peinture.setColor(Color.rgb(180, 215, 245));
            canvas.drawCircle(px, py, rayon * 0.34f, peinture);

            peinture.setStyle(Paint.Style.FILL);
        }

        public boolean onTouchEvent(MotionEvent event) {

            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN ||
                action == MotionEvent.ACTION_MOVE) {

                bloquerDefilementParent(true);

                float dx = event.getX() - centreX;
                float dy = event.getY() - centreY;

                float distance =
                    (float) Math.sqrt(dx * dx + dy * dy);

                float limite = rayon * 0.58f;

                if (distance > limite && distance > 0) {
                    dx = dx * limite / distance;
                    dy = dy * limite / distance;
                }

                axeX = dx / limite;
                axeY = dy / limite;

                actualiserTouches();
                invalidate();

                return true;
            }

            if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_CANCEL ||
                action == MotionEvent.ACTION_POINTER_UP) {

                axeX = 0;
                axeY = 0;

                actualiserTouches();
                invalidate();

                bloquerDefilementParent(false);

                return true;
            }

            return true;
        }

        private void actualiserTouches() {

            touchesActives.remove(toucheAvant);
            touchesActives.remove(toucheArriere);
            touchesActives.remove(toucheGauche);
            touchesActives.remove(toucheDroite);

            // Touches vers le haut
            if (axeY < -0.30f) {
                touchesActives.put(
                    toucheAvant,
                    new KeySpec(
                        utiliserFleches ? 0x52 : 0x1A,
                        0
                    )
                );
            }

            // Touches vers le bas
            else if (axeY > 0.30f) {
                touchesActives.put(
                    toucheArriere,
                    new KeySpec(
                        utiliserFleches ? 0x51 : 0x16,
                        0
                    )
                );
            }

            // Touches vers la gauche
            if (axeX < -0.30f) {
                touchesActives.put(
                    toucheGauche,
                    new KeySpec(
                        utiliserFleches ? 0x50 : 0x04,
                        0
                    )
                );
            }

            // Touches vers la droite
            else if (axeX > 0.30f) {
                touchesActives.put(
                    toucheDroite,
                    new KeySpec(
                        utiliserFleches ? 0x4F : 0x07,
                        0
                    )
                );
            }

            envoyerRapportClavier();
        }
    }

    /*
     * Création d'une touche tactile.
     */
    private void ajouterTouche(
        LinearLayout row,
        String label,
        final int usage,
        final int modifier,
        float poids) {

        final KeySpec spec = new KeySpec(usage, modifier);

        Button bouton = new Button(this);
        bouton.setText(label);
        bouton.setTextColor(couleurTexte);
        bouton.setTextSize(12);
        bouton.setAllCaps(false);

        bouton.setPadding(0, 0, 0, 0);
        bouton.setMinWidth(0);
        bouton.setMinimumWidth(0);
        bouton.setMinHeight(0);

        int couleur;

		if (modifier != 0) {
			couleur = couleurModificateur;
		} else if (usage == 0x28 ||
				   usage == 0x2A ||
				   usage == 0x2B ||
				   usage == 0x29 ||
			       usage == 0x4C) {
			couleur = couleurSpeciale;
		} else {
			couleur = couleurTouche;
		}

		int[][] etats = new int[][] {
			new int[] { android.R.attr.state_pressed },
			new int[] { android.R.attr.state_focused },
			new int[] {}
		};

		int[] couleurs = new int[] {
			Color.argb(220, 130, 170, 210),
			Color.argb(200, 110, 145, 185),
			couleur
		};

		android.content.res.ColorStateList listeCouleurs =
			new android.content.res.ColorStateList(etats, couleurs);

		android.graphics.drawable.GradientDrawable fond =
			new android.graphics.drawable.GradientDrawable();

		fond.setColor(listeCouleurs.getDefaultColor());
		fond.setCornerRadius(dp(5));

		/*
		 * Compatible Android ancien : pas de RippleDrawable du tout.
		 * Un simple fond qui change de couleur selon l'état.
		 */
		android.graphics.drawable.StateListDrawable fondEtats =
			new android.graphics.drawable.StateListDrawable();

		android.graphics.drawable.GradientDrawable enfonce =
			new android.graphics.drawable.GradientDrawable();
		enfonce.setColor(couleurs[0]);
		enfonce.setCornerRadius(dp(5));
		fondEtats.addState(
			new int[] { android.R.attr.state_pressed }, enfonce);

		fondEtats.addState(new int[] {}, fond);

		/* setBackground() n'existe qu'à partir d'API 16,
		 * setBackgroundDrawable() fonctionne partout. */
		bouton.setBackgroundDrawable(fondEtats);

        bouton.setOnTouchListener(
            new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    int action = event.getActionMasked();

                    if (action == MotionEvent.ACTION_DOWN ||
                        action == MotionEvent.ACTION_POINTER_DOWN) {

                        if (usage != 0 || modifier != 0) {
                            touchesActives.put(v, spec);
                            v.setPressed(true);
                            envoyerRapportClavier();
                        }
                        return true;
                    }

                    if (action == MotionEvent.ACTION_UP ||
                        action == MotionEvent.ACTION_POINTER_UP ||
                        action == MotionEvent.ACTION_CANCEL) {

                        touchesActives.remove(v);
                        v.setPressed(false);
                        envoyerRapportClavier();
                        return true;
                    }

                    return true;
                }
            }
        );

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(0, -1, poids);
        p.setMargins(dp(1), 0, dp(1), 0);
        row.addView(bouton, p);
    }

    /*
     * Boutons de souris maintenus pendant que l'on glisse sur le pavé.
     */
    private void ajouterBoutonSourisMaintenu(
        LinearLayout parent, String texte, final int masque) {

        Button bouton = new Button(this);
        bouton.setText(texte);
        bouton.setOnTouchListener(new View.OnTouchListener() {
				public boolean onTouch(View v, MotionEvent event) {
					int action = event.getActionMasked();

					if (action == MotionEvent.ACTION_DOWN) {
						if (masque == 1) {
							pavBoutonGaucheMaintenu = true;
						} else if (masque == 2) {
							pavBoutonDroitMaintenu = true;
						} else {
							pavBoutonMoletteMaintenu = true;
						}
						envoyerSouris(pavBoutonsActifs(), 0, 0, 0);
						v.setPressed(true);
						return true;
					}

					if (action == MotionEvent.ACTION_UP ||
						action == MotionEvent.ACTION_CANCEL) {
						if (masque == 1) {
							pavBoutonGaucheMaintenu = false;
						} else if (masque == 2) {
							pavBoutonDroitMaintenu = false;
						} else {
							pavBoutonMoletteMaintenu = false;
						}
						envoyerSouris(pavBoutonsActifs(), 0, 0, 0);
						v.setPressed(false);
						return true;
					}
					return true;
				}
			});
        parent.addView(bouton,
					   new LinearLayout.LayoutParams(0, -2, 1));
    }

    private int pavBoutonsActifs() {
        int boutons = 0;
        if (pavBoutonGaucheMaintenu || pavDoubleClicGlisse) {
            boutons |= 1;
        }
        if (pavBoutonDroitMaintenu) {
            boutons |= 2;
        }
        if (pavBoutonMoletteMaintenu) {
            boutons |= 4;
        }
        return boutons;
    }

    private class PavetactileView extends View {
        private Paint paint;

        public PavetactileView() {
            super(MainActivity.this);
            paint = new Paint();
            paint.setAntiAlias(true);
            setBackgroundColor(Color.rgb(55, 65, 75));
            setFocusable(true);
        }

        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(Color.WHITE);
            paint.setTextSize(dp(16));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("PAVÉ TACTILE", getWidth() / 2, getHeight() / 2 - dp(8), paint);
            paint.setTextSize(dp(12));
            canvas.drawText("Glisser : déplacer  |  2 doigts : défiler  |  Double-taper : clic gauche", getWidth() / 2,
							getHeight() / 2 + dp(18), paint);
            canvas.drawRect(1, 1, getWidth() - 1, getHeight() - 1, borderPaint());
        }

        private Paint borderPaint() {
            Paint p = new Paint();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(Color.rgb(150, 170, 190));
            return p;
        }

        private void bloquerDefilementParent(boolean bloquer) {
            android.view.ViewParent parent = getParent();
            while (parent != null) {
                parent.requestDisallowInterceptTouchEvent(bloquer);
                parent = parent.getParent();
            }
        }

        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            int count = event.getPointerCount();

            /* Empêche le ScrollView de récupérer les gestes du pavé tactile. */
            if (action == MotionEvent.ACTION_DOWN ||
                action == MotionEvent.ACTION_POINTER_DOWN ||
                action == MotionEvent.ACTION_MOVE) {
                bloquerDefilementParent(true);
            }

            if (action == MotionEvent.ACTION_DOWN) {
                pavX = event.getX(0);
                pavY = event.getY(0);
                pavDeuxY = pavY;
                pavDeplacement = false;
                pavDeuxDoigts = false;

                long maintenant = event.getEventTime();
                long delaiDoubleTap = ViewConfiguration.getDoubleTapTimeout();
                float seuil = dp(40);
                float ddx = pavX - pavDernierTapX;
                float ddy = pavY - pavDernierTapY;

                if (pavDernierTap > 0 &&
                    maintenant - pavDernierTap <= delaiDoubleTap &&
                    ddx * ddx + ddy * ddy <= seuil * seuil) {
                    /* Deuxième appui : maintenir le clic gauche pour le glisser. */
                    pavDoubleClicGlisse = true;
                    pavDernierTap = 0;
                    envoyerSouris(pavBoutonsActifs(), 0, 0, 0);
                } else {
                    pavDoubleClicGlisse = false;
                }
                return true;
            }

            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                if (count >= 2) {
                    pavDeuxDoigts = true;
                    pavDeuxY = (event.getY(0) + event.getY(1)) / 2;
                }
                return true;
            }

            if (action == MotionEvent.ACTION_MOVE) {
                if (count >= 2) {
                    float y = (event.getY(0) + event.getY(1)) / 2;
                    int scroll = Math.round((pavDeuxY - y) / dp(12));
                    if (scroll != 0) {
                        pavDeplacement = true;
                        envoyerSouris(pavBoutonsActifs(), 0, 0, scroll);
                        pavDeuxY = y;
                    }
                    pavDeuxDoigts = true;
                } else if (!pavDeuxDoigts) {
                    float x = event.getX(0);
                    float y = event.getY(0);
                    int dx = Math.round((x - pavX) * 2.2f);
                    int dy = Math.round((y - pavY) * 2.2f);
                    if (dx != 0 || dy != 0) {
                        pavDeplacement = true;
                        envoyerSouris(pavBoutonsActifs(), dx, dy, 0);
                    }
                    pavX = x;
                    pavY = y;
                }
                return true;
            }

            if (action == MotionEvent.ACTION_POINTER_UP) {
                return true;
            }

            if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_CANCEL) {

                if (action == MotionEvent.ACTION_UP &&
                    !pavDeplacement && !pavDoubleClicGlisse) {
                    if (pavDeuxDoigts) {
                        /* Deux doigts immobiles : clic droit. */
                        envoyerSouris(pavBoutonsActifs() | 2, 0, 0, 0);
                        envoyerSouris(pavBoutonsActifs(), 0, 0, 0);
                        pavDernierTap = 0;
                    } else {
                        /* Un doigt : clic gauche et mémorisation pour le double-tap. */
                        envoyerSouris(pavBoutonsActifs() | 1, 0, 0, 0);
                        envoyerSouris(pavBoutonsActifs(), 0, 0, 0);
                        pavDernierTap = event.getEventTime();
                        pavDernierTapX = event.getX(0);
                        pavDernierTapY = event.getY(0);
                    }
                } else if (action == MotionEvent.ACTION_UP &&
                           pavDoubleClicGlisse) {
                    /* Fin du glisser après le deuxième appui. */
                    pavDernierTap = 0;
                }

                pavDoubleClicGlisse = false;
                pavDeuxDoigts = false;
                pavDeplacement = false;
                envoyerSouris(pavBoutonsActifs(), 0, 0, 0);
                bloquerDefilementParent(false);
                return true;
            }
            return true;
        }
    }

    private void envoyerSouris(int boutons, int dx, int dy, int wheel) {
        if (dx > 32767) dx = 32767;
        if (dx < -32768) dx = -32768;
        if (dy > 32767) dy = 32767;
        if (dy < -32768) dy = -32768;
        if (wheel > 127) wheel = 127;
        if (wheel < -127) wheel = -127;

        if (modeWifi) {
            clientWifi.envoyerSouris(boutons, dx, dy, wheel);
            return;
        }

        if (hidService == null) return;

        byte[] rapport = new byte[6];
        rapport[0] = (byte) boutons;
        rapport[1] = (byte) (dx & 255);
        rapport[2] = (byte) ((dx >> 8) & 255);
        rapport[3] = (byte) (dy & 255);
        rapport[4] = (byte) ((dy >> 8) & 255);
        rapport[5] = (byte) wheel;
        hidService.sendMouseReport(rapport);
    }

    /*
     * Conversion dp.
     */
    private int dp(int valeur) {
        return (int) (
            valeur * getResources().getDisplayMetrics().density + 0.5f
			);
    }

    /*
     * Construction et envoi du rapport HID via le service,
     * ou des touches au serveur WiFi si ce mode est actif.
     */
    private void envoyerRapportClavier() {
        int modificateurs = 0;
        HashSet<Integer> usages = new HashSet<Integer>();

        Iterator<KeySpec> it = touchesActives.values().iterator();
        while (it.hasNext()) {
            KeySpec spec = it.next();
            modificateurs |= spec.modifier;
            if (spec.usage != 0) {
                usages.add(Integer.valueOf(spec.usage));
            }
        }

        if (modeWifi) {
            clientWifi.envoyerClavier(modificateurs, usages);
            return;
        }

        if (hidService == null) {
            return;
        }

        byte[] rapport = new byte[8];
        rapport[0] = (byte) modificateurs;

        if (usages.size() > 6) {
            for (int i = 2; i < 8; i++) {
                rapport[i] = 0x01;
            }
        } else {
            int index = 2;
            Iterator<Integer> keys = usages.iterator();
            while (keys.hasNext() && index < 8) {
                rapport[index] = (byte) keys.next().intValue();
                index++;
            }
        }

        hidService.sendReport(rapport);
    }

    /*
     * Relâche toutes les touches, sans arrêter le service HID.
     */
    private void relacherToutesLesTouches() {
        touchesActives.clear();
        pavBoutonGaucheMaintenu = false;
        pavBoutonDroitMaintenu = false;
        pavBoutonMoletteMaintenu = false;
        pavDoubleClicGlisse = false;
        if (modeWifi) {
            clientWifi.relacherTout();
        }
        if (hidService != null) {
            hidService.sendReport(new byte[8]);
            envoyerSouris(0, 0, 0, 0);
        }
    }

    /*
     * Boutons et affichage.
     */
    private void ajouterBouton(
        LinearLayout parent,
        String texte,
        View.OnClickListener listener) {

        Button bouton = new Button(this);
        bouton.setText(texte);
        bouton.setOnClickListener(listener);
        parent.addView(bouton);
    }

    private void afficher(String texte) {
        if (statut != null) {
            statut.setText(texte);
        }
    }

    private void afficherConnexion(String texte) {
        if (connexion != null) {
            connexion.setText(texte);
        }
    }

    /*
     * Bluetooth : l'activité délègue tout le HID au service.
     */
    /*
     * Connexion au serveur Python server_wifi.py (port 9798),
     * lance sur le PC. Il suffit d'entrer l'IP affichee par le
     * script dans la console.
     */
    private void connecterWifi() {
        final String ip = champIp.getText().toString().trim();

        if (ip.length() == 0) {
            afficher("Entre l'adresse IP du PC "
                + "(affichée par server_wifi.py).");
            return;
        }

        getSharedPreferences(
            "clavier_bluetooth",
            MODE_PRIVATE
        ).edit()
            .putString(PREF_IP_WIFI, ip)
            .apply();

        modeWifi = true;
        selecteurMode.setSelection(1);

        afficher("Connexion Wi-Fi à " + ip + "...");

        clientWifi.connecter(ip, new WifiClient.Listener() {
            public void onStatus(final String message) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        afficher(message);
                    }
                });
            }
        });
    }

    private void deconnecterWifi() {
        clientWifi.deconnecter();
        afficher("Connexion Wi-Fi fermée.");
    }

    private void activerBluetooth() {
        if (bluetoothAdapter == null) {
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, DEMANDE_BLUETOOTH);
        } else {
            demanderProfilHID();
        }
    }

    private void demanderProfilHID() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            afficher("Active le Bluetooth.");
            return;
        }

        if (!hidDisponible) {
            afficher("Clavier HID indisponible : Android 9 minimum requis.");
            return;
        }

        if (hidService != null) {
            hidService.requestHid();
            afficher("Activation du clavier HID...");
        } else {
            afficher("Démarrage du service Bluetooth...");
        }
    }

    private void rendreDetectable() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            afficher("Bluetooth désactivé.");
            return;
        }

        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivityForResult(intent, DEMANDE_VISIBILITE);
    }

    private void actualiserAppareils() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            afficher("Bluetooth désactivé.");
            return;
        }

        try {
            Set<BluetoothDevice> associes = bluetoothAdapter.getBondedDevices();
            appareils.clear();
            listeAdapter.clear();

            for (BluetoothDevice device : associes) {
                appareils.add(device);
                String nom = device.getName();
                if (nom == null) {
                    nom = "Appareil Bluetooth";
                }
                listeAdapter.add(nom + " - " + device.getAddress());
            }

            listeAdapter.notifyDataSetChanged();
            afficher(appareils.size() + " appareil(s) associé(s).");
        } catch (Exception e) {
            afficher("Erreur : " + e.getMessage());
        }
    }

    private void connecter() {
        if (hidService == null) {
            afficher("Service HID non prêt.");
            return;
        }

        int position = listeAppareils.getSelectedItemPosition();
        if (position < 0 || position >= appareils.size()) {
            afficher("Sélectionne un ordinateur.");
            return;
        }

        try {
            BluetoothDevice device = appareils.get(position);
            hidService.connect(device.getAddress());
            afficher("Connexion demandée à " + device.getName() + ".");
        } catch (Exception e) {
            afficher("Erreur : " + e.getMessage());
        }
    }

    private void deconnecter() {
        relacherToutesLesTouches();
        if (modeWifi) {
            deconnecterWifi();
        }
        if (hidService != null) {
            hidService.disconnect();
            afficher("Déconnexion demandée.");
        }
    }

    /*
     * Quitte completement l'application : ferme l'activite,
     * le service et la notification. Reutilise l'action
     * ACTION_FERMER du service, qui supprime la notification
     * et demande aussi la fermeture de l'activite.
     */
    private void quitter() {
        relacherToutesLesTouches();

        clientWifi.deconnecter();

        if (hidDisponible) {
            Intent fermeture =
                new Intent(this, BluetoothHidService.class);
            fermeture.setAction(
                BluetoothHidService.ACTION_FERMER);
            startService(fermeture);
        }

        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == DEMANDE_BLUETOOTH) {

			if (bluetoothAdapter != null &&
				bluetoothAdapter.isEnabled()) {

				demanderProfilHID();
				actualiserAppareils();

			} else {
				afficher("Bluetooth non activé.");
			}
		}

        if (requestCode == DEMANDE_VISIBILITE) {
            if (resultCode > 0) {
                afficher("Téléphone détectable.");
            } else {
                afficher("Visibilité refusée.");
            }
        }
    }

    @Override
    protected void onPause() {
        /*
         * Relâche les touches pour éviter une touche bloquée.
         * Le service HID reste actif en arrière-plan.
         */
        relacherToutesLesTouches();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        relacherToutesLesTouches();

        if (serviceBound) {
            if (hidService != null) {
                hidService.setListener(null);
            }
            unbindService(connectionService);
            serviceBound = false;
        }

        /*
         * Ne surtout pas appeler unregisterApp() ici :
         * l'enregistrement est maintenant géré par le service.
         */
        hidService = null;
		try {
			unregisterReceiver(fermetureReceiver);
		} catch (Exception e) {
		}
        super.onDestroy();
    }
}
