// Function to populate the timezone input with Select2 dropdown
function populateTimezones() {
    // Target the timezone inputs
    const timezoneInputs = $('input[type="timezone"]');
    
    // Get all timezones using moment-timezone
    const timezones = moment.tz.names();
    
    // Object to store timezones grouped by region
    const timezonesByRegion = {};
    
    // Group timezones by region
    timezones.forEach(timezone => {
        const region = timezone.split('/')[0];
        if (!timezonesByRegion[region]) {
            timezonesByRegion[region] = [];
        }
        timezonesByRegion[region].push(timezone);
    });
    
    // Initialize Select2 for each timezone input
    timezoneInputs.each(function() {
        const selectElement = $(this);
        // Clear existing options
        selectElement.empty();
        
        // Iterate over regions
        for (const region in timezonesByRegion) {
            // Create optgroup for the region
            const optgroup = $('<optgroup label="' + region + '"></optgroup>');
            // Append timezones for the region
            timezonesByRegion[region].forEach(timezone => {
                optgroup.append($('<option></option>').attr('value', timezone).text(timezone));
            });
            // Append optgroup to select element
            selectElement.append(optgroup);
        }
        
        // Enable searching within the dropdown and set dropdownParent to the modal element
        selectElement.select2({
            placeholder: 'Select a timezone',
            dropdownCssClass: 'select2-dropdown--below',
            width: '100%',
            search: true,
            dropdownParent: selectElement.closest('.modal') // Assuming the select element is within a modal
        });

        // Listen for change event and update the corresponding input field
        selectElement.on('change', function() {
            const selectedValue = $(this).val();
            const inputField = $(this).siblings('input[type="hidden"]');
            inputField.val(selectedValue).trigger('change'); // Trigger change event for input field if needed
        });
    });
}

// Call the function to populate the timezone input with Select2 when the page loads
$(document).ready(function() {
    console.log('DOMContentLoaded event triggered.');
    populateTimezones();
});
