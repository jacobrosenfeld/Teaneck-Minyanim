$(function () {
    // Initialize tooltips
    $('[data-toggle="tooltip"]').tooltip();

    const password = document.getElementById('new-password');
    const confirmation = document.getElementById('new-cpassword');
    if (password && confirmation) {
        const validateConfirmation = function () {
            confirmation.setCustomValidity(
                confirmation.value && confirmation.value !== password.value
                    ? 'Passwords must match.'
                    : ''
            );
        };
        password.addEventListener('input', validateConfirmation);
        confirmation.addEventListener('input', validateConfirmation);
    }
});
