// Function to replace 'Shacharis' with 'Shacharit' and 'Maariv' with 'Arvit' only in rows containing 'Edot Hamizrach'
function replacePrayerNames() {
    // Get all the rows in the table (`<tr>` elements)
    var rows = document.getElementsByTagName('tr');

    // Loop through each row
    for (var i = 0; i < rows.length; i++) {
        // Check if this row contains the text 'Edot Hamizrach'
        if (rows[i].innerHTML.includes('Edot Hamizrach')) {
            // Replace 'Shacharis' with 'Shacharit' and 'Maariv' with 'Arvit' within this row
            rows[i].innerHTML = rows[i].innerHTML
                .replace(/Shacharis/g, 'Shacharit')
                .replace(/Maariv/g, 'Arvit');
        }
    }
}

// Call the function to perform the replacement
replacePrayerNames();