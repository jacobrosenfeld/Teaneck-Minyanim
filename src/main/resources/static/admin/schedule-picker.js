/*
 * Shared cron schedule picker (templates/admin/fragments/schedule-picker.html).
 * Initialises every form[data-schedule-picker] on the page.
 *
 * Validation uses the form's preview endpoint (data-preview-url), which runs the same
 * server-side check as save (Spring CronExpression + minimum interval), so the browser never
 * disagrees with the server. Save stays disabled while a check is pending or the schedule is invalid.
 *
 * Hooks for the host page (dispatched on the form, bubbling):
 *   schedule-picker:statechange  detail {dirty, valid, pending}
 *   schedule-picker:saved        detail {result}
 * A toast is shown through window.showToast when the page provides it.
 */
(function () {
    'use strict';

    var PREVIEW_DELAY_MS = 300;
    var INVALID_SUMMARY = 'Fix the schedule above to see when it runs.';
    var SWITCH_WARNING = 'This custom schedule can\'t be shown with the day and time picker. '
        + 'Switching will replace it with the picker\'s schedule when you save. Continue?';

    function initSchedulePicker(form) {
        if (form.dataset.schedulePickerReady === 'true') {
            return;
        }
        form.dataset.schedulePickerReady = 'true';

        function part(role) {
            return form.querySelector('[data-role="' + role + '"]');
        }

        var simpleBlock = part('simple');
        var daysRow = part('days-row');
        var advancedToggle = part('advanced-toggle');
        var advancedBlock = part('advanced');
        var cronInput = part('cron');
        var cronError = part('cron-error');
        var simpleError = part('simple-error');
        var timeInput = part('time');
        var summary = part('summary');
        var nextRun = part('next-run');
        var state = part('state');
        var saveButton = part('save');
        var discardButton = part('discard');
        var previewUrl = form.dataset.previewUrl;

        var previewTimer = null;
        var previewRequest = 0;
        var pending = false;
        var saving = false;
        var valid = form.dataset.valid !== 'false';

        function currentMode() {
            return advancedToggle.checked ? 'ADVANCED' : 'SIMPLE';
        }

        function frequency() {
            var checked = form.querySelector('input[name="frequency"]:checked');
            return checked ? checked.value : 'WEEKLY';
        }

        function selectedDays() {
            return Array.prototype.map.call(form.querySelectorAll('input[name="days"]:checked'), function (input) {
                return input.value;
            });
        }

        // Mirrors CronSchedule.toCron on the server; null while the picker is incomplete.
        function cronFromPicker() {
            var match = /^(\d{2}):(\d{2})$/.exec(timeInput.value || '');
            if (!match) {
                return null;
            }
            var dayField = '*';
            if (frequency() === 'WEEKLY') {
                var days = selectedDays();
                if (days.length === 0) {
                    return null;
                }
                dayField = days.join(',');
            }
            return '0 ' + parseInt(match[2], 10) + ' ' + parseInt(match[1], 10) + ' * * ' + dayField;
        }

        // Form values without the CSRF token, plus the mode derived from the Advanced checkbox.
        function scheduleParams() {
            var data = new URLSearchParams(new FormData(form));
            data.delete('_csrf');
            data.set('mode', currentMode());
            return data;
        }

        function snapshot() {
            return scheduleParams().toString();
        }

        var savedSnapshot = snapshot();

        function isDirty() {
            return snapshot() !== savedSnapshot;
        }

        function emit(name, detail) {
            var event;
            try {
                event = new CustomEvent(name, {bubbles: true, detail: detail});
            } catch (e) {
                event = document.createEvent('CustomEvent');
                event.initCustomEvent(name, true, false, detail);
            }
            form.dispatchEvent(event);
        }

        function toast(message, kind) {
            if (typeof window.showToast === 'function') {
                window.showToast(message, kind);
            }
        }

        function setState(kind, icon, text) {
            state.className = 'field-state' + (kind ? ' ' + kind : '');
            state.querySelector('i').className = icon;
            state.querySelector('span').textContent = text;
        }

        function refreshControls() {
            var dirty = isDirty();
            saveButton.disabled = saving || pending || !valid;
            discardButton.disabled = saving || !dirty;
            emit('schedule-picker:statechange', {dirty: dirty, valid: valid, pending: pending});
        }

        function showDirtyState() {
            if (isDirty()) {
                setState('unsaved', 'far fa-circle-dot', 'Unsaved');
            } else {
                setState('', 'far fa-circle', 'Saved schedule');
            }
        }

        function applyModeUi() {
            var advanced = advancedToggle.checked;
            simpleBlock.hidden = advanced;
            advancedBlock.hidden = !advanced;
            daysRow.hidden = frequency() !== 'WEEKLY';
        }

        // Show or clear the inline error next to the field that is in use.
        function showFieldError(message) {
            var advanced = advancedToggle.checked;
            var cronMessage = advanced ? (message || '') : '';
            cronError.textContent = cronMessage;
            cronInput.classList.toggle('is-invalid', Boolean(cronMessage));
            cronInput.setAttribute('aria-invalid', cronMessage ? 'true' : 'false');
            simpleError.textContent = advanced ? '' : (message || '');
        }

        function showPreview(result) {
            valid = Boolean(result.valid);
            summary.classList.toggle('error', !valid);
            summary.textContent = valid ? result.summary : INVALID_SUMMARY;
            nextRun.textContent = valid && result.nextRun ? 'Next run: ' + result.nextRun : '';
            showFieldError(valid ? '' : (result.message || 'This schedule is not valid.'));
        }

        function fetchPreview(data) {
            return fetch(previewUrl + '?' + data.toString(), {
                headers: {'Accept': 'application/json'},
                credentials: 'same-origin'
            }).then(function (response) {
                if (!response.ok) {
                    throw new Error('The schedule could not be checked. Try again.');
                }
                return response.json();
            });
        }

        // Debounced validation through the preview endpoint; Save stays disabled until it answers.
        function schedulePreview() {
            clearTimeout(previewTimer);
            pending = true;
            refreshControls();
            previewTimer = setTimeout(function () {
                var requestId = ++previewRequest;
                fetchPreview(scheduleParams()).then(function (result) {
                    if (requestId === previewRequest) {
                        showPreview(result);
                    }
                }).catch(function (error) {
                    if (requestId === previewRequest) {
                        showPreview({valid: false, message: error.message});
                    }
                }).then(function () {
                    if (requestId === previewRequest) {
                        pending = false;
                        refreshControls();
                    }
                });
            }, PREVIEW_DELAY_MS);
        }

        function fillPicker(simple) {
            Array.prototype.forEach.call(form.querySelectorAll('input[name="frequency"]'), function (input) {
                input.checked = input.value === simple.frequency;
            });
            Array.prototype.forEach.call(form.querySelectorAll('input[name="days"]'), function (input) {
                input.checked = simple.days.indexOf(input.value) !== -1;
            });
            timeInput.value = simple.time;
        }

        function onChange() {
            applyModeUi();
            showDirtyState();
            schedulePreview();
        }

        function onFieldEvent(event) {
            if (event.target !== advancedToggle) {
                onChange();
            }
        }

        form.addEventListener('input', onFieldEvent);
        form.addEventListener('change', onFieldEvent);

        // Asks in the shared admin confirm dialog; resolves true only when the admin confirms.
        function confirmSwitch() {
            return new Promise(function (resolve) {
                var asked = window.AdminConfirm && window.AdminConfirm.ask({
                    opener: advancedToggle,
                    title: 'Replace custom schedule?',
                    message: SWITCH_WARNING,
                    label: 'Switch to simple',
                    severity: 'warning',
                    icon: 'fa-calendar-days',
                    onCancel: function () {
                        resolve(false);
                    }
                }, function () {
                    resolve(true);
                });
                if (!asked) {
                    // No dialog available: keep the custom schedule rather than replace it silently.
                    resolve(false);
                }
            });
        }

        advancedToggle.addEventListener('change', function () {
            if (advancedToggle.checked) {
                // Simple -> Advanced: start the cron box from the picker's schedule.
                var generated = cronFromPicker();
                if (generated) {
                    cronInput.value = generated;
                }
                onChange();
                cronInput.focus();
                return;
            }

            // Advanced -> Simple: parse the cron back into the picker when it fits; otherwise confirm.
            advancedToggle.disabled = true;
            var check = new URLSearchParams();
            check.set('mode', 'ADVANCED');
            check.set('cron', cronInput.value);
            fetchPreview(check).then(function (result) {
                if (result.valid && result.simple) {
                    fillPicker(result.simple);
                    return true;
                }
                return confirmSwitch();
            }, function () {
                return confirmSwitch();
            }).then(function (switched) {
                advancedToggle.disabled = false;
                if (!switched) {
                    // Cancelled: stay in Advanced with the custom schedule untouched.
                    advancedToggle.checked = true;
                    advancedToggle.focus();
                }
                onChange();
            });
        });

        // Make the saved values the ones the Discard button (form.reset) returns to.
        function commitDefaults() {
            Array.prototype.forEach.call(form.querySelectorAll('input'), function (input) {
                if (input.type === 'checkbox' || input.type === 'radio') {
                    input.defaultChecked = input.checked;
                } else if (input.type !== 'hidden') {
                    input.defaultValue = input.value;
                }
            });
        }

        discardButton.addEventListener('click', function () {
            form.reset();
            onChange();
        });

        form.addEventListener('submit', function (event) {
            event.preventDefault();
            if (saveButton.disabled) {
                return;
            }
            saving = true;
            saveButton.classList.add('saving');
            refreshControls();
            setState('', 'fas fa-spinner fa-spin', 'Saving');

            var body = new URLSearchParams(new FormData(form));
            body.set('mode', currentMode());
            fetch(form.action, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                credentials: 'same-origin',
                body: body
            }).then(function (response) {
                return response.json().catch(function () {
                    return {success: false, message: 'Schedule could not be saved.'};
                }).then(function (result) {
                    if (!response.ok || !result.success) {
                        throw new Error(result.message || 'Schedule could not be saved.');
                    }
                    return result;
                });
            }).then(function (result) {
                cronInput.value = result.cron;
                if (result.simple) {
                    fillPicker(result.simple);
                }
                commitDefaults();
                savedSnapshot = snapshot();
                applyModeUi();
                showPreview(result);
                setState('saved', 'fas fa-check-circle', 'Saved');
                toast('Schedule saved', 'success');
                emit('schedule-picker:saved', {result: result});
            }).catch(function (error) {
                // The server is authoritative: show its reason inline and keep Save off until the next edit.
                valid = false;
                showFieldError(error.message);
                summary.classList.add('error');
                summary.textContent = INVALID_SUMMARY;
                nextRun.textContent = '';
                setState('error', 'fas fa-exclamation-circle', 'Not saved');
                toast(error.message, 'error');
            }).then(function () {
                saving = false;
                saveButton.classList.remove('saving');
                refreshControls();
            });
        });

        applyModeUi();
        refreshControls();
    }

    function initAll() {
        Array.prototype.forEach.call(document.querySelectorAll('form[data-schedule-picker]'), initSchedulePicker);
    }

    window.initSchedulePickers = initAll;
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAll);
    } else {
        initAll();
    }
})();
