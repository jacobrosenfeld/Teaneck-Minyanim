/* API keys page: create modal behaviour and the one-time key copy button. */
(function () {
    'use strict';

    function ready(fn) {
        if (document.readyState !== 'loading') fn();
        else document.addEventListener('DOMContentLoaded', fn);
    }

    function toast(message, type) {
        if (typeof window.showToast === 'function') window.showToast(message, type);
    }

    ready(function () {
        var page = document.getElementById('api-keys-page');
        var owner = document.getElementById('key-owner');
        var shuls = document.getElementById('key-shuls');
        var scopeList = document.getElementById('scope-list');
        var radios = document.querySelectorAll('input[name="permissions"]');

        // Only the owner's accessible shuls can be chosen; super admins can use every shul.
        function filterShuls() {
            if (!owner || !shuls) return;
            var option = owner.options[owner.selectedIndex];
            var isSuper = !!option && option.getAttribute('data-super') === 'true';
            var allowed = option && option.value
                ? (option.getAttribute('data-shuls') || '').split(',').filter(Boolean) : [];
            Array.prototype.forEach.call(shuls.options, function (o) {
                var ok = isSuper || allowed.indexOf(o.value) !== -1;
                o.hidden = !ok;
                o.disabled = !ok;
                if (!ok) o.selected = false;
            });
        }

        function syncScopes() {
            if (!scopeList) return;
            var specific = document.getElementById('perm-scopes');
            var on = !!specific && specific.checked;
            scopeList.hidden = !on;
            Array.prototype.forEach.call(scopeList.querySelectorAll('input[type="checkbox"]'), function (c) {
                c.disabled = !on;
            });
        }

        if (owner) owner.addEventListener('change', filterShuls);
        Array.prototype.forEach.call(radios, function (r) { r.addEventListener('change', syncScopes); });
        filterShuls();
        syncScopes();

        // Reopen the create modal when the server re-rendered it with a validation error.
        if (page && page.getAttribute('data-open-create') === 'true' && window.jQuery) {
            window.jQuery('#create-key-modal').modal('show');
        }

        // One-time key panel: copy button.
        var copyBtn = document.getElementById('copy-key-btn');
        if (copyBtn) {
            copyBtn.addEventListener('click', function () {
                var valueEl = document.getElementById('new-key-value');
                var label = document.getElementById('copy-key-label');
                var feedback = document.getElementById('copy-key-feedback');
                var text = valueEl ? valueEl.textContent.trim() : '';

                function done(ok) {
                    var message = ok ? 'Copied to clipboard.' : 'Could not copy automatically. Select the key and copy it manually.';
                    if (feedback) feedback.textContent = message;
                    if (label && ok) {
                        label.textContent = 'Copied';
                        setTimeout(function () { label.textContent = 'Copy key'; }, 2000);
                    }
                    toast(message, ok ? 'success' : 'error');
                }

                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(text).then(function () { done(true); }, function () { done(false); });
                } else {
                    done(false);
                }
            });
        }
    });
})();
