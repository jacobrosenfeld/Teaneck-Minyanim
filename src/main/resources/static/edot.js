// Function to replace 'Shacharis' with 'Shacharit' and 'Maariv' with 'Arvit' if 'Edot Hamizrach' is present
function replacePrayerNames() {
    // Get all elements with class 'org-nusach'
    var orgNusachElements = document.getElementsByClassName('mobile-nusach');

    // Check if any of these elements contain 'Edot Hamizrach'
    var containsEdotHamizrach = Array.from(orgNusachElements).some(el => el.innerHTML.includes('Edot Hamizrach'));

    if (containsEdotHamizrach) {
      // Get all the table elements on the page
      var tables = document.getElementsByTagName('table');
      var h4 = document.getElementsByTagName('h4');
      var h5 = document.getElementsByTagName('h5');

      // Loop through each table
      for (var i = 0; i < tables.length; i++) {
        // Replace 'Shacharis' with 'Shacharit' and 'Maariv' with 'Arvit' in that table
        tables[i].innerHTML = tables[i].innerHTML.replace(/Shacharis/g, 'Shacharit').replace(/Maariv/g, 'Arvit');
      }

      // Loop through each h4
        for (var i = 0; i < h4.length; i++) {
            // Replace 'Shacharis' with 'Shacharit' and 'Maariv' with 'Arvit' in that h4
            h4[i].innerHTML = h4[i].innerHTML.replace(/Shacharis/g, 'Shacharit').replace(/Maariv/g, 'Arvit');
        }
      // Loop through each h5
        for (var i = 0; i < h5.length; i++) {
            // Replace 'Shacharis' with 'Shacharit' and 'Maariv' with 'Arvit' in that h5
            h5[i].innerHTML = h5[i].innerHTML.replace(/Shacharis/g, 'Shacharit').replace(/Maariv/g, 'Arvit');
        }

      // Get all the button elements on the page
      var buttons = document.getElementsByTagName('button');

      // Loop through each button
      for (var i = 0; i < buttons.length; i++) {
        // Replace 'Shacharis' with 'Shacharit' and 'Maariv' with 'Arvit' in that button
        buttons[i].innerHTML = buttons[i].innerHTML.replace(/Shacharis/g, 'Shacharit').replace(/Maariv/g, 'Arvit');
      }
    }
  }

  // Call the function to perform the replacement
  replacePrayerNames();