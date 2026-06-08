document.addEventListener('DOMContentLoaded', function () {
    var manager = document.getElementById('passkey-manager');
    var form = document.getElementById('passkey-register-form');
    var labelInput = document.getElementById('passkey-label');
    var registerButton = document.getElementById('passkey-register-button');
    var status = document.getElementById('passkey-status');

    if (!manager || !form || !labelInput || !registerButton) {
        return;
    }

    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        var label = labelInput.value.trim();
        if (!label) {
            setStatus('Add a label so you can recognize this passkey later.', 'error');
            return;
        }
        if (!passkeysCanRunHere()) {
            setStatus(passkeyUnavailableMessage(), 'error');
            return;
        }

        setBusy(true);
        setStatus('Waiting for your browser passkey prompt...', '');
        try {
            await registerPasskey(manager, label);
            setStatus('Passkey registered. Refreshing...', 'success');
            window.location.href = '/webauthn/register?success=true';
        } catch (error) {
            console.error('Passkey registration failed.', error);
            setStatus('Passkey registration did not complete. Try again from this browser or device.', 'error');
        } finally {
            setBusy(false);
        }
    });

    document.querySelectorAll('.passkey-delete-button').forEach(function (button) {
        button.addEventListener('click', async function () {
            var credentialId = button.dataset.credentialId;
            if (!credentialId) {
                return;
            }
            button.disabled = true;
            setStatus('Deleting passkey...', '');
            try {
                await deletePasskey(manager, credentialId);
                setStatus('Passkey deleted. Refreshing...', 'success');
                window.location.href = '/webauthn/register?deleted=true';
            } catch (error) {
                console.error('Passkey delete failed.', error);
                button.disabled = false;
                setStatus('Could not delete that passkey. Try again.', 'error');
            }
        });
    });

    function setBusy(isBusy) {
        registerButton.disabled = isBusy;
        labelInput.disabled = isBusy;
    }

    function setStatus(message, type) {
        status.textContent = message || '';
        status.classList.toggle('success', type === 'success');
        status.classList.toggle('error', type === 'error');
    }
});

async function registerPasskey(manager, label) {
    var options = await fetchJson(manager.dataset.registerOptionsUrl, {});
    var credentialOptions = decodeCredentialCreationOptions(options);
    var credential = await navigator.credentials.create({ publicKey: credentialOptions });
    var body = {
        publicKey: {
            credential: buildAttestationPayload(credential),
            label: label
        }
    };
    var response = await fetchJson(manager.dataset.registerUrl, body);
    if (!response || response.success !== true) {
        throw new Error('Registration endpoint did not confirm success.');
    }
}

async function deletePasskey(manager, credentialId) {
    var response = await fetch(manager.dataset.deleteBaseUrl + encodeURIComponent(credentialId), {
        method: 'DELETE'
    });
    if (!response.ok && response.status !== 204) {
        throw new Error('HTTP ' + response.status);
    }
}

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
        && typeof navigator.credentials.create === 'function';
}

function passkeyUnavailableMessage() {
    if (window.isSecureContext === false) {
        return 'Passkeys require HTTPS or localhost.';
    }
    return 'This browser is not exposing passkey registration right now.';
}

function decodeCredentialCreationOptions(options) {
    return {
        ...options,
        challenge: base64UrlToArrayBuffer(options.challenge),
        user: {
            ...options.user,
            id: base64UrlToArrayBuffer(options.user.id)
        },
        excludeCredentials: (options.excludeCredentials || []).map(function (credential) {
            return {
                ...credential,
                id: base64UrlToArrayBuffer(credential.id)
            };
        })
    };
}

function buildAttestationPayload(credential) {
    var response = credential.response;
    return {
        id: credential.id,
        rawId: arrayBufferToBase64Url(credential.rawId),
        response: {
            attestationObject: arrayBufferToBase64Url(response.attestationObject),
            clientDataJSON: arrayBufferToBase64Url(response.clientDataJSON),
            transports: response.getTransports ? response.getTransports() : []
        },
        type: credential.type,
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
