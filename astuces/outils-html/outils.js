/* --- Popup ---*/
function openPopup(htmlContent) {

    var d = document.createElement('div');
    d.className = "popup-overlay";

    // fermeture arrière-plan en inline
    d.setAttribute(
        "onclick",
        "var e=event||window.event;var t=e.target||e.srcElement;if(t==this&&this.parentNode)this.parentNode.removeChild(this);"
    );

    var p = document.createElement('div');
    p.className = "popup-window";
    p.setAttribute("tabindex", "-1"); // focus PC

    var t = document.createElement('div');
    t.className = "popup-content";
    t.innerHTML = htmlContent;

    var b = document.createElement('button');
    b.textContent = 'Fermer';
    b.className = "popup-close";

    // fermeture bouton en inline
    b.setAttribute(
        "onclick",
        "(function(btn){ var d = btn.parentNode.parentNode; if(d && d.parentNode){ d.parentNode.removeChild(d); } })(this)"
    );

    p.appendChild(t);
    p.appendChild(b);
    d.appendChild(p);
    document.body.appendChild(d);

    // focus automatique sur la fenêtre
    p.focus();
}

/* --- Copier textarea ---*/
function copyTextarea(id, btn) {
    var code = document.getElementById(id);

    var temp = document.createElement("textarea");
    temp.value = code.value;
    document.body.appendChild(temp);
    temp.select();

    try { document.execCommand("copy"); } catch (err) {}

    document.body.removeChild(temp);
    btn.focus();
}
var mapTextarea = {
    copyinput: 'input',
    copyoutput: 'output',
    copyCustomPlus: 'importCustomPlusText',
    copyFancyCustomPlus: 'importFancyCustomPlusText',
    copyjson: 'json',
    copyview: 'view',
    copyasciiTextArea: 'asciiTextArea',
    copyhtmlCode: 'htmlCode'
};
Object.keys(mapTextarea).forEach(function(btnId) {
    var el = document.getElementById(btnId);
    if (!el) return; // ignore proprement si absent

    el.onclick = function(e) {
        copyTextarea(mapTextarea[btnId], e.target);
    };
});

/* --- Glisser-déposer ---*/
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
setupDragDrop("importBox");
setupDragDrop("text");

/* --- Bouton importer fichier vers textarea ---*/
function importTextareaFile(buttonId, fileInputId, textareaId) {
    var btn  = document.getElementById(buttonId);
    var file = document.getElementById(fileInputId);
    var area = document.getElementById(textareaId);

    // Si un des éléments n'existe pas → on ignore proprement
    if (!btn || !file || !area) return;

    // Bouton → ouvre le sélecteur de fichier
    btn.onclick = function () {
        file.click();
    };

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
importTextareaFile("importTextareaFileinput", "textareaFileinput", "input");
importTextareaFile("importTextareaFileoutput", "textareaFileoutput", "output");
importTextareaFile("importTextareaFileimportBox", "textareaFileimportBox", "importBox");
importTextareaFile("importTextareaFileview", "textareaFileview", "view");
importTextareaFile("importTextareaFiletextA", "textareaFiletextA", "textA");
importTextareaFile("importTextareaFiletextB", "textareaFiletextB", "textB");
importTextareaFile("importTextareaFiletext", "textareaFiletext", "text");
