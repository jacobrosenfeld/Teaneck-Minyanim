document.addEventListener('DOMContentLoaded', function () {
    const search = document.getElementById('user-search');
    search.addEventListener('input', function () {
        const query = search.value.trim().toLocaleLowerCase();
        let count = 0;
        document.querySelectorAll('.user-item').forEach(function (row) {
            row.hidden = !row.dataset.search.toLocaleLowerCase().includes(query);
            if (!row.hidden) count++;
        });
        document.getElementById('no-users').hidden = count > 0;
    });
    document.querySelectorAll('.access-form').forEach(function (form) {
        const role = form.querySelector('.access-select');
        const shuls = form.querySelector('.shul-fieldset');
        function updateScope() {
            shuls.disabled = role.value === 'SUPER_ADMIN';
        }
        role.addEventListener('change', updateScope);
        updateScope();
    });
    document.querySelectorAll('form[data-confirm]').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            if (!window.confirm(form.dataset.confirm)) event.preventDefault();
        });
    });
    if (document.querySelector('#new-user form [value]:invalid') || document.querySelector('#new-user [data-open-create-user]')) {
        document.getElementById('new-user')?.scrollIntoView();
    }
});
