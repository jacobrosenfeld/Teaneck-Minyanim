// Function to replace prayer names globally across the whole page
function replacePrayerNamesGlobally() {
    document.body.innerHTML = document.body.innerHTML
        .replace(/Shacharis/g, 'Shaharit') // Replace globally in the body
        .replace(/Maariv/g, 'Arvit');
}

// Function to replace prayer names per specific <tr> rows or child scopes based on Nusach/Event Context
function replacePrayerNamesInPage() {
    // Target the org details div where Nusach is defined (global condition at line 149)
    var orgDetailsDiv = document.querySelector('.org-details');
    var nusachListItem = orgDetailsDiv.querySelector('li'); // Specifically the first <li> in 'org-details'

    // Check if 'Edot Hamizrach' is defined in Nusach and update text accordingly
    if (nusachListItem && nusachListItem.textContent.includes('Edot Hamizrach')) {
        replacePrayerNamesGlobally(); // If Edot Hamizrach, replace globally
    } else {
        // Scoped replacement logic
        // Target all rows in tables with minyan times
        var minyanRows = document.querySelectorAll('table.table-org tbody tr');

        // Loop through each row
        minyanRows.forEach(function (row) {
            // Check if row contains Nusach info (e.g., 'Edot Hamizrach') in targeted <p> tags
            var nusachElement = row.querySelector('.mobile-nusach');
            if (nusachElement && nusachElement.textContent.includes('Edot Hamizrach')) {
                // Replace only within this specific row
                row.innerHTML = row.innerHTML
                    .replace(/Shacharis/g, 'Shaharit') // Replace "Shacharis" as "Shaharit"
                    .replace(/Maariv/g, 'Arvit');
            }
        });
    }
}

// Call the main function to perform replacements
replacePrayerNamesInPage();