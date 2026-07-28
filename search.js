function openSearchPopup() {
  var container = document.getElementById("popupSearchContent");
  openPopupMini(container);
}
function doSearch() {
  var q = document.getElementById("search").value.toLowerCase();
  var out = "<ul>";
  var found = 0;
  for (var i=0; i<docs.length; i++) {
    if (docs[i].content.toLowerCase().indexOf(q) !== -1) {
      out += "<li>"
          + "<a href='" + docs[i].url + "' target='_self'>" + docs[i].title + "</a>"
          + " <a href='" + docs[i].url + "' download>[↓]</a>"
          + "</li>";
      found++;
    }
  }
  out += "</ul>";
  // Affichage du compteur
  document.getElementById("count").textContent =
      found > 0 ? found + " résultat(s)" : "Aucun résultat";
  // Affichage des résultats
  document.getElementById("results").innerHTML =
      found > 0 ? out : "";
  // Sauvegarde
  sessionStorage.setItem("lastQuery", q);
  sessionStorage.setItem("lastResults", out);
  sessionStorage.setItem("lastCount", found);
}
// Au chargement de la page, restaurer
window.onload = function() {
  if (sessionStorage.getItem("lastQuery")) {
    document.getElementById("search").value = sessionStorage.getItem("lastQuery");
    document.getElementById("results").innerHTML = sessionStorage.getItem("lastResults");
  }
  if (sessionStorage.getItem("lastCount")) {
    var c = sessionStorage.getItem("lastCount");
    document.getElementById("count").textContent =
        c > 0 ? c + " résultat(s)" : "Aucun résultat";
  }
};
