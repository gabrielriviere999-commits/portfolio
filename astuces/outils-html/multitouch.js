function addMultiTouch(selector){
    var els = document.querySelectorAll(selector);

    for(var i=0; i<els.length; i++){
        var el = els[i];

        var action = el.getAttribute("data-action");
        if(action){
            el._action = window[action];
        }

        // pointerdown : détecte tactile ou souris
        el.onpointerdown = function(e){
            this._touchTriggered = (e.pointerType === "touch");
        };

        // pointerup : tactile → action ici
        el.onpointerup = function(e){
            if(this._touchTriggered && this._action){
                this._action();
            }
            // ⭐ NE PAS remettre à false ici
            // sinon le click PC n'est plus bloqué
        };

        // click : souris → action ici
        el.onclick = function(e){
            if(this._touchTriggered){
                // tactile → ignorer le click natif
                return false;
            }
            if(this._action){
                this._action();
            }
        };
    }
}
