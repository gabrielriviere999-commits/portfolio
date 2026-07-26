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
