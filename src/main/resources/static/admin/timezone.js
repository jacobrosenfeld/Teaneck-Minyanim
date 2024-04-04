// Function to populate the timezone input with datalist
function populateTimezones() {
    const timezoneInputs = document.querySelectorAll('[aria-describedby*="Time Zone"]');
    
    // Get all timezones using moment-timezone
    const timezones = moment.tz.names();
    const timezoneOptions = [...new Set(timezones)];
    console.log('Timezone options:', timezoneOptions);
    
    // Create datalist element
    const dataList = document.createElement('datalist');
    dataList.id = 'timezoneOptions';
    
    // Populate datalist with timezone options
    timezoneOptions.forEach(timezone => {
        const option = document.createElement('option');
        option.value = timezone;
        dataList.appendChild(option);
    });
    
    // Append datalist to the document body
    document.body.appendChild(dataList);
    
    // Associate each timezone input with the datalist
    timezoneInputs.forEach(input => {
        input.setAttribute('list', 'timezoneOptions');
    });
}

// Call the function to populate the timezone input with datalist when the page loads.
document.addEventListener('DOMContentLoaded', function() {
    console.log('DOMContentLoaded event triggered.');
    populateTimezones();
});
