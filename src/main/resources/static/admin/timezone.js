// Function to populate the timezone input with Select2 dropdown
function populateTimezones() {
    // Target the timezone inputs
    const timezoneInputs = $('input[type="timezone"]');
    
    // Get all timezones using moment-timezone
    const timezones = moment.tz.names();
    const timezoneOptions = [...new Set(timezones)];
    
    // Initialize Select2 for each timezone input
    timezoneInputs.each(function() {
        $(this).select2({
            data: timezoneOptions.sort(),
            placeholder: 'Select a timezone',
            dropdownCssClass: 'select2-dropdown--below',
            width: '100%',
        });
    });
}

// Call the function to populate the timezone input with Select2 when the page loads
$(document).ready(function() {
    console.log('DOMContentLoaded event triggered.');
    populateTimezones();
});