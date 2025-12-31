/**
 * AG Grid initialization for Calendar Events (materialization) management table
 * Uses AG Grid Community 31.3.2 for advanced data grid functionality with high performance
 * Optimized for handling 10,000+ rows with virtual scrolling and efficient rendering
 */

// Wait for DOM and AG Grid to be ready
document.addEventListener('DOMContentLoaded', function() {
    // Get data passed from Thymeleaf
    const data = window.calendarEventsData || {};
    const orgId = data.orgId;
    const events = data.events || [];
    const locations = data.locations || [];
    
    console.log('Initializing AG Grid with', events.length, 'events');
    
    // Build location options for dropdown
    const locationOptions = locations.reduce((acc, loc) => {
        acc[loc.id] = loc.name;
        return acc;
    }, {});
    locationOptions[''] = 'No location';
    
    // Helper function to format date
    function formatDate(params) {
        if (!params.value) return 'N/A';
        const date = new Date(params.value);
        return date.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
    }
    
    // Helper function to format time
    function formatTime(params) {
        if (!params.value) return 'N/A';
        const time = new Date(params.value);
        const formatted = time.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
        
        // Add dynamic time string if present
        const dynamicTime = params.data.dynamicTimeString;
        if (dynamicTime) {
            return `${formatted}<br><small style="color: #666; font-size: 0.75rem;">(${dynamicTime})</small>`;
        }
        return formatted;
    }
    
    // Badge renderer for minyan type
    function minyanTypeBadgeRenderer(params) {
        if (!params.value) return '—';
        
        const minyanType = params.value;
        const name = minyanType.name || minyanType;
        const displayName = minyanType.displayName || name;
        
        let badgeClass = 'badge-event ';
        switch(name) {
            case 'SHACHARIS': badgeClass += 'badge-shacharis'; break;
            case 'MINCHA': badgeClass += 'badge-mincha'; break;
            case 'MAARIV': badgeClass += 'badge-maariv'; break;
            case 'MINCHA_MAARIV': badgeClass += 'badge-mincha-maariv'; break;
            default: badgeClass += 'badge-mincha-maariv';
        }
        
        return `<span class="${badgeClass}">${displayName}</span>`;
    }
    
    // Badge renderer for source
    function sourceBadgeRenderer(params) {
        if (!params.value) return '—';
        
        const source = params.value;
        const name = source.name || source;
        const displayName = source.displayName || name;
        
        let badgeClass = 'badge-event ';
        switch(name) {
            case 'RULES': badgeClass += 'badge-rules'; break;
            case 'IMPORTED': badgeClass += 'badge-imported'; break;
            case 'MANUAL': badgeClass += 'badge-manual'; break;
            default: badgeClass += 'badge-rules';
        }
        
        const manuallyEdited = params.data.manuallyEdited ? 
            '<span class="manually-edited-indicator" title="Manually edited"></span>' : '';
        
        return `<span class="${badgeClass}">${displayName}</span>${manuallyEdited}`;
    }
    
    // Badge renderer for enabled status
    function statusBadgeRenderer(params) {
        const enabled = params.value;
        const badgeClass = enabled ? 'badge-event badge-enabled' : 'badge-event badge-disabled';
        const text = enabled ? 'Enabled' : 'Disabled';
        return `<span class="${badgeClass}">${text}</span>`;
    }
    
    // Location editor and renderer
    function locationRenderer(params) {
        const locationId = params.value;
        const locationName = locationId ? (locationOptions[locationId] || 'Unknown') : 'No location';
        return locationName;
    }
    
    // Notes renderer
    function notesRenderer(params) {
        const notes = params.value;
        return notes ? `<span style="font-size: 0.875rem;">${notes}</span>` : '<span style="color: #999;">—</span>';
    }
    
    // Actions cell renderer
    function actionsCellRenderer(params) {
        const row = params.data;
        const enabled = row.enabled;
        const source = row.source?.name || row.source;
        const isManual = source === 'MANUAL';
        
        const toggleBtn = `<button class="action-btn btn-toggle" data-id="${row.id}" data-action="toggle">
            ${enabled ? 'Disable' : 'Enable'}
        </button>`;
        
        const deleteBtn = isManual ? 
            `<button class="action-btn btn-delete" data-id="${row.id}" data-action="delete">Delete</button>` : '';
        
        return toggleBtn + deleteBtn;
    }
    
    // Column definitions for AG Grid
    const columnDefs = [
        {
            headerName: 'Date',
            field: 'date',
            sortable: true,
            filter: 'agDateColumnFilter',
            width: 180,
            valueFormatter: formatDate,
            sort: 'asc'
        },
        {
            headerName: 'Time',
            field: 'startTime',
            sortable: true,
            filter: false,
            width: 130,
            cellRenderer: formatTime
        },
        {
            headerName: 'Type',
            field: 'minyanType',
            sortable: true,
            filter: 'agSetColumnFilter',
            width: 150,
            cellRenderer: minyanTypeBadgeRenderer,
            filterParams: {
                values: ['SHACHARIS', 'MINCHA', 'MAARIV', 'MINCHA_MAARIV', 'SELICHOS'],
                valueFormatter: params => {
                    const displayNames = {
                        'SHACHARIS': 'Shacharis',
                        'MINCHA': 'Mincha',
                        'MAARIV': 'Maariv',
                        'MINCHA_MAARIV': 'Mincha/Maariv',
                        'SELICHOS': 'Selichos'
                    };
                    return displayNames[params.value] || params.value;
                }
            }
        },
        {
            headerName: 'Source',
            field: 'source',
            sortable: true,
            filter: 'agSetColumnFilter',
            width: 150,
            cellRenderer: sourceBadgeRenderer,
            filterParams: {
                values: ['RULES', 'IMPORTED', 'MANUAL'],
                valueFormatter: params => {
                    const displayNames = {
                        'RULES': 'Rules',
                        'IMPORTED': 'Imported',
                        'MANUAL': 'Manual'
                    };
                    return displayNames[params.value] || params.value;
                }
            }
        },
        {
            headerName: 'Location',
            field: 'locationId',
            sortable: true,
            filter: 'agTextColumnFilter',
            width: 200,
            editable: true,
            cellEditor: 'agSelectCellEditor',
            cellEditorParams: {
                values: Object.keys(locationOptions)
            },
            cellRenderer: locationRenderer,
            onCellValueChanged: onLocationChanged
        },
        {
            headerName: 'Notes',
            field: 'notes',
            sortable: true,
            filter: 'agTextColumnFilter',
            minWidth: 200,
            flex: 1,
            editable: true,
            cellRenderer: notesRenderer,
            onCellValueChanged: onNotesChanged
        },
        {
            headerName: 'Status',
            field: 'enabled',
            sortable: true,
            filter: 'agSetColumnFilter',
            width: 120,
            cellRenderer: statusBadgeRenderer,
            filterParams: {
                values: [true, false],
                valueFormatter: params => params.value ? 'Enabled' : 'Disabled'
            }
        },
        {
            headerName: 'Actions',
            field: 'actions',
            width: 200,
            cellRenderer: actionsCellRenderer,
            sortable: false,
            filter: false,
            pinned: 'right'
        }
    ];
    
    // Grid options
    const gridOptions = {
        columnDefs: columnDefs,
        rowData: events,
        defaultColDef: {
            resizable: true,
            sortable: true,
            filter: true,
            floatingFilter: false,
            wrapText: false,
            autoHeight: false
        },
        pagination: true,
        paginationPageSize: 50,
        paginationPageSizeSelector: [25, 50, 100, 200],
        domLayout: 'normal',
        rowHeight: 50,
        animateRows: true,
        enableCellTextSelection: true,
        suppressRowClickSelection: true,
        getRowClass: params => {
            return params.data.enabled ? '' : 'disabled-row';
        },
        onCellClicked: onCellClicked,
        onGridReady: function(params) {
            console.log('AG Grid ready with', params.api.getDisplayedRowCount(), 'rows');
            // Auto size columns to fit
            params.api.sizeColumnsToFit();
        }
    };
    
    // Initialize grid
    const gridDiv = document.querySelector('#calendarEventsGrid');
    const gridApi = agGrid.createGrid(gridDiv, gridOptions);
    
    // Cell click handler for action buttons
    function onCellClicked(event) {
        const target = event.event.target;
        if (target.classList.contains('action-btn') || target.closest('.action-btn')) {
            const button = target.classList.contains('action-btn') ? target : target.closest('.action-btn');
            const action = button.getAttribute('data-action');
            const eventId = button.getAttribute('data-id');
            
            if (action === 'toggle') {
                toggleEvent(eventId);
            } else if (action === 'delete') {
                if (confirm('Delete this manual event?')) {
                    deleteEvent(eventId);
                }
            }
        }
    }
    
    // Handle location change
    function onLocationChanged(event) {
        if (event.oldValue === event.newValue) return;
        
        const eventId = event.data.id;
        const locationId = event.newValue;
        updateEventField(eventId, 'locationId', locationId);
    }
    
    // Handle notes change
    function onNotesChanged(event) {
        if (event.oldValue === event.newValue) return;
        
        const eventId = event.data.id;
        const notes = event.newValue;
        updateEventField(eventId, 'notes', notes);
    }
    
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
    
    // Make grid API globally accessible for debugging
    window.calendarEventsGridApi = gridApi;
    
    console.log('AG Grid initialization complete');
});
