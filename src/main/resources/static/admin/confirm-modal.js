/*
 * Shared in-app confirmation dialog for admin pages.
 *
 * Include the fragment once per page:
 *   <th:block th:replace="~{admin/fragments/confirm-modal :: confirmModal}"></th:block>
 *
 * Mark any trigger with data-confirm. Triggers should be type="button" so nothing is
 * submitted when JavaScript is unavailable. Supported attributes:
 *   data-confirm-title     dialog heading
 *   data-confirm-message   main sentence
 *   data-confirm-detail    optional extra context (e.g. the row being changed)
 *   data-confirm-label     confirm button text
 *   data-confirm-severity  danger (default) | warning | primary
 *   data-confirm-icon      optional Font Awesome icon name (e.g. "fa-ban"); anything else is ignored
 *   data-confirm-form      selector of the POST form to submit; defaults to the trigger's own form.
 *                          Only a <form> element is accepted. Prefer this (or the closest form).
 *   data-confirm-action    fallback when there is no form: a same-origin /admin path to POST to
 *                          (a form with the CSRF token is built). Any other URL is refused.
 *
 * All text is set with textContent, never parsed as HTML, and no attribute value is used as a link or
 * script URL. The only URL written to the DOM is the output of safeAdminAction().
 */
(function (window, document) {
    'use strict';

    if (window.AdminConfirm) {
        return;
    }

    var SEVERITIES = {
        danger: {button: 'btn-danger', icon: 'fa-triangle-exclamation'},
        warning: {button: 'btn-warning', icon: 'fa-circle-exclamation'},
        primary: {button: 'btn-primary', icon: 'fa-circle-question'}
    };

    var state = {trigger: null, form: null, action: null};

    function modalEl() {
        return document.getElementById('confirm-modal');
    }

    function part(name) {
        var modal = modalEl();
        return modal ? modal.querySelector('[data-confirm-part="' + name + '"]') : null;
    }

    var ICON_PATTERN = /^fa-[a-z0-9-]+$/;

    function isForm(element) {
        return element instanceof HTMLFormElement;
    }

    function resolveForm(trigger) {
        var selector = trigger.getAttribute('data-confirm-form');
        if (selector) {
            var target = null;
            try {
                target = document.querySelector(selector);
            } catch (ignored) {
                target = null;
            }
            return isForm(target) ? target : null;
        }
        var closest = trigger.closest('form');
        return isForm(closest) ? closest : null;
    }

    /**
     * Returns the normalized path + query of {@code value} when it is a same-origin http(s) URL
     * under /admin, otherwise null. Never returns the raw attribute value.
     */
    function safeAdminAction(value) {
        if (typeof value !== 'string' || value.trim() === '') {
            return null;
        }
        var url;
        try {
            url = new URL(value.trim(), window.location.origin);
        } catch (ignored) {
            return null;
        }
        if (url.protocol !== 'http:' && url.protocol !== 'https:') {
            return null;
        }
        if (url.origin !== window.location.origin) {
            return null;
        }
        if (url.pathname !== '/admin' && url.pathname.indexOf('/admin/') !== 0) {
            return null;
        }
        return url.pathname + url.search;
    }

    function refuseAction(value) {
        if (window.console && console.warn) {
            console.warn('Confirm dialog: refusing to submit to a URL outside /admin on this site.', value);
        }
    }

    function csrfInput() {
        var meta = document.querySelector('meta[name="_csrf"]');
        if (!meta || !meta.content) {
            return null;
        }
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = '_csrf';
        input.value = meta.content;
        return input;
    }

    function submitForm(form) {
        if (!form.querySelector('input[name="_csrf"]')) {
            var token = csrfInput();
            if (token) {
                form.appendChild(token);
            }
        }
        // Use the prototype so a field named "submit" cannot shadow the method.
        HTMLFormElement.prototype.submit.call(form);
    }

    function postTo(rawAction) {
        var safeAction = safeAdminAction(rawAction);
        if (safeAction === null) {
            refuseAction(rawAction);
            return false;
        }
        var form = document.createElement('form');
        form.method = 'post';
        form.action = safeAction;
        form.hidden = true;
        document.body.appendChild(form);
        submitForm(form);
        return true;
    }

    function open(trigger) {
        var modal = modalEl();
        if (!modal || !window.jQuery) {
            return false;
        }

        var form = resolveForm(trigger);
        var action = null;
        if (!form) {
            var rawAction = trigger.getAttribute('data-confirm-action');
            if (rawAction === null) {
                return false;
            }
            if (safeAdminAction(rawAction) === null) {
                refuseAction(rawAction);
                return false;
            }
            action = rawAction;
        }

        var severityName = trigger.getAttribute('data-confirm-severity') || 'danger';
        var severity = SEVERITIES[severityName] || SEVERITIES.danger;
        if (!SEVERITIES[severityName]) {
            severityName = 'danger';
        }

        part('title').textContent = trigger.getAttribute('data-confirm-title') || 'Are you sure?';
        part('message').textContent = trigger.getAttribute('data-confirm-message') || 'This action cannot be undone.';

        var detail = part('detail');
        var detailText = trigger.getAttribute('data-confirm-detail');
        detail.textContent = detailText || '';
        detail.hidden = !detailText;

        var icon = part('icon');
        icon.className = 'admin-modal-icon ' + severityName;
        var iconName = trigger.getAttribute('data-confirm-icon');
        icon.firstElementChild.className = 'fas ' + (iconName && ICON_PATTERN.test(iconName) ? iconName : severity.icon);

        var confirmButton = part('confirm');
        confirmButton.className = 'btn-modern ' + severity.button;
        confirmButton.disabled = false;
        confirmButton.querySelector('span').textContent = trigger.getAttribute('data-confirm-label') || 'Confirm';

        state.trigger = trigger;
        state.form = form;
        state.action = action;

        window.jQuery(modal).modal('show');
        return true;
    }

    function confirm() {
        if (!state.form && !state.action) {
            return;
        }
        var confirmButton = part('confirm');
        confirmButton.disabled = true;

        var form = state.form;
        var action = state.action;
        state.form = null;
        state.action = null;

        if (form) {
            // Keep focus restoration from jumping while the page navigates away.
            state.trigger = null;
            submitForm(form);
            return;
        }
        if (postTo(action)) {
            state.trigger = null;
            return;
        }
        // Refused: close the dialog and leave the page as it was (focus returns to the trigger).
        window.jQuery(modalEl()).modal('hide');
    }

    document.addEventListener('click', function (event) {
        var trigger = event.target.closest ? event.target.closest('[data-confirm]') : null;
        if (!trigger || trigger.disabled || trigger.getAttribute('aria-disabled') === 'true') {
            return;
        }
        event.preventDefault();
        open(trigger);
    });

    function bindModal() {
        var modal = modalEl();
        if (!modal || modal.dataset.confirmBound === 'true' || !window.jQuery) {
            return;
        }
        modal.dataset.confirmBound = 'true';

        part('confirm').addEventListener('click', confirm);

        window.jQuery(modal)
            .on('shown.bs.modal', function () {
                // Cancel is the safe default for destructive actions.
                var cancel = part('cancel');
                if (cancel) {
                    cancel.focus();
                }
            })
            .on('hidden.bs.modal', function () {
                var trigger = state.trigger;
                state.trigger = null;
                state.form = null;
                state.action = null;
                if (trigger && document.body.contains(trigger) && typeof trigger.focus === 'function') {
                    trigger.focus();
                }
            });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', bindModal);
    } else {
        bindModal();
    }

    window.AdminConfirm = {open: open, safeAdminAction: safeAdminAction};
})(window, document);
