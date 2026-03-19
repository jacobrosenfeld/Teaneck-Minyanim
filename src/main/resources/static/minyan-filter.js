(function () {
  var filterBar = document.getElementById('minyan-filter-bar');
  if (!filterBar) return;

  var isToday = filterBar.getAttribute('data-is-today') === 'true';
  var tables = document.querySelectorAll('.minyan-data-table');
  var emptyState = document.getElementById('filter-empty-state');
  var emptyMsg = document.getElementById('filter-empty-message');
  var loadMoreBtn = document.getElementById('load-more-button');

  var activeType = 'all';
  var activeOrg = 'all';

  // Populate shul dropdown from row data
  var shulSelect = document.getElementById('shul-filter-select');
  if (shulSelect && tables.length > 0) {
    var orgNames = [];
    var seen = {};
    tables.forEach(function (table) {
      Array.from(table.querySelectorAll('tbody tr')).forEach(function (row) {
        var org = row.getAttribute('data-org');
        if (org && !seen[org]) {
          seen[org] = true;
          orgNames.push(org);
        }
      });
    });
    orgNames.sort().forEach(function (name) {
      var opt = document.createElement('option');
      opt.value = name;
      opt.textContent = name;
      shulSelect.appendChild(opt);
    });
  }

  function typeMatchesFilter(rowType, filter) {
    if (filter === 'all') return true;
    rowType = (rowType || '').toUpperCase();
    if (filter === 'shacharis') return rowType === 'SHACHARIS';
    if (filter === 'mincha') return rowType === 'MINCHA' || rowType === 'MINCHA_MAARIV';
    if (filter === 'maariv') return rowType === 'MAARIV';
    return true;
  }

  function buildEmptyMessage() {
    var typeLabels = { shacharis: 'Shacharis', mincha: 'Mincha', maariv: 'Maariv' };
    var typeLabel = typeLabels[activeType] || null;
    var orgLabel = activeOrg !== 'all' ? activeOrg : null;

    if (isToday) {
      if (typeLabel && orgLabel) return 'No more ' + typeLabel + ' minyanim at ' + orgLabel + ' today.';
      if (typeLabel) return 'No more ' + typeLabel + ' minyanim today.';
      if (orgLabel) return 'There are no more minyanim at ' + orgLabel + ' today.';
      return "Sorry, there aren't any upcoming minyanim today.";
    } else {
      if (typeLabel && orgLabel) return 'No ' + typeLabel + ' minyanim at ' + orgLabel + ' on this date.';
      if (typeLabel) return 'No ' + typeLabel + ' minyanim on this date.';
      if (orgLabel) return 'There are no minyanim at ' + orgLabel + ' on this date.';
      return "Sorry, there aren't any minyanim scheduled for this date.";
    }
  }

  function applyFilters() {
    var filterActive = activeType !== 'all' || activeOrg !== 'all';
    var visibleCount = 0;

    tables.forEach(function (table) {
      Array.from(table.querySelectorAll('tbody tr')).forEach(function (row) {
        var rowType = row.getAttribute('data-type') || '';
        var rowOrg = row.getAttribute('data-org') || '';
        var matches =
          typeMatchesFilter(rowType, activeType) &&
          (activeOrg === 'all' || rowOrg === activeOrg);
        row.style.display = matches ? '' : 'none';
        if (matches) visibleCount++;
      });
    });

    // Pagination coordination
    if (filterActive) {
      if (loadMoreBtn) loadMoreBtn.style.display = 'none';
    } else {
      if (typeof resetMinyanTable === 'function') resetMinyanTable();
    }

    // Empty state
    if (visibleCount === 0 && emptyState && emptyMsg) {
      emptyState.style.display = '';
      emptyMsg.textContent = buildEmptyMessage();
    } else if (emptyState) {
      emptyState.style.display = 'none';
    }
  }

  // Type pill click handlers
  document.querySelectorAll('.filter-pill').forEach(function (btn) {
    btn.addEventListener('click', function () {
      document.querySelectorAll('.filter-pill').forEach(function (b) {
        b.classList.remove('active');
      });
      this.classList.add('active');
      activeType = this.getAttribute('data-filter-type');
      applyFilters();
    });
  });

  // Shul select handler
  if (shulSelect) {
    shulSelect.addEventListener('change', function () {
      activeOrg = this.value;
      applyFilters();
    });
  }
})();
