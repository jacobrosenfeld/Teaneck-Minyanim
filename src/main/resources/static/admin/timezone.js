// Function to populate the timezone select with options
function populateTimezones() {
    const timezoneSelects = document.querySelectorAll('select[type="timezone"]');
    
    // Get all timezones using moment-timezone
    const timezones = moment.tz.names();
    const timezoneOptions = [...new Set(timezones)];
    console.log('Timezone options:', timezoneOptions);
    
    // Populate each select with timezone options
    timezoneSelects.forEach(select => {
        timezoneOptions.forEach(timezone => {
            const option = document.createElement('option');
            option.value = timezone;
            option.text = timezone;
            select.appendChild(option);
        });
    });
}

// Call the function to populate the timezone select with options when the page loads.
document.addEventListener('DOMContentLoaded', function() {
    console.log('DOMContentLoaded event triggered.');
    populateTimezones();
});