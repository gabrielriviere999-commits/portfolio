/* Fonction sansAccents pour filtres */
function sansAccents(str){return str
        .replace(/[_]/g, " ")
        .replace(/[&]/g, "et")
        .replace(/[àáâãäå]/g, "a")
        .replace(/[ç]/g, "c")
        .replace(/[èéêë]/g, "e")
        .replace(/[ìíîï]/g, "i")
        .replace(/[ñ]/g, "n")
        .replace(/[òóôõö]/g, "o")
        .replace(/[ùúûü]/g, "u")
        .replace(/[ýÿ]/g, "y");
}
/* Copier */
function copyText(id, btn){
    var text = document.getElementById(id).innerHTML;
    var t = document.createElement("textarea");
    document.body.appendChild(t);
    t.value = text;
    t.select();
    try { document.execCommand("copy"); } catch (err) {}
    document.body.removeChild(t);
    if (btn && btn.focus) {
        btn.focus();
    }
}
/* Copier textarea */
function copyTextarea(id, btn) {
    var code = document.getElementById(id);
    var t = document.createElement("textarea");
    t.value = code.value;
    document.body.appendChild(t);
    t.select();
    try { document.execCommand("copy"); } catch (err) {}
    document.body.removeChild(t);
    btn.focus();
}
var mapTextarea = {
    copyinput: 'input',
    copyoutput: 'output',
    copyPopupResultText: 'popupResultText',
    copyCustomPlus: 'importCustomPlusText',
    copyFancyCustomPlus: 'importFancyCustomPlusText',
    copyjson: 'json',
    copyview: 'view',
    copyarbo: 'arbo'
};
Object.keys(mapTextarea).forEach(function(btnId) {
    var el = document.getElementById(btnId);
    if (!el) return; // ignore proprement si absent
    el.onclick = function(e) {
        copyTextarea(mapTextarea[btnId], e.target);
    };
});
/* Effacer textarea */
function effacerTextareas(ids) {
    var liste = ids.split(",");
    var i;
    var textarea;
    for (i = 0; i < liste.length; i++) {
        textarea = document.getElementById(liste[i]);
        if (textarea) {
            textarea.value = "";
        }
    }
}
/* Glisser-déposer textarea */
function setupDragDrop(textareaId) {
    var area = document.getElementById(textareaId);
    if (!area) return; // ignore proprement si absent
    area.addEventListener("dragover", function(e) {
        if (e.dataTransfer.types &&
            (e.dataTransfer.types.indexOf("Files") !== -1 ||
             e.dataTransfer.types.indexOf("application/x-moz-file") !== -1)) {
            e.preventDefault();
        }
    });
    area.addEventListener("drop", function(e) {
        if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
            e.preventDefault();
            var f = e.dataTransfer.files[0];
            var reader = new FileReader();
            reader.onload = function(ev) {
                area.value = ev.target.result;
            };
            reader.readAsText(f);
            return;
        }
    });
}
setupDragDrop("input");
setupDragDrop("output");
setupDragDrop("textA");
setupDragDrop("textB");
setupDragDrop("view");
setupDragDrop("textInput");
setupDragDrop("json");
setupDragDrop("importCustomPlusText");
setupDragDrop("importFancyCustomPlusText");
/* Sélecteur de fichiers importer textarea */
function importTextareaFile(fileInputId, textareaId) {
    var file = document.getElementById(fileInputId);
    var area = document.getElementById(textareaId);
    // Si un des éléments n'existe pas → on ignore proprement
    if (!file || !area) return;
    // Input fichier → lit le fichier et le met dans le textarea
    file.addEventListener("change", function () {
        var f = this.files[0];
        if (!f) return;
        var reader = new FileReader();
        reader.onload = function (e) {
            area.value = e.target.result;
        };
        reader.readAsText(f);
    });
}
importTextareaFile("textareaFileinput", "input");
importTextareaFile("textareaFileoutput", "output");
importTextareaFile("textareaFileimportBox", "importBox");
importTextareaFile("textareaFileview", "view");
importTextareaFile("textareaFiletext", "text");
importTextareaFile("textareaFiletextA", "textA");
importTextareaFile("textareaFiletextB", "textB");
/* Paramètre URL fichier textarea */
function chargerTextareaFile() {
    var query = window.location.search.substring(1);
    var params = query.split("&");
    var i;
    var parts;
    var paramName;
    var paramValue;
    var morceaux;
    var textareaId;
    var fichierUrl;
    var textarea;
    var xhr;
    for (i = 0; i < params.length; i++) {
        parts = params[i].split("=");
        if (parts.length < 2) {
            continue;
        }
        paramName = decodeURIComponent(parts[0]);
        paramValue = decodeURIComponent(parts.slice(1).join("="));
        /* On cherche : ?textareafile=ID|FICHIER
           Exemple : ?textareafile=input|fichier.txt
        */
        if (paramName != "textareafile") {
            continue;
        }
        morceaux = paramValue.split("|");
        if (morceaux.length < 2) {
            continue;
        }
        textareaId = morceaux[0];
        fichierUrl = morceaux.slice(1).join("|");
        textarea = document.getElementById(textareaId);
        if (!textarea) {
            continue;
        }
        xhr = new XMLHttpRequest();
        (function(textarea, fichierUrl, xhr) {
            xhr.onreadystatechange = function() {
                if (xhr.readyState == 4) {
                    if (xhr.status == 200 || xhr.status == 0) {
                        textarea.value = xhr.responseText;
                    }
                    else {
                        textarea.value = "Erreur lors du chargement du fichier : " + xhr.status;
                    }
                }
            };
            xhr.open("GET", fichierUrl, true);
            xhr.send(null);
        })(textarea, fichierUrl, xhr);
    }
}
if (document.addEventListener) {
    document.addEventListener("DOMContentLoaded", chargerTextareaFile, false);
    window.addEventListener("load", chargerTextareaFile, false);
} else if (document.attachEvent) {
    document.attachEvent("onreadystatechange", function(){
        if (document.readyState === "complete") chargerTextareaFile;
    });
    window.attachEvent("onload", chargerTextareaFile);
} else {
    // Dernier fallback ES3
    var old = window.onload;
    window.onload = function(){
        if (typeof old === "function") old();
        chargerTextareaFile;
    };
}
/* Ctrl + V image depuis le presse-papiers */
var pasteZones = [
    { pastezone: "pastefileinput", fileinput: "fileinput" },
    { pastezone: "pastedrawingFile", fileinput: "drawingFile" },
    { pastezone: "pasteimageFile", fileinput: "imageFile" },
    { pastezone: "pastefileNorth", fileinput: "fileNorth" },
    { pastezone: "pastefileEast", fileinput: "fileEast" },
    { pastezone: "pastefileSouth", fileinput: "fileSouth" },
    { pastezone: "pastefileWest", fileinput: "fileWest" },
    { pastezone: "pastefileTop", fileinput: "fileTop" },
    { pastezone: "pastefileBottom", fileinput: "fileBottom" }
];
pasteZones.forEach(function (item) {
    var pastezone = document.getElementById(item.pastezone);
    var fileinput = document.getElementById(item.fileinput);
    if (!pastezone || !fileinput) {
        return;
    }
    pastezone.onpaste = function (e) {
        var items = (e.clipboardData || e.originalEvent.clipboardData).items;
        var file = null;
        for (var i = 0; i < items.length; i++) {
            if (items[i].type.indexOf("image") !== -1) {
                file = items[i].getAsFile();
                break;
            }
        }
        if (file) {
            // Met l'image dans l'input file correspondant
            var dataTransfer = new DataTransfer();
            dataTransfer.items.add(file);
            fileinput.files = dataTransfer.files;
            // Déclenche l'événement change
            fileinput.dispatchEvent(new Event("change", {
                bubbles: true
            }));
            // traitement
            loadFile(file);
        } else {
            alert("Aucune image trouvée dans le presse-papier.");
        }
    };
});
/* Date et heure téléchargement */
function getTimestampName(prefix, ext) {
    var d = new Date();
    var YYYY = d.getFullYear();
    var MM = String(d.getMonth() + 1);
    var DD = String(d.getDate());
    var hh = String(d.getHours());
    var mm = String(d.getMinutes());
    var ss = String(d.getSeconds());
    if (MM.length < 2) MM = "0" + MM;
    if (DD.length < 2) DD = "0" + DD;
    if (hh.length < 2) hh = "0" + hh;
    if (mm.length < 2) mm = "0" + mm;
    if (ss.length < 2) ss = "0" + ss;
    return prefix + "_" + YYYY + MM + DD + "_" + hh + mm + ss + "." + ext;
}
/* Boutons - et + sliders */
var sliders = {
    zoomRange: ["applyZoomRange", 1],
    imgScale: ["zoomImage", 5],
    zoomInput: ["applyZoom", 20],
    sizeInput: ["sizeInput", 1],
    dottedGapInput: ["dottedGapValue", 1],
    polygonSidesInput: ["polygonSidesInput", 1],
    starBranchesInput: ["starBranchesInput", 1],
    zoom: ["zoom", 20],
    fovSlider: ["fovSlider", 1]
};
var sliderLabels = {
    sizeInput: "sizeLabel",
    dottedGapInput: "dottedGapLabel",
    polygonSidesInput: "polygonSidesLabel",
    starBranchesInput: "starBranchesLabel",
    zoom: "zoomLabel"
};
function changeSlider(id, amount) {
    var slider = document.getElementById(id);
    if (!slider) return;
    var value = parseInt(slider.value, 10);
    var min = parseInt(slider.min, 10);
    var max = parseInt(slider.max, 10);
    value += amount;
    if (value < min) value = min;
    if (value > max) value = max;
    slider.value = value;
    if (sliderLabels[id]) {
        document.getElementById(sliderLabels[id]).textContent = value;
    }
    if (sliders[id] && typeof window[sliders[id][0]] === "function") {
        window[sliders[id][0]](value);
    }
    // Déclenche exactement le même mécanisme que lorsqu'on déplace le slider à la souris
    slider.dispatchEvent(new Event("input", { bubbles: true }));
}
// Activation souris / tactile
var sliderEvent = ("PointerEvent" in window) ? "pointerup" : "click";
document.addEventListener(sliderEvent, function(e) {
    var action = e.target.getAttribute("data-action");
    if (!action) return;
    handleSliderAction(action);
});
// Activation clavier (Entrée / Espace)
document.addEventListener("keydown", function(e) {
    if (e.key !== "Enter" && e.key !== " ") return;
    var el = document.activeElement;
    if (!el) return;
    var action = el.getAttribute("data-action");
    if (!action) return;
    handleSliderAction(action);
});
// Fonction commune
function handleSliderAction(action) {
    var parts = action.match(/^(.*)(Minus|Plus)$/);
    if (!parts) return;
    var id = parts[1];
    if (!sliders[id]) return;
    var amount = parts[2] === "Minus"
        ? -sliders[id][1]
        : sliders[id][1];
    changeSlider(id, amount);
}
/* BOUTONS DE SCROLL */
var scrollButtons = document.querySelectorAll(".scroll-btn");
for (var i = 0; i < scrollButtons.length; i++) {
    (function (btn) {
        var dir = btn.getAttribute("data-dir");
        var targetId = btn.getAttribute("data-target");
        var target = document.getElementById(targetId);
        if (!target) {
            return;
        }
        var scrolling = false;
        function scroll() {
            var speed = 15;
            if (dir === "up") {
                target.scrollTop -= speed;
            }
            else if (dir === "down") {
                target.scrollTop += speed;
            }
            else if (dir === "left") {
                target.scrollLeft -= speed;
            }
            else if (dir === "right") {
                target.scrollLeft += speed;
            }
        }
        function startScroll(e) {
            this.classList.add("pressed");
            if (e && e.preventDefault) {
                e.preventDefault();
            }
            scrolling = true;
        }
        function stopScroll(e) {
            this.classList.remove("pressed");
            if (e && e.preventDefault) {
                e.preventDefault();
            }
            scrolling = false;
        }
        // SOURIS
        btn.addEventListener("mousedown", startScroll, false);
        btn.addEventListener("mouseup", stopScroll, false);
        btn.addEventListener("mouseleave", stopScroll, false);
        // TACTILE
        btn.addEventListener("touchstart", startScroll, false);
        btn.addEventListener("touchend", stopScroll, false);
        btn.addEventListener("touchcancel", stopScroll, false);
        // CLAVIER
        btn.addEventListener("keydown", function (e) {
            var key = e.key || e.keyCode;
            if (key === " " || key === "Enter") {
                e.preventDefault();
                scrolling = true;
            }
        }, false);
        btn.addEventListener("keyup", function (e) {
            var key = e.key || e.keyCode;
            if (key === " " || key === "Enter") {
                e.preventDefault();
                scrolling = false;
            }
        }, false);
        // CLIC / BOUTON A 3DS
        btn.addEventListener("click", function (e) {
            e.preventDefault();
            scroll();
        }, false);
        // SCROLL CONTINU
        setInterval(function () {
            if (scrolling) {
                scroll();
            }
        }, 50);
    })(scrollButtons[i]);
}
/* Masquer toolbar */
var toolbar = document.getElementsByClassName('toolbar')[0];
var toggleBtn = document.getElementById('toggleToolbarBtn');
var toolbarVisible = true;
if (toolbar && toggleBtn) {
  toggleBtn.onclick = function(e) {
    toolbarVisible = !toolbarVisible;
    if (toolbarVisible) {
      toolbar.className = 'toolbar';
    } else {
      toolbar.className = 'toolbar hidden';
    }
  };
}