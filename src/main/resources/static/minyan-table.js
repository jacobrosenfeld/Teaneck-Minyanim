var _minyanTable = document.getElementById("minyan-table");
var _minyanTableRowNum = 10;
var _minyanTableIncrement = 5;
var _minyanTableLoadMoreBtn = document.getElementById("load-more-button");

function _getBodyRows() {
  if (!_minyanTable) return [];
  return Array.from(_minyanTable.querySelectorAll('tbody tr'));
}

function resetMinyanTable() {
  if (!_minyanTable) return;
  var rows = _getBodyRows();
  _minyanTableRowNum = 10;
  for (var i = 0; i < rows.length; i++) {
    rows[i].style.display = i < _minyanTableRowNum ? "" : "none";
  }
  if (!_minyanTableLoadMoreBtn) return;
  if (rows.length <= _minyanTableRowNum) {
    _minyanTableLoadMoreBtn.style.display = "none";
  } else {
    _minyanTableLoadMoreBtn.style.display = "";
  }
}

function loadMore() {
  if (!_minyanTable) return;
  var rows = _getBodyRows();
  var numRows = rows.length;
  if (_minyanTableRowNum + _minyanTableIncrement < numRows) {
    for (var i = _minyanTableRowNum; i < _minyanTableRowNum + _minyanTableIncrement; i++) {
      rows[i].style.display = "";
    }
    _minyanTableRowNum += _minyanTableIncrement;
  } else {
    for (var i = _minyanTableRowNum; i < numRows; i++) {
      rows[i].style.display = "";
    }
    if (_minyanTableLoadMoreBtn) _minyanTableLoadMoreBtn.style.display = "none";
  }
}

// Initial setup
resetMinyanTable();
if (_minyanTableLoadMoreBtn) {
  _minyanTableLoadMoreBtn.addEventListener("click", loadMore);
}
