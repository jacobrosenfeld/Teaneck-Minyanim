// Function to populate the timezone dropdown
function populateTimezones() {
    const timezoneInputs = document.querySelectorAll('input[type="timezone"]');

    // Get all timezones using moment-timezone
    const timezones = moment.tz.names();
    const timezoneOptions = [...new Set(timezones)];

    // Iterate over each input element with type="timezone"
    timezoneInputs.forEach(function(input) {
        // Get the class, id, and aria-describedby attributes from the input element
        const inputClass = input.getAttribute('class');
        const inputId = input.getAttribute('id');
        const ariaDescribedby = input.getAttribute('aria-describedby');
        const currentZone = input.value; // Get current timezone value from input value

        // Create select element
        const select = document.createElement('select');

        // Set the select element's class, id, and aria-describedby attributes
        select.setAttribute('class', inputClass);
        select.setAttribute('id', inputId);
        select.setAttribute('aria-describedby', ariaDescribedby);

        // Populate the select with timezone options
        timezoneOptions.forEach(timezone => {
            const option = document.createElement('option');
            option.text = timezone;
            option.value = timezone;
            // Set selected attribute if timezone matches currentZone
            if (timezone === currentZone) {
                option.selected = true;
            }
            select.appendChild(option);
        });

        // Replace input with select
        input.parentNode.replaceChild(select, input);
    });
}

// Call the function to populate the dropdown when the page loads.
document.addEventListener('DOMContentLoaded', populateTimezones);
