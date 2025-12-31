function showChangePasswordModal() {
    console.log("change-password-btn clicked");
    document.getElementById("change-password-btn").click();
}

$(function () {
    // Initialize tooltips
    $('[data-toggle="tooltip"]').tooltip();

    // Explicitly bind modal triggers to ensure they work after jQuery/Bootstrap load
    $('#open-disable-account-modal').on('click', function (e) {
        e.preventDefault();
        $('#disable-account-modal').modal('show');
    });

    $('#open-enable-account-modal').on('click', function (e) {
        e.preventDefault();
        $('#enable-account-modal').modal('show');
    });

    $('#open-delete-account-modal').on('click', function (e) {
        if (!$(this).hasClass('disabled')) {
            e.preventDefault();
            $('#delete-account-modal').modal('show');
        }
    });
});