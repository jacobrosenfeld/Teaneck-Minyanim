// Function to replace 'Shacharis' with 'Shacharit' and 'Maariv' with 'Arvit' if 'Edot Hamizrach' is present
function replacePrayerNames() {
    // Get all the table row elements on the page
    var rows = document.getElementsByTagName('tr');
  
    // Loop through each row
    for (var i = 0; i < rows.length; i++) {
      // Check if the row has an element with class 'p-notes'
      var pNotes = rows[i].getElementsByClassName('p-notes');
      if (pNotes.length > 0) {
        // Check if 'Edot Hamizrach' is in the row
        if (pNotes[0].innerHTML.includes('Edot Hamizrach')) {
          // Replace 'Shacharis' with 'Shacharit' and 'Maariv' with 'Arvit' in that row
          rows[i].innerHTML = rows[i].innerHTML.replace(/Shacharis/g, 'Shacharit').replace(/Maariv/g, 'Arvit');
        }
      }
    }
  }
  
  // Call the function to perform the replacement
  replacePrayerNames();
  