// Function to replace prayer names globally across the whole page
function replacePrayerNamesGlobally() {
    document.body.innerHTML = document.body.innerHTML
        .replace(/Shacharis/g, 'Shaharit') // Replace globally in the body
        .replace(/Maariv/g, 'Arvit');
}

// Function to replace prayer names in specific `tr` rows or scoped child elements based on context
function replacePrayerNamesInRows(containingClass, checkText) {
    // Get all rows that could potentially contain the specified class
    var rows = document.getElementsByTagName('tr');

    // Loop through each row
    for (var i = 0; i < rows.length; i++) {
        // Check if the row has child elements with the specified class
        var elements = rows[i].getElementsByClassName(containingClass);

        // If any elements with the containingClass exist in the row
        if (elements.length > 0) {
            for (var j = 0; j < elements.length; j++) {
                // Perform replacement if the content includes the specified checkText
                if (elements[j].innerHTML.includes(checkText)) {
                    rows[i].innerHTML = rows[i].innerHTML
                        .replace(/Shacharis/g, 'Shaharit') // Replace prayer names
                        .replace(/Maariv/g, 'Arvit');
                    break; // Stop processing once the replacement is done
                }
            }
        }
    }
}

// Main function to determine how/where to perform replacements
function replacePrayerNames() {
    // Get the div containing org details (specific to the org page)
    var orgDetailsDiv = document.querySelector('.org-details');
    var homepageRowsFound = false;

    // 1. Check if "Edot Hamizrach" is globally defined in org details
    if (orgDetailsDiv) {
        var nusachElement = orgDetailsDiv.querySelector('li'); // First <li> inside the org-details div

        // If the Nusach includes "Edot Hamizrach", perform a global replacement
        if (nusachElement && nusachElement.textContent.includes('Edot Hamizrach')) {
            replacePrayerNamesGlobally();
            return; // Exit as global replacement is already done
        }
    }

    // 2. Handle homepage logic
    // If we're on the homepage, look specifically for rows with `.p-notes` and "Edot Hamizrach"
    var homepageRows = document.querySelectorAll('tr'); // All rows on the page
    homepageRows.forEach(function (row) {
        var pNotes = row.querySelector('.p-notes'); // Check for elements inside rows
        if (pNotes && pNotes.innerHTML.includes('Edot Hamizrach')) {
            row.innerHTML = row.innerHTML
                .replace(/Shacharis/g, 'Shaharit') // Replace "Shacharis" with "Shaharit"
                .replace(/Maariv/g, 'Arvit');
            homepageRowsFound = true; // Mark that homepage-specific rows were found and updated
        }
    });

    // 3. If no homepage rows were updated, handle the default per-row minyan behavior on the org page
    if (!homepageRowsFound && orgDetailsDiv) {
        var minyanRows = document.querySelectorAll('table.table-org tbody tr'); // Target <tr> rows in minyan tables

        minyanRows.forEach(function (row) {
            // Check for mobile-nusach elements within each row
            var nusachElement = row.querySelector('.mobile-nusach');
            if (nusachElement && nusachElement.textContent.includes('Edot Hamizrach')) {
                row.innerHTML = row.innerHTML
                    .replace(/Shacharis/g, 'Shaharit') // Replace "Shacharis" with "Shaharit"
                    .replace(/Maariv/g, 'Arvit');
            }
        });
    }
}

// Call the main function to perform replacements
replacePrayerNames();