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
      Array.from(table.querySelectorAll('tbody tr[data-type]')).forEach(function (row) {
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
    if (filter === 'selichos_shacharis') return rowType === 'SELICHOS_SHACHARIS';
    if (filter === 'mincha') return rowType === 'MINCHA' || rowType === 'MINCHA_MAARIV';
    if (filter === 'maariv') return rowType === 'MAARIV';
    if (filter === 'night_selichos') return rowType === 'NIGHT_SELICHOS';
    if (filter === 'selichos') return rowType === 'SELICHOS';
    return true;
  }

  function buildEmptyMessage() {
    var typeLabels = {
      shacharis: 'Shacharis',
      selichos_shacharis: 'Selichos & Shacharis',
      mincha: 'Mincha',
      maariv: 'Maariv',
      night_selichos: 'Night Selichos',
      selichos: 'Selichos',
    };
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

  /**
   * Analytics: push a GA4-friendly event to the GTM dataLayer. GTM routes these to GA4 and
   * PostHog. Must never throw, even when GTM is turned off or blocked.
   */
  function pushAnalyticsEvent(eventName, payload) {
    try {
      window.dataLayer = window.dataLayer || [];
      var event = { event: eventName };
      for (var key in payload) {
        if (Object.prototype.hasOwnProperty.call(payload, key)) event[key] = payload[key];
      }
      window.dataLayer.push(event);
    } catch (e) {
      // Ignore analytics failures; filtering must keep working.
    }
  }

  function trackFilterChange(eventName, source, value, visibleCount) {
    pushAnalyticsEvent(eventName, {
      page: 'homepage',
      interaction_source: source,
      filter_value: value,
      filter_type: activeType,
      filter_org: activeOrg,
      filter_active: activeType !== 'all' || activeOrg !== 'all',
      visible_count: visibleCount,
      is_today: isToday,
    });
  }

  function applyFilters() {
    var filterActive = activeType !== 'all' || activeOrg !== 'all';
    var visibleCount = 0;

    tables.forEach(function (table) {
      Array.from(table.querySelectorAll('tbody tr[data-type]')).forEach(function (row) {
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

    return visibleCount;
  }

  // Type pill click handlers
  document.querySelectorAll('.filter-pill').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var newType = this.getAttribute('data-filter-type');
      var changed = newType !== activeType;
      document.querySelectorAll('.filter-pill').forEach(function (b) {
        b.classList.remove('active');
      });
      this.classList.add('active');
      activeType = newType;
      var visibleCount = applyFilters();
      if (changed) {
        trackFilterChange('homepage_filter_type_selected', 'type_pill', activeType, visibleCount);
      }
    });
  });

  // Shul select handler
  if (shulSelect) {
    shulSelect.addEventListener('change', function () {
      var changed = this.value !== activeOrg;
      activeOrg = this.value;
      var visibleCount = applyFilters();
      if (changed) {
        trackFilterChange('homepage_filter_shul_selected', 'shul_select', activeOrg, visibleCount);
      }
    });
  }
})();
