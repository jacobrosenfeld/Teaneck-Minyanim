// Function to populate the timezone dropdown
function populateTimezones() {
    const timezoneInputs = document.querySelectorAll('input[type="timezone"]');

    // Get all timezones
    const timezones = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const timezoneOptions = [...new Set([timezones])];

    // Iterate over each input element with type="timezone"
    timezoneInputs.forEach(function(input) {
        // Populate the dropdown with timezone options for each input
        timezoneOptions.forEach(timezone => {
            const option = document.createElement('option');
            option.text = timezone;
            option.value = timezone;
            input.appendChild(option);
        });
    });
}

// Call the function to populate the dropdown when the page loads
window.onload = populateTimezones;
