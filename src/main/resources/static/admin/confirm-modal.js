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
 *   data-confirm-icon      optional Font Awesome class (e.g. "fa-ban")
 *   data-confirm-form      selector of the POST form to submit; defaults to the trigger's own form
 *   data-confirm-action    URL to POST to when there is no form (a form with the CSRF token is built)
 *
 * All text is set with textContent, never as HTML.
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

    function resolveForm(trigger) {
        var selector = trigger.getAttribute('data-confirm-form');
        if (selector) {
            try {
                return document.querySelector(selector);
            } catch (ignored) {
                return null;
            }
        }
        return trigger.closest('form');
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

    function postTo(action) {
        var form = document.createElement('form');
        form.method = 'post';
        form.action = action;
        form.hidden = true;
        document.body.appendChild(form);
        submitForm(form);
    }

    function open(trigger) {
        var modal = modalEl();
        if (!modal || !window.jQuery) {
            return false;
        }

        var form = resolveForm(trigger);
        var action = trigger.getAttribute('data-confirm-action');
        if (!form && !action) {
            return false;
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
        icon.firstElementChild.className = 'fas ' + (trigger.getAttribute('data-confirm-icon') || severity.icon);

        var confirmButton = part('confirm');
        confirmButton.className = 'btn-modern ' + severity.button;
        confirmButton.disabled = false;
        confirmButton.querySelector('span').textContent = trigger.getAttribute('data-confirm-label') || 'Confirm';

        state.trigger = trigger;
        state.form = form;
        state.action = form ? null : action;

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
        // Keep focus restoration from jumping while the page navigates away.
        state.trigger = null;

        if (form) {
            submitForm(form);
        } else {
            postTo(action);
        }
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

    window.AdminConfirm = {open: open};
})(window, document);
