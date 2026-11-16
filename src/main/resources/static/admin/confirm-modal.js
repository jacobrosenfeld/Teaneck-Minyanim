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
 * Script callers (e.g. AJAX actions) use AdminConfirm.ask(options, onConfirm); see ask() below.
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

    var state = {trigger: null, form: null, action: null, onConfirm: null, onSecondary: null};

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

    function resetState() {
        state.trigger = null;
        state.form = null;
        state.action = null;
        state.onConfirm = null;
        state.onSecondary = null;
    }

    /**
     * Fills and shows the dialog. config: {opener, title, message, detail, label, severity, icon,
     * form, action, onConfirm, secondary: {label, icon, onClick}}. Exactly one of form, action
     * (already validated) or onConfirm says what Confirm does.
     */
    function show(config) {
        var modal = modalEl();
        if (!modal || !window.jQuery) {
            return false;
        }

        var severityName = SEVERITIES[config.severity] ? config.severity : 'danger';
        var severity = SEVERITIES[severityName];

        part('title').textContent = config.title || 'Are you sure?';
        part('message').textContent = config.message || 'This action cannot be undone.';

        var detail = part('detail');
        detail.textContent = config.detail || '';
        detail.hidden = !config.detail;

        var icon = part('icon');
        icon.className = 'admin-modal-icon ' + severityName;
        icon.firstElementChild.className = 'fas ' + (config.icon && ICON_PATTERN.test(config.icon) ? config.icon : severity.icon);

        var confirmButton = part('confirm');
        confirmButton.className = 'btn-modern ' + severity.button;
        confirmButton.disabled = false;
        confirmButton.querySelector('span').textContent = config.label || 'Confirm';

        var secondaryButton = part('secondary');
        var secondary = config.secondary && typeof config.secondary.onClick === 'function' ? config.secondary : null;
        if (secondaryButton) {
            secondaryButton.hidden = !secondary;
            secondaryButton.querySelector('span').textContent = secondary ? (secondary.label || '') : '';
            var secondaryIcon = secondaryButton.querySelector('i');
            var secondaryIconName = secondary && secondary.icon && ICON_PATTERN.test(secondary.icon) ? secondary.icon : null;
            secondaryIcon.className = secondaryIconName ? 'fas ' + secondaryIconName : '';
            secondaryIcon.hidden = !secondaryIconName;
        }

        resetState();
        state.trigger = config.opener || null;
        state.form = config.form || null;
        state.action = state.form ? null : (config.action || null);
        state.onConfirm = typeof config.onConfirm === 'function' ? config.onConfirm : null;
        state.onSecondary = secondary ? secondary.onClick : null;

        window.jQuery(modal).modal('show');
        return true;
    }

    /** Opens the dialog for a [data-confirm] trigger, submitting its form (or validated action URL). */
    function open(trigger) {
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
        return show({
            opener: trigger,
            title: trigger.getAttribute('data-confirm-title'),
            message: trigger.getAttribute('data-confirm-message'),
            detail: trigger.getAttribute('data-confirm-detail'),
            label: trigger.getAttribute('data-confirm-label'),
            severity: trigger.getAttribute('data-confirm-severity'),
            icon: trigger.getAttribute('data-confirm-icon'),
            form: form,
            action: action
        });
    }

    /**
     * For pages that act in script (e.g. AJAX): AdminConfirm.ask({title, message, detail, label,
     * severity, icon, opener, secondary: {label, icon, onClick}}, onConfirm). onConfirm runs after
     * the dialog closes; so does secondary.onClick. Returns false if the dialog is unavailable.
     */
    function ask(options, onConfirm) {
        options = options || {};
        if (typeof onConfirm !== 'function') {
            return false;
        }
        return show({
            opener: options.opener || document.activeElement,
            title: options.title,
            message: options.message,
            detail: options.detail,
            label: options.label,
            severity: options.severity,
            icon: options.icon,
            secondary: options.secondary,
            onConfirm: onConfirm
        });
    }

    function hideThen(callback) {
        window.jQuery(modalEl()).one('hidden.bs.modal', function () {
            callback();
        }).modal('hide');
    }

    function confirm() {
        var form = state.form;
        var action = state.action;
        var onConfirm = state.onConfirm;
        if (!form && !action && !onConfirm) {
            return;
        }
        part('confirm').disabled = true;
        state.form = null;
        state.action = null;
        state.onConfirm = null;
        state.onSecondary = null;

        if (onConfirm) {
            hideThen(onConfirm);
            return;
        }
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

    function secondaryAction() {
        var onSecondary = state.onSecondary;
        if (!onSecondary) {
            return;
        }
        state.onSecondary = null;
        state.onConfirm = null;
        // The secondary action usually opens another dialog, which takes focus from here.
        state.trigger = null;
        hideThen(onSecondary);
    }

    document.addEventListener('click', function (event) {
        var trigger = event.target.closest ? event.target.closest('[data-confirm]') : null;
        // Only buttons and links are triggers; a click anywhere inside a container never is.
        if (!trigger || (trigger.tagName !== 'BUTTON' && trigger.tagName !== 'A')) {
            return;
        }
        if (trigger.disabled || trigger.getAttribute('aria-disabled') === 'true') {
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
        var secondaryButton = part('secondary');
        if (secondaryButton) {
            secondaryButton.addEventListener('click', secondaryAction);
        }

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
                resetState();
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

    window.AdminConfirm = {open: open, ask: ask, safeAdminAction: safeAdminAction};
})(window, document);
