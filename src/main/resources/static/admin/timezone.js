// Function to populate the timezone dropdown
function populateTimezones() {
    const timezoneInputs = document.querySelectorAll('input[type="timezone"]');

    // Get all timezones
    const timezones = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const timezoneOptions = [...new Set([timezones])];

    // Iterate over each input element with type="timezone"
    timezoneInputs.forEach(function(input) {
        // Change input type to select
        input.setAttribute('type', 'select');

        // Create select element
        const select = document.createElement('select');

        // Populate the select with timezone options
        timezoneOptions.forEach(timezone => {
            const option = document.createElement('option');
            option.text = timezone;
            option.value = timezone;
            select.appendChild(option);
        });

        // Replace input with select
        input.parentNode.replaceChild(select, input);
    });
}

// Call the function to populate the dropdown when the page loads
window.onload = populateTimezones;
