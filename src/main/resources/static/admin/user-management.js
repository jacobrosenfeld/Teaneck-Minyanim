document.addEventListener('DOMContentLoaded', function () {
    const search = document.getElementById('user-search');
    if (search) {
        search.addEventListener('input', function () {
            const query = search.value.trim().toLocaleLowerCase();
            let count = 0;
            document.querySelectorAll('.user-item').forEach(function (row) {
                row.hidden = !row.dataset.search.toLocaleLowerCase().includes(query);
                if (!row.hidden) count++;
            });
            document.getElementById('no-users').hidden = count > 0;
        });
    }
    document.querySelectorAll('.access-form').forEach(function (form) {
        const role = form.querySelector('.access-select');
        const shuls = form.querySelector('.shul-fieldset');
        const shulInputs = form.querySelectorAll('input[name="organizationIds"]');
        function updateScope() {
            const superAdmin = role.value === 'SUPER_ADMIN';
            if (shuls) shuls.disabled = superAdmin;
            shulInputs.forEach(function (input) { input.disabled = superAdmin; });
        }
        if (role) {
            role.addEventListener('change', updateScope);
            updateScope();
        }
    });
    document.querySelectorAll('form[data-confirm]').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            if (!window.confirm(form.dataset.confirm)) event.preventDefault();
        });
    });
    const createForm = document.querySelector('#new-user [data-open-create-user]');
    if (createForm?.dataset.openCreateUser === 'true') {
        document.getElementById('new-user')?.scrollIntoView();
    }
});
