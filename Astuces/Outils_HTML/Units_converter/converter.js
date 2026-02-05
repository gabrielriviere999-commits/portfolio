// ===============================
// TABLES D’UNITÉS
// ===============================

var categories = {

  distance: {
    base: "m",
    units: {
      m: 1,
      km: 1000,
      cm: 0.01,
      mm: 0.001,
      mi: 1609.344,
      yd: 0.9144,
      ft: 0.3048,
      in: 0.0254,
      nmi: 1852
    }
  },

  acceleration: {
    base: "m/s²",
    units: {
      "m/s²": 1,
      "cm/s²": 0.01,
      "ft/s²": 0.3048,
      "in/s²": 0.0254,
      "km/h·s": 1000 / 3600,
      "mi/h·s": 1609.344 / 3600,
      "mi/h·min": 1609.344 / (3600 * 60),
      g: 9.80665
    }
  },

  masse: {
    base: "kg",
    units: {
      kg: 1,
      g: 0.001,
      mg: 0.000001,
      t: 1000,
      lb: 0.45359237,
      oz: 0.0283495231,
      stone: 6.35029318
    }
  },

  volume: {
    base: "L",
    units: {
      L: 1,
      mL: 0.001,
      m3: 1000,
      "ft3": 28.316846592,
      gal_US: 3.785411784,
      qt_US: 0.946352946,
      pt_US: 0.473176473,
      cup_US: 0.2365882365
    }
  },

  volume_sec: {
    base: "L",
    units: {
      L: 1,
      cup_dry_US: 0.2365882365,
      pt_dry_US: 0.550610471,
      qt_dry_US: 1.101220942,
      gal_dry_US: 4.40488377,
      bushel_US: 35.23907017
    }
  },

  surface: {
    base: "m²",
    units: {
      "m²": 1,
      "cm²": 0.0001,
      "mm²": 0.000001,
      "km²": 1000000,
      ha: 10000,
      "ft²": 0.09290304,
      "in²": 0.00064516,
      acre: 4046.8564224
    }
  },

  vitesse: {
    base: "m/s",
    units: {
      "m/s": 1,
      "km/h": 1000 / 3600,
      "mph": 1609.344 / 3600,
      "kn": 1852 / 3600,
      "ft/s": 0.3048
    }
  },

  temps: {
    base: "s",
    units: {
      s: 1,
      ms: 0.001,
      min: 60,
      h: 3600,
      jour: 86400,
      semaine: 604800,
      an: 31557600
    }
  },

  energie: {
    base: "J",
    units: {
      J: 1,
      kJ: 1000,
      Wh: 3600,
      kWh: 3600000,
      cal: 4.184,
      kcal: 4184,
      BTU: 1055.05585
    }
  },

  puissance: {
    base: "W",
    units: {
      W: 1,
      kW: 1000,
      MW: 1000000,
      hp_mec: 745.699872,
      hp_met: 735.49875
    }
  },

  force: {
    base: "N",
    units: {
      N: 1,
      kN: 1000,
      dyn: 0.00001,
      lbf: 4.4482216152605,
      kgf: 9.80665
    }
  },

  pression: {
    base: "Pa",
    units: {
      Pa: 1,
      kPa: 1000,
      bar: 100000,
      mbar: 100,
      psi: 6894.757293168,
      atm: 101325,
      mmHg: 133.3223684211
    }
  },

  densite: {
    base: "kg/m³",
    units: {
      "kg/m³": 1,
      "g/cm³": 1000,
      "g/L": 1,
      "lb/ft³": 16.01846337
    }
  },

  debit: {
    base: "m³/s",
    units: {
      "m³/s": 1,
      "m³/h": 1 / 3600,
      "L/s": 0.001,
      "L/min": 0.001 / 60,
      "L/h": 0.001 / 3600,
      "gal_US/min": 3.785411784 / 60
    }
  },

  angle: {
    base: "rad",
    units: {
      rad: 1,
      deg: Math.PI / 180,
      grad: Math.PI / 200,
      tour: 2 * Math.PI
    }
  },

  lumiere: {
    base: "lx",
    units: {
      lx: 1,
      "foot-candle": 10.76391041671
    }
  },

  temperature: {
    special: true,
    units: ["C", "F", "K"]
  },

  custom: {
    special: "custom",
    units: {}
  }
};

// ===============================
// TEMPÉRATURE
// ===============================

function convertTemperature(value, from, to) {
  var c;

  if (from === "C") c = value;
  if (from === "F") c = (value - 32) * 5 / 9;
  if (from === "K") c = value - 273.15;

  if (to === "C") return c;
  if (to === "F") return c * 9 / 5 + 32;
  if (to === "K") return c + 273.15;
}

// ===============================
// DOM
// ===============================

var categorySelect = document.getElementById("category");
var fromSelect = document.getElementById("fromUnit");
var toSelect = document.getElementById("toUnit");
var valueInput = document.getElementById("value");
var resultSpan = document.getElementById("result");

var customBox = document.getElementById("customBox");
var customA = document.getElementById("customA");
var customB = document.getElementById("customB");
var customFactor = document.getElementById("customFactor");

// ===============================
// LISTENERS (ES5)
// ===============================

categorySelect.addEventListener("change", function () {
  updateUnits();
  saveState();
});

fromSelect.addEventListener("change", function () {
  convert();
  saveState();
});

toSelect.addEventListener("change", function () {
  convert();
  saveState();
});

valueInput.addEventListener("input", function () {
  convert();
  saveState();
});

customA.addEventListener("input", function () {
  updateCustomUnits();
  saveState();
});

customB.addEventListener("input", function () {
  updateCustomUnits();
  saveState();
});

customFactor.addEventListener("input", function () {
  updateCustomUnits();
  saveState();
});

// ===============================
// INITIALISATION
// ===============================

for (var cat in categories) {
  categorySelect.add(new Option(cat, cat));
}

loadState();

// ===============================
// FONCTIONS (ES5)
// ===============================

function updateUnits() {
  var cat = categories[categorySelect.value];

  fromSelect.innerHTML = "";
  toSelect.innerHTML = "";

  if (cat.special === "custom") {
    customBox.style.display = "block";
    updateCustomUnits();
  } else {
    customBox.style.display = "none";

    if (cat.special === true) {
      for (var i = 0; i < cat.units.length; i++) {
        var u = cat.units[i];
        fromSelect.add(new Option(u, u));
        toSelect.add(new Option(u, u));
      }
    } else {
      for (var u2 in cat.units) {
        fromSelect.add(new Option(u2, u2));
        toSelect.add(new Option(u2, u2));
      }
    }
  }

  convert();
}

function updateCustomUnits() {
  var nameA = customA.value || "A";
  var nameB = customB.value || "B";
  var factor = parseFloat(customFactor.value) || 1;

  categories.custom.units = {};
  categories.custom.units[nameA] = 1;
  categories.custom.units[nameB] = factor;

  fromSelect.innerHTML = "";
  toSelect.innerHTML = "";

  fromSelect.add(new Option(nameA, nameA));
  fromSelect.add(new Option(nameB, nameB));

  toSelect.add(new Option(nameA, nameA));
  toSelect.add(new Option(nameB, nameB));

  convert();
}

function convert() {
  var cat = categories[categorySelect.value];
  var val = parseFloat(valueInput.value) || 0;
  var from = fromSelect.value;
  var to = toSelect.value;

  if (!from || !to) return;

  var result;

  if (cat.special === true) {
    result = convertTemperature(val, from, to);
  } else {
    var base = val * cat.units[from];
    result = base / cat.units[to];
  }

  resultSpan.innerHTML = result + " " + to;
}

// ===============================
// SAUVEGARDE / RESTAURATION (ES5)
// ===============================

function saveState() {
  var state = {
    category: categorySelect.value,
    from: fromSelect.value,
    to: toSelect.value,
    value: valueInput.value,
    customA: customA.value,
    customB: customB.value,
    customFactor: customFactor.value
  };

  localStorage.setItem("converterState", JSON.stringify(state));
}

function loadState() {
  var saved = localStorage.getItem("converterState");
  if (!saved) return;

  var state = JSON.parse(saved);

  if (state.category) categorySelect.value = state.category;

  updateUnits();

  customA.value = state.customA || "";
  customB.value = state.customB || "";
  customFactor.value = state.customFactor || 1;

  if (state.category === "custom") updateCustomUnits();

  if (state.from) fromSelect.value = state.from;
  if (state.to) toSelect.value = state.to;
  if (state.value) valueInput.value = state.value;

  convert();
}

// ===============================
// RECHERCHE — VERSION ES5
// ===============================

document.addEventListener("DOMContentLoaded", function () {

  function createResultsBox(inputId) {
    var input = document.getElementById(inputId);
    var box = document.createElement("div");

    box.style.background = "white";
    box.style.border = "1px solid #999";
    box.style.maxHeight = "150px";
    box.style.overflowY = "auto";
    box.style.display = "none";
    box.style.marginTop = "4px";

    input.parentNode.appendChild(box);
    return box;
  }

  var fromBox = createResultsBox("searchFrom");
  var toBox = createResultsBox("searchTo");

  function searchUnits(text, box, targetSelectId) {
    var val = text.toLowerCase().trim();
    box.innerHTML = "";

    if (!val) {
      box.style.display = "none";
      return;
    }

    for (var catName in categories) {

      if (catName === "custom") continue;

      var cat = categories[catName];
      var list = cat.special === true ? cat.units : Object.keys(cat.units);

      for (var i = 0; i < list.length; i++) {
        var unit = list[i];

        if (unit.toLowerCase().indexOf(val) !== -1) {

          var item = document.createElement("div");
          item.textContent = unit + " (" + catName + ")";
          item.style.padding = "4px";
          item.style.cursor = "pointer";

          item.onclick = (function (catName, unit) {
            return function () {

              if (categorySelect.value !== catName) {
                categorySelect.value = catName;
                updateUnits();
              }

              document.getElementById(targetSelectId).value = unit;
              convert();
              saveState();
              box.style.display = "none";
            };
          })(catName, unit);

          box.appendChild(item);
        }
      }
    }

    box.style.display = box.children.length ? "block" : "none";
  }

  document.getElementById("searchFrom").addEventListener("input", function (e) {
    searchUnits(e.target.value, fromBox, "fromUnit");
  });

  document.getElementById("searchTo").addEventListener("input", function (e) {
    searchUnits(e.target.value, toBox, "toUnit");
  });

  document.addEventListener("click", function (e) {
    if (!e.target || !e.target.id || e.target.id !== "searchFrom") {
      fromBox.style.display = "none";
    }
    if (!e.target || !e.target.id || e.target.id !== "searchTo") {
      toBox.style.display = "none";
    }
  });

});
