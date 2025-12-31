function showChangePasswordModal() {
    console.log("change-password-btn clicked");
    document.getElementById("change-password-btn").click();
}

$(function () {
    $('[data-toggle="tooltip"]').tooltip();

    $('#open-disable-account-modal').on('click', function () {
        $('#disable-account-modal').modal('show');
    });

    $('#open-enable-account-modal').on('click', function () {
        $('#enable-account-modal').modal('show');
    });

    $('#open-delete-account-modal').on('click', function () {
        if (!$(this).hasClass('disabled')) {
            $('#delete-account-modal').modal('show');
        }
    });
});