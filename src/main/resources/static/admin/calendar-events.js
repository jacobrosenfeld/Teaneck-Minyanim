/**
 * Calendar Events Admin Page - JavaScript
 * Handles calendar view toggle, month navigation, and event interactions
 */

(function() {
    'use strict';
    
    // ========================================
    // View Toggle Functionality
    // ========================================
    
    function initViewToggle() {
        const toggleBtn = document.getElementById('viewToggleBtn');
        const agendaView = document.querySelector('.agenda-view');
        const calendarView = document.querySelector('.calendar-view');
        
        if (!toggleBtn || !agendaView || !calendarView) {
            return;
        }
        
        toggleBtn.addEventListener('click', function() {
            const currentView = this.dataset.view;
            
            if (currentView === 'agenda') {
                // Switch to calendar view
                agendaView.classList.add('hidden');
                calendarView.classList.add('active');
                this.dataset.view = 'calendar';
                this.innerHTML = '<i class="fas fa-list"></i> Agenda View';
                
                // Save preference
                localStorage.setItem('adminCalendarView', 'calendar');
            } else {
                // Switch to agenda view
                calendarView.classList.remove('active');
                agendaView.classList.remove('hidden');
                this.dataset.view = 'agenda';
                this.innerHTML = '<i class="fas fa-calendar-alt"></i> Calendar View';
                
                // Save preference
                localStorage.setItem('adminCalendarView', 'agenda');
            }
        });
        
        // Restore user's view preference
        const savedView = localStorage.getItem('adminCalendarView');
        if (savedView === 'calendar') {
            toggleBtn.click();
        }
    }
    
    // ========================================
    // Calendar Navigation
    // ========================================
    
    function initCalendarNavigation() {
        const prevMonthBtn = document.querySelector('.prev-month-btn');
        const nextMonthBtn = document.querySelector('.next-month-btn');
        const monthYearDisplay = document.querySelector('.month-year');
        
        if (!prevMonthBtn || !nextMonthBtn || !monthYearDisplay) {
            return;
        }
        
        let currentDate = new Date();
        
        prevMonthBtn.addEventListener('click', function() {
            currentDate.setMonth(currentDate.getMonth() - 1);
            loadCalendarForMonth(currentDate);
        });
        
        nextMonthBtn.addEventListener('click', function() {
            currentDate.setMonth(currentDate.getMonth() + 1);
            loadCalendarForMonth(currentDate);
        });
    }
    
    function loadCalendarForMonth(date) {
        const orgId = getOrgIdFromUrl();
        const year = date.getFullYear();
        const month = date.getMonth() + 1; // JavaScript months are 0-indexed
        
        // Construct URL with filters
        const url = new URL(window.location.href);
        url.searchParams.set('calendarMonth', month);
        url.searchParams.set('calendarYear', year);
        url.searchParams.set('view', 'calendar');
        
        // Reload page with new month
        window.location.href = url.toString();
    }
    
    // ========================================
    // Event Detail Modal
    // ========================================
    
    function initEventDetails() {
        const eventPills = document.querySelectorAll('.event-pill');
        const eventCards = document.querySelectorAll('.event-card');
        
        // Handle event pill clicks (calendar view)
        eventPills.forEach(function(pill) {
            pill.addEventListener('click', function(e) {
                if (!e.target.closest('button, form')) {
                    const eventId = this.dataset.eventId;
                    if (eventId) {
                        showEventDetail(eventId);
                    }
                }
            });
        });
        
        // Handle event card clicks (agenda view) - but not actions
        eventCards.forEach(function(card) {
            card.addEventListener('click', function(e) {
                if (!e.target.closest('.event-actions, button, form, a')) {
                    const eventId = this.dataset.eventId;
                    if (eventId) {
                        showEventDetail(eventId);
                    }
                }
            });
        });
    }
    
    function showEventDetail(eventId) {
        // For now, just highlight the event
        // In future, could show a modal with full details
        console.log('Show detail for event:', eventId);
        
        // TODO: Implement modal with event details
        // Could fetch via AJAX or populate from data attributes
    }
    
    // ========================================
    // Filter Management
    // ========================================
    
    function initFilters() {
        const clearFiltersBtn = document.getElementById('clearFiltersBtn');
        const filterForm = document.getElementById('filterForm');
        
        if (clearFiltersBtn && filterForm) {
            clearFiltersBtn.addEventListener('click', function() {
                filterForm.reset();
                // Remove all query parameters except orgId
                const url = new URL(window.location.href);
                const orgId = getOrgIdFromUrl();
                window.location.href = window.location.pathname;
            });
        }
        
        // Handle filter collapse icon rotation
        const filterHeader = document.querySelector('.filter-panel .card-header');
        if (filterHeader) {
            filterHeader.addEventListener('click', function() {
                const icon = this.querySelector('.filter-collapse-icon');
                if (icon) {
                    icon.classList.toggle('collapsed');
                }
            });
        }
    }
    
    // ========================================
    // Form Submissions with Loading State
    // ========================================
    
    function initFormSubmissions() {
        const toggleForms = document.querySelectorAll('form[action*="/toggle"]');
        
        toggleForms.forEach(function(form) {
            form.addEventListener('submit', function(e) {
                const button = this.querySelector('button[type="submit"]');
                if (button) {
                    button.disabled = true;
                    const originalHTML = button.innerHTML;
                    button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Processing...';
                    
                    // Re-enable after a delay if something goes wrong
                    setTimeout(function() {
                        button.disabled = false;
                        button.innerHTML = originalHTML;
                    }, 5000);
                }
            });
        });
    }
    
    // ========================================
    // Alert Auto-Dismiss
    // ========================================
    
    function initAlerts() {
        const alerts = document.querySelectorAll('.alert:not(.alert-permanent)');
        
        alerts.forEach(function(alert) {
            setTimeout(function() {
                $(alert).fadeOut(500, function() {
                    $(this).remove();
                });
            }, 5000); // Auto-dismiss after 5 seconds
        });
    }
    
    // ========================================
    // Tooltips
    // ========================================
    
    function initTooltips() {
        $('[data-toggle="tooltip"]').tooltip();
    }
    
    // ========================================
    // Utility Functions
    // ========================================
    
    function getOrgIdFromUrl() {
        const pathParts = window.location.pathname.split('/');
        const adminIndex = pathParts.indexOf('admin');
        if (adminIndex !== -1 && pathParts.length > adminIndex + 1) {
            return pathParts[adminIndex + 1];
        }
        return null;
    }
    
    // ========================================
    // Initialize on DOM Ready
    // ========================================
    
    document.addEventListener('DOMContentLoaded', function() {
        initViewToggle();
        initCalendarNavigation();
        initEventDetails();
        initFilters();
        initFormSubmissions();
        initAlerts();
        initTooltips();
        
        console.log('Calendar Events Admin Page initialized');
    });
    
    // ========================================
    // Export for potential external use
    // ========================================
    
    window.CalendarEventsAdmin = {
        loadCalendarForMonth: loadCalendarForMonth,
        showEventDetail: showEventDetail
    };
    
})();
