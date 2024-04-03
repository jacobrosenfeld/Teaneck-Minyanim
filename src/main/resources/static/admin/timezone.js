// Function to populate the timezone dropdown
function populateTimezones() {
    const timezoneInputs = document.querySelectorAll('input[type="timezone"]');

    // Get all timezones
    const timezones = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const timezoneOptions = [...new Set([timezones])];

    // Populate the dropdown with timezone options
    timezoneOptions.forEach(timezone => {
        const option = document.createElement('option');
        option.text = timezone;
        option.value = timezone;
        timezoneDropdown.add(option);
    });
}

// Call the function to populate the dropdown when the page loads
window.onload = populateTimezones;

