/* --- Popup ---*/
function popupScrollUp(){
    var t = document.querySelector('.popup-content');
    if(t) t.scrollTop -= 50;
}
function popupScrollDown(){
    var t = document.querySelector('.popup-content');
    if(t) t.scrollTop += 50;
}
var popupOrigin = null;
var popupJustOpened = false;
function openPopup(container) {
    popupOrigin = container;

    popupJustOpened = true;
    setTimeout(function(){ popupJustOpened = false; }, 150);

    var d = document.createElement('div');
    d.className = "popup-overlay";

    d.onmousedown = function(e){
        e = e || window.event;
        e.cancelBubble = true;
        if (e.stopPropagation) e.stopPropagation();
    };

    d.onclick = function(e){
        e = e || window.event;
        e.cancelBubble = true;
        if (e.stopPropagation) e.stopPropagation();

        if (popupJustOpened) return; 

        if (e.target === d) closePopup();
    };

    var p = document.createElement('div');
    p.className = "popup-window";
    p.setAttribute("tabindex", "-1");

    var t = document.createElement('div');
    t.className = "popup-content";

    while (container.firstChild) {
        t.appendChild(container.firstChild);
    }

    var up = document.createElement('button');
    up.textContent = 'Monter';
    up.className = 'popup-scroll-up';
    up.setAttribute("data-action", "popupScrollUp");
    up.onclick = popupScrollUp;

    var down = document.createElement('button');
    down.textContent = 'Descendre';
    down.className = 'popup-scroll-down';
    down.setAttribute("data-action", "popupScrollDown");
    down.onclick = popupScrollDown;

    var b = document.createElement('button');
    b.textContent = 'Fermer';
    b.className = "popup-close";
    b.setAttribute("data-action", "closePopup");
    b.onclick = closePopup;
    
    var btns = document.createElement('div');
    btns.className = "popup-buttons";

    btns.appendChild(up);
    btns.appendChild(down);
    btns.appendChild(b);

    p.appendChild(t);
    p.appendChild(btns);
    d.appendChild(p);
    document.body.appendChild(d);

    setTimeout(function(){p.focus();},0);
    addMultiTouch(".popup-close, .popup-scroll-up, .popup-scroll-down");
}
function openPopupMini(container) {
    popupOrigin = container;

    popupJustOpened = true;
    setTimeout(function(){ popupJustOpened = false; }, 150);

    var d = document.createElement('div');
    d.className = "popup-overlay";

    d.onclick = function(e){
        if (popupJustOpened) return;
        if (e.target === d) closePopup();
    };

    var p = document.createElement('div');
    p.className = "popup-window-small";
    p.setAttribute("tabindex", "-1");

    var t = document.createElement('div');
    t.className = "popup-content-small";

    while (container.firstChild) {
        t.appendChild(container.firstChild);
    }

    p.appendChild(t);
    d.appendChild(p);
    document.body.appendChild(d);

    setTimeout(function(){ p.focus(); }, 0);
}
function closePopup() {
    var d = document.querySelector('.popup-overlay');
    if (!d) return;
    var popupContent = d.querySelector('.popup-content,.popup-content-small');
    d.style.opacity = "0";
    // Remettre les enfants dans leur conteneur d'origine
    while (popupContent.firstChild) {
        popupOrigin.appendChild(popupContent.firstChild);
    }
    popupOrigin = null;
    setTimeout(function(){
        if (d.parentNode) {
            d.parentNode.removeChild(d);
        }
    }, 100);
}
document.onkeydown = function(e){
    e = e || window.event;
    if (e.key === "Escape" || e.keyCode === 27) {
        var d = document.querySelector('.popup-overlay');
        if (d) closePopup();
    }
};
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
setupDragDrop("text");
setupDragDrop("textA");
setupDragDrop("textB");
setupDragDrop("view");
setupDragDrop("textInput");
setupDragDrop("importBox");

/* --- Bouton importer fichier vers textarea ---*/
function importTextareaFile(buttonId, fileInputId, textareaId) {
    var btn = document.getElementById(buttonId);
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
importTextareaFile("importTextareaFiletext", "textareaFiletext", "text");
importTextareaFile("importTextareaFiletextA", "textareaFiletextA", "textA");
importTextareaFile("importTextareaFiletextB", "textareaFiletextB", "textB");
