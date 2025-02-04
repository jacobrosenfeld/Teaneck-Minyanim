// Function to replace 'Shacharis' with 'Shaharit' and 'Maariv' with 'Arvit'
function replacePrayerNames() {
    // Get the div containing org details
    var orgDetailsDiv = document.querySelector('.org-details'); // Change `.org-details` to the appropriate class or ID for the org details div

    // Check if the org details div contains "Edot Hamizrach"
    var globalReplace = orgDetailsDiv && orgDetailsDiv.innerHTML.includes('Edot Hamizrach');

    if (globalReplace) {
        // Perform global replacement across the whole page
        document.body.innerHTML = document.body.innerHTML
            .replace(/Shacharis/g, 'Shaharit')
            .replace(/Maariv/g, 'Arvit');
    } else {
        // If not globally replacing, target specific rows containing "Edot Hamizrach"
        var rows = document.getElementsByTagName('tr');

        // Loop through each row
        for (var i = 0; i < rows.length; i++) {
            // Check if this row contains the text 'Edot Hamizrach'
            if (rows[i].innerHTML.includes('Edot Hamizrach')) {
                // Replace 'Shacharis' with 'Shaharit' and 'Maariv' with 'Arvit' within this row
                rows[i].innerHTML = rows[i].innerHTML
                    .replace(/Shacharis/g, 'Shaharit')
                    .replace(/Maariv/g, 'Arvit');
            }
        }
    }
}

// Call the function to perform the replacement
replacePrayerNames();