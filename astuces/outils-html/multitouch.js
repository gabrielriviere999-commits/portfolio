function addMultiTouch(selector){
    var els = document.querySelectorAll(selector);

    for(var i=0; i<els.length; i++){
        var el = els[i];

        // Récupère la fonction à appeler
        var action = el.getAttribute("data-action");
        if(action){
            el._action = window[action]; // stocke la vraie fonction
        }

        // Multitouch (pointerdown)
        el.onpointerdown = function(e){
            e = e || window.event;
            if(e.stopPropagation) e.stopPropagation();
            e.cancelBubble = true;

            // Appelle la fonction directement
            if(this._action){
                this._action();
            }

            // Marque que l'action vient du tactile
            this._touchTriggered = true;
        };

        // Activation clavier (click)
        el.onclick = function(e){
            // Si le tactile vient de déclencher → on ignore le click
            if(this._touchTriggered){
                this._touchTriggered = false;
                return false;
            }

            // Sinon → c’est un vrai click clavier
            if(this._action){
                this._action();
            }
        };
    }
}
