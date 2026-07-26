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

function copyFrom(id, btn) {
    var code = document.getElementById(id);

    var temp = document.createElement("textarea");
    temp.value = code.value;
    document.body.appendChild(temp);
    temp.select();

    try { document.execCommand("copy"); } catch (err) {}

    document.body.removeChild(temp);
    btn.focus();
}
function copyOnClick(id, fn) {
    var el = document.getElementById(id);
    if (el) el.onclick = fn;
}
copyOnClick('copyinput', function(e) {
    copyFrom('input', e.target);
});
copyOnClick('copyoutput', function(e) {
    copyFrom('output', e.target);
});
copyOnClick('copyCustomPlus', function(e) {
    copyFrom('importCustomPlusText', e.target);
});
copyOnClick('copyFancyCustomPlus', function(e) {
    copyFrom('importFancyCustomPlusText', e.target);
});
copyOnClick('copyjson', function(e) {
    copyFrom('json', e.target);
});
copyOnClick('copyview', function(e) {
    copyFrom('view', e.target);
});
copyOnClick('copyasciiTextArea', function(e) {
    copyFrom('asciiTextArea', e.target);
});
copyOnClick('copyhtmlCode', function(e) {
    copyFrom('htmlCode', e.target);
});
