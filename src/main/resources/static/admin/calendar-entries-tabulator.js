/**
 * Tabulator initialization for Calendar Entries management table
 * Uses Tabulator 5.5 for advanced data grid functionality
 */

// Wait for DOM to be ready
document.addEventListener('DOMContentLoaded', function() {
    // Get data passed from Thymeleaf
    const data = window.calendarEntriesData || {};
    const orgId = data.orgId;
    const entries = data.entries || [];
    const locations = data.locations || [];
    
    // Build location options for dropdown
    const locationOptions = locations.reduce((acc, loc) => {
        acc[loc.id] = loc.name;
        return acc;
    }, {});
    locationOptions[''] = '-- No location --';
    
    // Helper function to get prayer pill HTML
    function getPrayerPillHtml(title) {
        const titleLower = (title || '').toLowerCase();
        let pillClass = '';
        let pillText = '';
        
        // Check for combined Mincha/Maariv first (most specific)
        if ((titleLower.includes('mincha') || titleLower.includes('minchah')) && 
            (titleLower.includes('maariv') || titleLower.includes("ma'ariv") || titleLower.includes('arvit'))) {
            pillClass = 'prayer-pill prayer-pill-mincha-maariv';
            pillText = 'Mincha/Maariv';
        }
        // Check for individual prayer types
        else if (titleLower.includes('shacharis') || titleLower.includes('shacharit') || titleLower.includes('shaharit')) {
            pillClass = 'prayer-pill prayer-pill-shacharis';
            pillText = 'Shacharis';
        }
        else if (titleLower.includes('mincha') || titleLower.includes('minchah') || titleLower.includes('minha')) {
            pillClass = 'prayer-pill prayer-pill-mincha';
            pillText = 'Mincha';
        }
        else if (titleLower.includes('maariv') || titleLower.includes("ma'ariv") || titleLower.includes('arvit')) {
            pillClass = 'prayer-pill prayer-pill-maariv';
            pillText = 'Maariv';
        }
        
        return pillClass ? `<span class="${pillClass}">${pillText}</span> ` : '';
    }
    
    // Helper function to format date
    function formatDate(dateStr) {
        if (!dateStr) return 'N/A';
        const date = new Date(dateStr);
        return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    }
    
    // Helper function to format time
    function formatTime(timeStr) {
        if (!timeStr) return 'N/A';
        const time = new Date(timeStr);
        return time.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
    }
    
    // Helper function to get badge class
    function getClassificationBadgeClass(classification) {
        if (!classification) return 'badge-other';
        
        const name = classification.name || classification;
        switch(name) {
            case 'SHACHARIS': return 'badge-shacharis';
            case 'MINCHA': return 'badge-mincha';
            case 'MAARIV': return 'badge-maariv';
            case 'SELICHOS': return 'badge-selichos';
            case 'MINCHA_MAARIV': return 'badge-mincha-maariv';
            case 'NON_MINYAN': return 'badge-non-minyan';
            default: return 'badge-other';
        }
    }
    
    // Helper function to get display name
    function getClassificationDisplayName(classification) {
        if (!classification) return '—';
        return classification.displayName || classification.name || classification;
    }
    
    // Initialize Tabulator
    const table = new Tabulator("#calendar-entries-table", {
        data: entries,
        layout: "fitDataStretch",
        responsiveLayout: "collapse",
        pagination: true,
        paginationSize: 30,
        paginationSizeSelector: [10, 20, 30, 50, 100],
        movableColumns: true,
        resizableColumns: true,
        initialSort: [
            {column: "date", dir: "asc"},
            {column: "startTime", dir: "asc"}
        ],
        columns: [
            {
                title: "Date",
                field: "date",
                sorter: "date",
                width: 130,
                formatter: function(cell) {
                    return formatDate(cell.getValue());
                },
                headerFilter: "input",
                headerFilterPlaceholder: "Filter date..."
            },
            {
                title: "Time",
                field: "startTime",
                sorter: "datetime",
                width: 100,
                formatter: function(cell) {
                    return formatTime(cell.getValue());
                }
            },
            {
                title: "Title",
                field: "title",
                sorter: "string",
                minWidth: 200,
                formatter: function(cell) {
                    const row = cell.getRow().getData();
                    const pillHtml = getPrayerPillHtml(row.title);
                    const classificationReason = row.classificationReason ? 
                        `<br><small class="text-muted">${row.classificationReason}</small>` : '';
                    return `${pillHtml}<strong>${cell.getValue()}</strong>${classificationReason}`;
                },
                headerFilter: "input",
                headerFilterPlaceholder: "Search title..."
            },
            {
                title: "Type",
                field: "classification",
                sorter: "string",
                width: 150,
                formatter: function(cell) {
                    const classification = cell.getValue();
                    const badgeClass = getClassificationBadgeClass(classification);
                    const displayName = getClassificationDisplayName(classification);
                    return `<span class="badge ${badgeClass}">${displayName}</span>`;
                },
                headerFilter: "select",
                headerFilterParams: {
                    values: {
                        "": "All Types",
                        "SHACHARIS": "Shacharis",
                        "MINCHA": "Mincha",
                        "MAARIV": "Maariv",
                        "MINCHA_MAARIV": "Mincha/Maariv",
                        "SELICHOS": "Selichos",
                        "NON_MINYAN": "Non-Minyan",
                        "OTHER": "Other"
                    }
                },
                headerFilterFunc: function(headerValue, rowValue, rowData) {
                    if (!headerValue) return true;
                    const classification = rowValue?.name || rowValue;
                    return classification === headerValue;
                }
            },
            {
                title: "Location",
                field: "location",
                sorter: "string",
                width: 180,
                editor: "list",
                editorParams: {
                    values: locationOptions,
                    clearable: true,
                    autocomplete: true
                },
                formatter: function(cell) {
                    const row = cell.getRow().getData();
                    const location = cell.getValue() || 'Add location';
                    const manuallyEdited = row.locationManuallyEdited ? 
                        '<span class="manually-edited-indicator" title="Manually edited"></span>' : '';
                    return `<span style="cursor: pointer;">${location}${manuallyEdited}</span>`;
                },
                cellEdited: function(cell) {
                    const row = cell.getRow().getData();
                    const locationId = cell.getValue();
                    updateLocation(row.id, locationId);
                },
                headerFilter: "input",
                headerFilterPlaceholder: "Filter location..."
            },
            {
                title: "Notes",
                field: "notes",
                sorter: "string",
                minWidth: 150,
                formatter: function(cell) {
                    const notes = cell.getValue();
                    return notes ? `<span class="notes-cell">${notes}</span>` : '<span class="text-muted">—</span>';
                }
            },
            {
                title: "Status",
                field: "enabled",
                sorter: "boolean",
                width: 110,
                formatter: function(cell) {
                    const enabled = cell.getValue();
                    const row = cell.getRow().getData();
                    const badge = enabled ? 
                        '<span class="badge badge-success">Enabled</span>' : 
                        '<span class="badge badge-secondary">Disabled</span>';
                    const duplicateReason = row.duplicateReason ? 
                        `<br><small class="text-muted">${row.duplicateReason}</small>` : '';
                    return badge + duplicateReason;
                },
                headerFilter: "select",
                headerFilterParams: {
                    values: {
                        "": "All",
                        "true": "Enabled",
                        "false": "Disabled"
                    }
                },
                headerFilterFunc: function(headerValue, rowValue) {
                    if (!headerValue) return true;
                    return String(rowValue) === headerValue;
                }
            },
            {
                title: "Actions",
                field: "actions",
                width: 120,
                hozAlign: "center",
                headerSort: false,
                formatter: function(cell) {
                    const row = cell.getRow().getData();
                    const enabled = row.enabled;
                    const btnClass = enabled ? 'btn-outline-secondary' : 'btn-outline-success';
                    const icon = enabled ? 'fa-eye-slash' : 'fa-eye';
                    const text = enabled ? 'Disable' : 'Enable';
                    
                    return `<button class="btn btn-sm ${btnClass} toggle-btn" data-id="${row.id}">
                        <i class="fas ${icon}"></i> ${text}
                    </button>`;
                }
            }
        ],
        rowFormatter: function(row) {
            const data = row.getData();
            if (!data.enabled) {
                row.getElement().classList.add('disabled-row');
            }
        }
    });
    
    // Handle toggle button clicks
    table.on("cellClick", function(e, cell) {
        if (e.target.classList.contains('toggle-btn') || e.target.closest('.toggle-btn')) {
            const button = e.target.closest('.toggle-btn') || e.target;
            const entryId = button.getAttribute('data-id');
            toggleEntry(entryId);
        }
    });
    
    // Function to toggle entry status
    function toggleEntry(entryId) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = `/admin/${orgId}/calendar-entries/${entryId}/toggle`;
        document.body.appendChild(form);
        form.submit();
    }
    
    // Function to update location
    function updateLocation(entryId, locationId) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = `/admin/${orgId}/calendar-entries/${entryId}/update-location`;
        
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = 'locationId';
        input.value = locationId || '';
        
        form.appendChild(input);
        document.body.appendChild(form);
        form.submit();
    }
    
    // Update entry count display
    function updateEntryCount() {
        const count = table.getDataCount("active");
        const countElement = document.getElementById('entry-count');
        if (countElement) {
            countElement.textContent = `(${count} entries)`;
        }
    }
    
    // Update count on filter
    table.on("dataFiltered", updateEntryCount);
    
    // Initial count update
    updateEntryCount();
    
    // Make table globally accessible for debugging
    window.calendarEntriesTable = table;
});
