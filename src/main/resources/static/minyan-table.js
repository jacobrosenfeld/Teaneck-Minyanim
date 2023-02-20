let table = document.getElementById("minyan-table");
let rows = table.rows;
let rowNum = 10; // the number of rows to display initially
let increment = 5; // the number of rows to add each time the load more button is clicked

function loadMore() {
  let numRows = rows.length;

  // add 5 more rows if there are still rows left to display
  if (rowNum + increment < numRows) {
    for (let i = rowNum; i < rowNum + increment; i++) {
      rows[i].style.display = "";
    }
    rowNum += increment;
  } else {
    // if there are no more rows left to display, hide the load more button
    for (let i = rowNum; i < numRows; i++) {
      rows[i].style.display = "";
    }
    document.getElementById("load-more-button").style.display = "none";
  }
}

// hide all rows except for the first 10
for (let i = rowNum; i < rows.length; i++) {
  rows[i].style.display = "none";
}

// add event listener to the load more button
document.getElementById("load-more-button").addEventListener("click", loadMore);
