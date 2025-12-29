/**
 * Notification Popup Manager
 * Handles display of homepage notifications with cookie-based tracking
 * for view count and expiration checking
 */

(function() {
    'use strict';

    const NotificationManager = {
        /**
         * Get cookie value by name
         */
        getCookie: function(name) {
            const value = `; ${document.cookie}`;
            const parts = value.split(`; ${name}=`);
            if (parts.length === 2) return parts.pop().split(';').shift();
            return null;
        },

        /**
         * Set cookie with optional expiration
         */
        setCookie: function(name, value, days) {
            let expires = '';
            if (days) {
                const date = new Date();
                date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
                expires = '; expires=' + date.toUTCString();
            }
            document.cookie = name + '=' + (value || '') + expires + '; path=/; SameSite=Lax';
        },

        /**
         * Get current view count for a notification
         */
        getViewCount: function(notificationId) {
            const cookieName = 'notification_views_' + notificationId;
            const count = this.getCookie(cookieName);
            return count ? parseInt(count, 10) : 0;
        },

        /**
         * Increment view count for a notification
         */
        incrementViewCount: function(notificationId) {
            const currentCount = this.getViewCount(notificationId);
            const newCount = currentCount + 1;
            const cookieName = 'notification_views_' + notificationId;
            // Store for 1 year
            this.setCookie(cookieName, newCount, 365);
            return newCount;
        },

        /**
         * Check if notification has expired
         */
        isExpired: function(expirationDate) {
            if (!expirationDate || expirationDate === '') {
                return false;
            }
            const expDate = new Date(expirationDate);
            const today = new Date();
            // Set both to start of day for proper comparison
            today.setHours(0, 0, 0, 0);
            expDate.setHours(0, 0, 0, 0);
            // Notification expires AFTER the expiration date (inclusive of that day)
            return today > expDate;
        },

        /**
         * Check if notification should be shown
         */
        shouldShowNotification: function(notificationId, maxDisplays, expirationDate) {
            // Check expiration
            if (this.isExpired(expirationDate)) {
                return false;
            }

            // Check view count
            if (maxDisplays && maxDisplays > 0) {
                const viewCount = this.getViewCount(notificationId);
                if (viewCount >= maxDisplays) {
                    return false;
                }
            }

            return true;
        },

        /**
         * Show notification modal
         */
        showNotification: function(notificationId, title, message, maxDisplays, expirationDate) {
            if (!this.shouldShowNotification(notificationId, maxDisplays, expirationDate)) {
                return;
            }

            // Increment view count
            this.incrementViewCount(notificationId);

            // Escape HTML to prevent XSS
            const escapeHtml = function(text) {
                const div = document.createElement('div');
                div.textContent = text;
                return div.innerHTML;
            };

            const safeTitle = escapeHtml(title);
            const safeMessage = escapeHtml(message);

            // Create modal HTML with site styling (#275ed8 primary color)
            const modalHtml = `
                <div class="modal fade" id="notification-popup-modal" tabindex="-1" aria-labelledby="notification-popup-label" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content" style="border-radius: 8px; border: none;">
                            <div class="modal-header text-white" style="background-color: #275ed8; border-top-left-radius: 8px; border-top-right-radius: 8px;">
                                <h5 class="modal-title" id="notification-popup-label" style="font-family: 'Montserrat', sans-serif; font-weight: 600;">
                                    <i class="bi bi-megaphone-fill me-2"></i>${safeTitle}
                                </h5>
                                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
                            </div>
                            <div class="modal-body" style="padding: 1.5rem; font-family: 'Montserrat', sans-serif;">
                                <p class="mb-0" style="font-size: 1rem; line-height: 1.6;">${safeMessage}</p>
                            </div>
                            <div class="modal-footer" style="border-top: 1px solid #dee2e6; padding: 1rem 1.5rem;">
                                <button type="button" class="btn btn-cta" data-bs-dismiss="modal" style="background-color: #275ed8 !important; color: #fff !important; border: none; padding: 8px 24px; font-family: 'Montserrat', sans-serif; font-weight: 600; border-radius: 4px; font-size: 14px; letter-spacing: 0.5px;">Got it!</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            // Append modal to body
            const modalElement = document.createElement('div');
            modalElement.innerHTML = modalHtml;
            document.body.appendChild(modalElement.firstElementChild);

            // Show modal after a short delay to ensure page is loaded
            setTimeout(function() {
                const modal = new bootstrap.Modal(document.getElementById('notification-popup-modal'));
                modal.show();

                // Remove modal from DOM after it's hidden
                document.getElementById('notification-popup-modal').addEventListener('hidden.bs.modal', function() {
                    this.remove();
                });
            }, 500);
        },

        /**
         * Initialize notification from page data
         */
        init: function() {
            const notificationData = window.notificationData;
            if (notificationData && notificationData.enabled) {
                this.showNotification(
                    notificationData.id,
                    notificationData.title,
                    notificationData.message,
                    notificationData.maxDisplays,
                    notificationData.expirationDate
                );
            }
        }
    };

    // Auto-initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() {
            NotificationManager.init();
        });
    } else {
        NotificationManager.init();
    }

    // Expose globally for debugging
    window.NotificationManager = NotificationManager;
})();
