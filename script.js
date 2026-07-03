var lastScroll = 0;

window.addEventListener("scroll", function () {
    var navbar = document.querySelector(".navbar");
    var current = window.scrollY;

    if (current > lastScroll && current > 50) {
        navbar.classList.add("hide");
    } else {
        navbar.classList.remove("hide");
    }

    lastScroll = current;
});

var supportsTransform = 'transform' in document.body.style;

if (!supportsTransform) {
    document.documentElement.classList.add('no-transform');
}

function downloadFile(select) {
    var file = select.value;
    if (!file) return;

    // Teste si le navigateur supporte l'attribut download
    var a = document.createElement("a");
    var supportsDownload = ("download" in a);

    if (supportsDownload) {
        // mode normal : téléchargement
        a.href = file;
        a.download = "";
        document.body.appendChild(a);
        a.click();
        a.remove();
    } else {
        // mode fallback (3DS, vieux navigateurs) : ouvrir dans un nouvel onglet
        window.open(file, "_blank");
    }

    // Réinitialise le menu
    select.value = "";
}

var slideIndex = {
  "infrastructure": 0,
  "haproxy": 0,
  "glpi": 0,
  "stage-bts2": 0,
  "linux": 0,
  "android": 0
};

function plusSlides(n, group) {
  slideIndex[group] = slideIndex[group] + n;
  showSlides(slideIndex[group], group);
}

function currentSlide(n, group) {
  slideIndex[group] = n;
  showSlides(slideIndex[group], group);
}

function scrollThumbnailIntoView(index, group) {
    var row = document.querySelector('.thumbnail-row[data-group="' + group + '"]');
    if (!row) return;

    var thumbs = row.getElementsByClassName("thumbnail");
    var t = thumbs[index - 1];
    if (!t) return;

    var left = t.offsetLeft;
    var width = t.offsetWidth;
    var visible = row.clientWidth;
    var current = row.scrollLeft;

    if (left < current) {
        row.scrollLeft = left - 10;
    } else if (left + width > current + visible) {
        row.scrollLeft = (left + width) - visible + 10;
    }
}

function showSlides(n, group) {
    var slides = document.querySelectorAll('.slide[data-group="' + group + '"]');
    var thumbs = document.querySelectorAll('.thumbnail[data-group="' + group + '"]');
    var total = slides.length;
    var i;

    if (total === 0) return;

    if (n > total) slideIndex[group] = 1;
    if (n < 1) slideIndex[group] = total;

    // masquer tous les slides
    for (i = 0; i < total; i++) {
        slides[i].style.display = "none";
    }

    // Désactiver toutes les miniatures
    for (i = 0; i < thumbs.length; i++) {
        thumbs[i].className = thumbs[i].className.replace(" active", "");
    }

    var current = slides[slideIndex[group] - 1];
    current.style.display = "block";

    // Lazy-load image
    var img = current.querySelector("img[data-src]");
    if (img && !img.src) {
        img.src = img.getAttribute("data-src");
    }

    // Activer la miniature
    if (thumbs[slideIndex[group] - 1]) {
        thumbs[slideIndex[group] - 1].className += " active";
    }

    // mettre à jour le compteur
    var counter = document.getElementById("counter-" + group);
    if (counter) {
        counter.textContent = slideIndex[group] + " / " + total;
    }

    // Synchroniser le select
    var select = document.querySelector('.slide-select[data-group="' + group + '"]');
    if (select && slideIndex[group] > 0) {
        select.value = slideIndex[group];
    }
    scrollThumbnailIntoView(slideIndex[group], group);

    // Création dynamique de l'iframe
    var holder = current.querySelector(".iframe-holder");
    if (holder && !holder.dataset.loaded) {
        var iframe = document.createElement("iframe");
        iframe.src = holder.getAttribute("data-src");
        holder.appendChild(iframe);
        holder.dataset.loaded = "1";
    }
}

function openFullscreen(group) {
    var index = slideIndex[group] || 1;
    var slides = document.querySelectorAll('.slide[data-group="' + group + '"]');
    if (slides.length === 0) return;

    var activeSlide = slides[index - 1];
    if (!activeSlide) return;

    // 1) iframe (HTmL, TXT, PDF)
    var iframe = activeSlide.querySelector("iframe");
    if (iframe) {
        var url = iframe.src || iframe.getAttribute("data-src");
        if (url) {
            window.open(url, "_blank");
            return;
        }
    }

    // 2) image
    var img = activeSlide.querySelector("img");
    if (img && img.src) {
        window.open(img.src, "_blank");
        return;
    }

    // 3) vidéo
    var video = activeSlide.querySelector("video");
    if (video && video.src) {
        window.open(video.src, "_blank");
        return;
    }

    console.log("Aucun contenu ouvrable trouvé dans cette slide");
}

function downloadSlide(group) {
    var index = slideIndex[group] || 1;
    var slides = document.querySelectorAll('.slide[data-group="' + group + '"]');
    if (slides.length === 0) return;

    var activeSlide = slides[index - 1];
    if (!activeSlide) return;

    var url = null;

    // 1) iframe (HTmL, TXT, PDF)
    var iframe = activeSlide.querySelector("iframe");
    if (iframe) {
        url = iframe.src || iframe.getAttribute("data-src");
    }

    // 2) image
    var img = activeSlide.querySelector("img");
    if (!url && img) {
        url = img.src;
    }

    // 3) vidéo
    var video = activeSlide.querySelector("video");
    if (!url && video) {
        url = video.src;
    }

    if (!url) {
        console.log("Aucun contenu téléchargeable trouvé");
        return;
    }

    // Création d'un lien invisible pour forcer le téléchargement
    var a = document.createElement("a");
    a.href = url;
    a.download = url.split('/').pop(); // nom du fichier
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

function initAllSelects() {
    var selects = document.querySelectorAll(".slide-select");

    for (var s = 0; s < selects.length; s++) {
        (function (select) {
            var group = select.getAttribute("data-group");
            var slides = document.querySelectorAll('.slide[data-group="' + group + '"]');

            for (var i = 0; i < slides.length; i++) {

                var file = null;

                // 1) iframe-holder (HTML, TXT, PDF…)
                var holder = slides[i].querySelector(".iframe-holder");
                if (holder) {
                    file = holder.getAttribute("data-src");
                }

                // 2) image (JPG, PNG…)
                if (!file) {
                    var img = slides[i].querySelector("img[data-src]");
                    if (img) file = img.getAttribute("data-src");
                }

                // 3) vidéo
                if (!file) {
                    var video = slides[i].querySelector("video[data-src]");
                    if (video) file = video.getAttribute("data-src");
                }

                // Création de l’option
                var opt = document.createElement("option");
                opt.value = i + 1;

                if (file) {
                    opt.text = file.split("/").pop(); // nom du fichier
                } else {
                    opt.text = "Slide " + (i + 1);
                }

                select.appendChild(opt);
            }

            // Quand on change → aller à la slide
            select.onchange = function () {
                if (this.value !== "") {
                    currentSlide(parseInt(this.value, 10), group);
                }
            };
        })(selects[s]);
    }
}

initAllSelects();
