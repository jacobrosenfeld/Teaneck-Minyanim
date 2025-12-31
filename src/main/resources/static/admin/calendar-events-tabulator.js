/**
 * Tabulator initialization for Calendar Events (materialization) management table
 * Uses Tabulator 5.5 for advanced data grid functionality
 */

// Wait for DOM to be ready
document.addEventListener('DOMContentLoaded', function() {
    // Get data passed from Thymeleaf
    const data = window.calendarEventsData || {};
    const orgId = data.orgId;
    const events = data.events || [];
    const locations = data.locations || [];
    
    // Build location options for dropdown
    const locationOptions = locations.reduce((acc, loc) => {
        acc[loc.id] = loc.name;
        return acc;
    }, {});
    locationOptions[''] = 'No location';
    
    // Helper function to format date
    function formatDate(dateStr) {
        if (!dateStr) return 'N/A';
        const date = new Date(dateStr);
        return date.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
    }
    
    // Helper function to format time
    function formatTime(timeStr) {
        if (!timeStr) return 'N/A';
        const time = new Date(timeStr);
        return time.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
    }
    
    // Helper function to get minyan type badge class
    function getMinyanTypeBadgeClass(minyanType) {
        if (!minyanType) return 'badge-other';
        
        const name = minyanType.name || minyanType;
        switch(name) {
            case 'SHACHARIS': return 'badge-shacharis';
            case 'MINCHA': return 'badge-mincha';
            case 'MAARIV': return 'badge-maariv';
            case 'MINCHA_MAARIV': return 'badge-mincha-maariv';
            default: return 'badge-other';
        }
    }
    
    // Helper function to get source badge class
    function getSourceBadgeClass(source) {
        if (!source) return 'badge-other';
        
        const name = source.name || source;
        switch(name) {
            case 'RULES': return 'badge-rules';
            case 'IMPORTED': return 'badge-imported';
            case 'MANUAL': return 'badge-manual';
            default: return 'badge-other';
        }
    }
    
    // Helper function to get display name
    function getDisplayName(obj) {
        if (!obj) return '—';
        return obj.displayName || obj.name || obj;
    }
    
    // Initialize Tabulator
    const table = new Tabulator("#calendar-events-table", {
        data: events,
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
                width: 150,
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
                width: 120,
                formatter: function(cell) {
                    const row = cell.getRow().getData();
                    const time = formatTime(cell.getValue());
                    const dynamicTime = row.dynamicTimeString ? 
                        `<br><small style="color: #666; font-size: 0.75rem;">(${row.dynamicTimeString})</small>` : '';
                    return time + dynamicTime;
                }
            },
            {
                title: "Type",
                field: "minyanType",
                sorter: "string",
                width: 130,
                formatter: function(cell) {
                    const minyanType = cell.getValue();
                    const badgeClass = getMinyanTypeBadgeClass(minyanType);
                    const displayName = getDisplayName(minyanType);
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
                        "SELICHOS": "Selichos"
                    }
                },
                headerFilterFunc: function(headerValue, rowValue, rowData) {
                    if (!headerValue) return true;
                    const minyanType = rowValue?.name || rowValue;
                    return minyanType === headerValue;
                }
            },
            {
                title: "Source",
                field: "source",
                sorter: "string",
                width: 130,
                formatter: function(cell) {
                    const row = cell.getRow().getData();
                    const source = cell.getValue();
                    const badgeClass = getSourceBadgeClass(source);
                    const displayName = getDisplayName(source);
                    const manuallyEdited = row.manuallyEdited ? 
                        '<span class="manually-edited-indicator" title="Manually edited"></span>' : '';
                    return `<span class="badge ${badgeClass}">${displayName}</span>${manuallyEdited}`;
                },
                headerFilter: "select",
                headerFilterParams: {
                    values: {
                        "": "All Sources",
                        "RULES": "Rules",
                        "IMPORTED": "Imported",
                        "MANUAL": "Manual"
                    }
                },
                headerFilterFunc: function(headerValue, rowValue, rowData) {
                    if (!headerValue) return true;
                    const source = rowValue?.name || rowValue;
                    return source === headerValue;
                }
            },
            {
                title: "Location",
                field: "locationId",
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
                    const locationId = cell.getValue();
                    const locationName = locationId ? (locationOptions[locationId] || 'Unknown') : 'No location';
                    return `<span style="cursor: pointer;">${locationName}</span>`;
                },
                cellEdited: function(cell) {
                    const row = cell.getRow().getData();
                    const locationId = cell.getValue();
                    updateEventField(row.id, 'locationId', locationId);
                },
                headerFilter: "input",
                headerFilterPlaceholder: "Filter location..."
            },
            {
                title: "Notes",
                field: "notes",
                sorter: "string",
                minWidth: 150,
                editor: "input",
                formatter: function(cell) {
                    const notes = cell.getValue();
                    return notes ? `<span style="font-size: 0.875rem;">${notes}</span>` : '<span class="text-muted">—</span>';
                },
                cellEdited: function(cell) {
                    const row = cell.getRow().getData();
                    const notes = cell.getValue();
                    updateEventField(row.id, 'notes', notes);
                }
            },
            {
                title: "Status",
                field: "enabled",
                sorter: "boolean",
                width: 110,
                formatter: function(cell) {
                    const enabled = cell.getValue();
                    return enabled ? 
                        '<span class="badge badge-enabled">Enabled</span>' : 
                        '<span class="badge badge-disabled">Disabled</span>';
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
                width: 180,
                hozAlign: "center",
                headerSort: false,
                formatter: function(cell) {
                    const row = cell.getRow().getData();
                    const enabled = row.enabled;
                    const source = row.source?.name || row.source;
                    const isManual = source === 'MANUAL';
                    
                    const toggleBtn = `<button class="action-btn btn-toggle toggle-btn" data-id="${row.id}">
                        ${enabled ? 'Disable' : 'Enable'}
                    </button>`;
                    
                    const deleteBtn = isManual ? 
                        `<button class="action-btn btn-delete delete-btn" data-id="${row.id}">Delete</button>` : '';
                    
                    return toggleBtn + deleteBtn;
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
            const eventId = button.getAttribute('data-id');
            toggleEvent(eventId);
        } else if (e.target.classList.contains('delete-btn') || e.target.closest('.delete-btn')) {
            const button = e.target.closest('.delete-btn') || e.target;
            const eventId = button.getAttribute('data-id');
            if (confirm('Delete this manual event?')) {
                deleteEvent(eventId);
            }
        }
    });
    
    // Function to toggle event status
    function toggleEvent(eventId) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = `/admin/${orgId}/calendar-events/${eventId}/toggle`;
        document.body.appendChild(form);
        form.submit();
    }
    
    // Function to delete event
    function deleteEvent(eventId) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = `/admin/${orgId}/calendar-events/${eventId}/delete`;
        document.body.appendChild(form);
        form.submit();
    }
    
    // Function to update event field
    function updateEventField(eventId, fieldName, fieldValue) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = `/admin/${orgId}/calendar-events/${eventId}/update`;
        
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = fieldName;
        input.value = fieldValue || '';
        
        form.appendChild(input);
        document.body.appendChild(form);
        form.submit();
    }
    
    // Update entry count display
    function updateEventCount() {
        const count = table.getDataCount("active");
        const countElement = document.getElementById('events-count');
        if (countElement) {
            countElement.textContent = `(${count} events)`;
        }
    }
    
    // Update count on filter
    table.on("dataFiltered", updateEventCount);
    
    // Initial count update
    updateEventCount();
    
    // Make table globally accessible for debugging
    window.calendarEventsTable = table;
});
