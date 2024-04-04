function populateTimezones() {
    const timezoneInputs = document.querySelectorAll('input[type="timezone"]');

    // Get all timezones using moment-timezone
    var timezones = moment.tz.names();
    
    // Iterate over each input element with type="timezone"
    timezoneInputs.forEach(function(input) {
        // Get the class, id, and aria-describedby attributes from the input element
        const inputClass = input.getAttribute('class');
        const inputId = input.getAttribute('id');
        const ariaDescribedby = input.getAttribute('aria-describedby');
        const inputValue = input.value; // Use .value to get input value

        // Create select element
        const select = document.createElement('select');

        // Set the select element's class, id, and aria-describedby attributes
        select.setAttribute('class', inputClass);
        select.setAttribute('id', inputId);
        select.setAttribute('aria-describedby', ariaDescribedby);

        // Create an empty option element to represent the default value
        const defaultOption = document.createElement('option');
        defaultOption.text = "Select timezone"; // Change to your default option text if needed
        defaultOption.disabled = true;
        defaultOption.selected = true;
        select.appendChild(defaultOption);

        // Populate the select with timezone options
        timezones.forEach(timezone => {
            const option = document.createElement('option');
            option.text = timezone;
            option.value = timezone;
            select.appendChild(option);
        });

        // Replace input with select
        input.parentNode.replaceChild(select, input);

        // Set the selected value for the select element
        select.value = inputValue;
    });
}

// Call the function to populate the dropdown when the page loads
window.onload = populateTimezones;
