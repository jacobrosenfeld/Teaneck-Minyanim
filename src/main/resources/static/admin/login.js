function preparePasswordLogin() {
    var identifierField = document.getElementById('login-identifier-field');
    var passwordUsernameField = document.getElementById('password-username-field');
    if (!identifierField || !passwordUsernameField) {
        return true;
    }
    passwordUsernameField.value = normalizedIdentifier(identifierField.value);
    return true;
}

function normalizedIdentifier(value) {
    return (value || '').trim();
}

document.addEventListener('DOMContentLoaded', function () {
    var identifierForm = document.getElementById('identifier-form');
    var identifierField = document.getElementById('login-identifier-field');
    var submitButton = document.getElementById('identifier-submit');
    var status = document.getElementById('login-flow-status');
    var passwordToggle = document.getElementById('password-toggle');
    var passwordPanel = document.getElementById('login-form');
    var passwordField = document.getElementById('password-field');

    if (!identifierForm || !identifierField || !submitButton) {
        return;
    }

    identifierField.addEventListener('input', function () {
        submitButton.disabled = normalizedIdentifier(identifierField.value).length === 0;
        preparePasswordLogin();
    });

    if (passwordToggle && passwordPanel) {
        passwordToggle.addEventListener('click', function () {
            showPasswordPanel();
        });
    }

    if (passwordPanel && passwordPanel.classList.contains('is-open')) {
        passwordToggle && passwordToggle.classList.remove('d-none');
    } else if (passwordField) {
        passwordField.disabled = true;
    }

    identifierForm.addEventListener('submit', async function (event) {
        event.preventDefault();

        var identifier = normalizedIdentifier(identifierField.value);
        if (!identifier) {
            return;
        }

        preparePasswordLogin();
        setLoading(true);
        setStatus('Checking sign-in methods...', '');

        try {
            var methods = await loginMethods(identifierForm, identifier);
            if (methods.passkeyAvailable && passkeysCanRunHere()) {
                try {
                    setStatus('Use your passkey to continue.', '');
                    await authenticateWithPasskey(identifierForm);
                    return;
                } catch (error) {
                    console.warn('Passkey authentication did not complete.', error);
                    setStatus('Passkey was not completed. Sending a sign-in link instead.', '');
                }
            } else if (methods.passkeyAvailable && !passkeysCanRunHere()) {
                setStatus(passkeyUnavailableMessage(), '');
            }

            await requestMagicLink(identifierForm, identifier);
        } catch (error) {
            console.error('Login method check failed.', error);
            setStatus('Could not complete the passwordless check. You can try again or use password instead.', 'error');
            showPasswordOption();
        } finally {
            setLoading(false);
        }
    });

    function loginMethods(form, identifier) {
        return fetchJson(form.dataset.authMethodsUrl, { identifier: identifier }).catch(function (error) {
            console.warn('Login method check failed. Falling back to magic link.', error);
            return { passkeyAvailable: false, magicLinkAvailable: true };
        });
    }

    function requestMagicLink(form, identifier) {
        return fetchJson(form.dataset.magicLinkUrl, { identifier: identifier }).then(function (response) {
            setStatus(response.message || 'If this email is authorized, a sign-in link will be sent shortly.', 'success');
            showPasswordOption();
        });
    }

    function showPasswordOption() {
        if (passwordToggle) {
            passwordToggle.classList.remove('d-none');
        }
    }

    function showPasswordPanel() {
        showPasswordOption();
        if (passwordPanel) {
            passwordPanel.classList.add('is-open');
        }
        if (passwordField) {
            passwordField.disabled = false;
            passwordField.focus();
        }
    }

    function setLoading(isLoading) {
        submitButton.disabled = isLoading || normalizedIdentifier(identifierField.value).length === 0;
        submitButton.classList.toggle('is-loading', isLoading);
        identifierField.disabled = isLoading;
    }

    function setStatus(message, type) {
        if (!status) {
            return;
        }
        status.textContent = message || '';
        status.classList.toggle('login-status-error', type === 'error');
        status.classList.toggle('login-status-success', type === 'success');
    }
});

async function fetchJson(url, body) {
    var response = await fetch(url, {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(body || {})
    });
    if (!response.ok) {
        throw new Error('HTTP ' + response.status);
    }
    return response.json();
}

function passkeysCanRunHere() {
    return window.isSecureContext !== false
        && !!window.PublicKeyCredential
        && !!navigator.credentials
        && typeof navigator.credentials.get === 'function';
}

function passkeyUnavailableMessage() {
    if (window.isSecureContext === false) {
        return 'Passkeys require HTTPS or localhost. Sending a sign-in link instead.';
    }
    return 'This browser is not exposing passkeys right now. Sending a sign-in link instead.';
}

async function authenticateWithPasskey(form) {
    var options = await fetchJson(form.dataset.webauthnOptionsUrl, {});
    var publicKey = decodeCredentialRequestOptions(options);
    var credential = await navigator.credentials.get({ publicKey: publicKey });
    var loginResponse = await fetchJson(form.dataset.webauthnLoginUrl, buildAssertionPayload(credential));

    if (!loginResponse || !loginResponse.authenticated || !loginResponse.redirectUrl) {
        throw new Error('Passkey authentication did not return a redirect.');
    }
    window.location.href = loginResponse.redirectUrl;
}

function decodeCredentialRequestOptions(options) {
    return {
        ...options,
        challenge: base64UrlToArrayBuffer(options.challenge),
        allowCredentials: (options.allowCredentials || []).map(function (credential) {
            return {
                ...credential,
                id: base64UrlToArrayBuffer(credential.id)
            };
        })
    };
}

function buildAssertionPayload(credential) {
    var response = credential.response;
    return {
        id: credential.id,
        rawId: arrayBufferToBase64Url(credential.rawId),
        response: {
            authenticatorData: arrayBufferToBase64Url(response.authenticatorData),
            clientDataJSON: arrayBufferToBase64Url(response.clientDataJSON),
            signature: arrayBufferToBase64Url(response.signature),
            userHandle: response.userHandle ? arrayBufferToBase64Url(response.userHandle) : null
        },
        credType: credential.type,
        clientExtensionResults: credential.getClientExtensionResults ? credential.getClientExtensionResults() : {},
        authenticatorAttachment: credential.authenticatorAttachment
    };
}

function base64UrlToArrayBuffer(value) {
    var base64 = value.replace(/-/g, '+').replace(/_/g, '/');
    var padding = base64.length % 4;
    if (padding) {
        base64 += '='.repeat(4 - padding);
    }
    var binary = window.atob(base64);
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < binary.length; i += 1) {
        bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
}

function arrayBufferToBase64Url(buffer) {
    var bytes = new Uint8Array(buffer);
    var binary = '';
    for (var i = 0; i < bytes.byteLength; i += 1) {
        binary += String.fromCharCode(bytes[i]);
    }
    return window.btoa(binary)
        .replace(/=/g, '')
        .replace(/\+/g, '-')
        .replace(/\//g, '_');
}
