var table = document.getElementById("minyan-table");
var loadMoreButton = document.getElementById("load-more-button");
var numRowsToShow = 10;
var numRowsShowing = 0;

// Hide rows that exceed the number of rows to show
for (var i = numRowsToShow; i < table.rows.length; i++) {
  table.rows[i].style.display = "none";
}

// Update the number of rows showing
numRowsShowing = numRowsToShow;

// Add a click event listener to the load more button
loadMoreButton.addEventListener("click", function() {
  // Show the next set of rows
  for (var i = numRowsShowing; i < numRowsShowing + numRowsToShow; i++) {
    if (i < table.rows.length) {
      table.rows[i].style.display = "";
    }
  }

  // Update the number of rows showing
  numRowsShowing += numRowsToShow;

  // Hide the load more button if all rows are showing
  if (numRowsShowing >= table.rows.length) {
    loadMoreButton.style.display = "none";
  }
});


