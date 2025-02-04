// Function to replace prayer names globally across the whole page
function replacePrayerNamesGlobally() {
    document.body.innerHTML = document.body.innerHTML
        .replace(/Shacharis/g, 'Shaharit') // Use 'Shaharit' for replacement
        .replace(/Maariv/g, 'Arvit');
}

// Function to replace prayer names in specific table rows containing 'Edot Hamizrach'
function replacePrayerNamesInRows(containingClass) {
    // Get all table row elements on the page
    var rows = document.getElementsByTagName('tr');

    // Loop through each row
    for (var i = 0; i < rows.length; i++) {
        // Check if the row has an element with the specified class (e.g., 'p-notes')
        var elements = rows[i].getElementsByClassName(containingClass);

        if (elements.length > 0) {
            // Check if 'Edot Hamizrach' is in the row's specific child element
            for (var j = 0; j < elements.length; j++) {
                if (elements[j].innerHTML.includes('Edot Hamizrach')) {
                    // Replace prayer names only in this specific row
                    rows[i].innerHTML = rows[i].innerHTML
                        .replace(/Shacharis/g, 'Shaharit') // Use 'Shaharit' for replacement
                        .replace(/Maariv/g, 'Arvit');
                    break; // Stop further checks for this row as we've done the replacement
                }
            }
        }
    }
}

// Main function to determine how to replace prayer names
function replacePrayerNames() {
    // Get the div containing org details
    var orgDetailsDiv = document.querySelector('.org-details'); // Change `.org-details` to match your HTML structure

    // Check if the org details div contains "Edot Hamizrach"
    var globalReplace = orgDetailsDiv && orgDetailsDiv.innerHTML.includes('Edot Hamizrach');

    if (globalReplace) {
        // If "Edot Hamizrach" is present in org details, replace globally
        replacePrayerNamesGlobally();
    } else {
        // Otherwise, replace prayer names in rows containing 'Edot Hamizrach' within a class like 'p-notes'
        replacePrayerNamesInRows('p-notes');
    }
}

// Call the main function to perform the replacements
replacePrayerNames();