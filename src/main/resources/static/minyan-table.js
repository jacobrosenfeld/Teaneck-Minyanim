let tables = document.querySelectorAll(".minyan-table");
let rows = []; // initialize an empty array to store all rows
let rowNums = []; // initialize an empty array to store the current row numbers
let increments = [5, 5]; // the number of rows to add each time the load more button is clicked for each table
let loadMoreButtons = document.querySelectorAll(".load-more-button");

// loop through all tables to get the rows and set up the load more button
for (let i = 0; i < tables.length; i++) {
  let table = tables[i];
  rows[i] = table.rows;
  rowNums[i] = 10; // the number of rows to display initially

  // hide all rows except for the first 10
  for (let j = rowNums[i]; j < rows[i].length; j++) {
    rows[i][j].style.display = "none";
  }

  // hide load more button if there are 5 or fewer rows
  if (rows[i].length <= 5) {
    loadMoreButtons[i].style.display = "none";
  } else {
    // add event listener to the load more button
    loadMoreButtons[i].addEventListener("click", function () {
      loadMore(table, i);
    });
  }
}

function loadMore(table, i) {
  let numRows = rows[i].length;
  let increment = increments[i];

  // add 5 more rows if there are still rows left to display
  if (rowNums[i] + increment < numRows) {
    for (let j = rowNums[i]; j < rowNums[i] + increment; j++) {
      rows[i][j].style.display = "";
    }
    rowNums[i] += increment;
  } else {
    // if there are no more rows left to display, hide the load more button
    for (let j = rowNums[i]; j < numRows; j++) {
      rows[i][j].style.display = "";
    }
    loadMoreButtons[i].style.display = "none";
  }
}
