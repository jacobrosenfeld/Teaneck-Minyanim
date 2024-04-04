function populateTimezones() {
    const timezoneInputs = document.querySelectorAll('input[type="timezone"]');

    // Get all timezones using moment-timezone
    const timezones = moment.tz.names();
    
    // Iterate over each input element with type="timezone"
    timezoneInputs.forEach(function(input) {
        // Get the class, id, and aria-describedby attributes from the input element
        const inputClass = input.getAttribute('class');
        const inputId = input.getAttribute('id');
        const ariaDescribedby = input.getAttribute('aria-describedby');
        const inputValue = input.getValue('value');

        // Create select element
        const select = document.createElement('select');

        // Set the select element's class, id, and aria-describedby attributes
        select.setAttribute('class', inputClass);
        select.setAttribute('id', inputId);
        select.setAttribute('aria-describedby', ariaDescribedby);
        select.setAttribute('value', inputValue);

        // Replace input with select
        input.parentNode.replaceChild(select, input);

        // Populate the select with timezone options
        timezones.forEach(timezone => {
            const option = document.createElement('option');
            option.text = timezone;
            option.value = timezone;
            select.appendChild(option);
        });
    });
}

// Call the function to populate the dropdown when the page loads
window.onload = populateTimezones;