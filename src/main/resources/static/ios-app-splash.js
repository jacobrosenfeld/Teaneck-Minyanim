(function () {
  var splash = document.getElementById('ios-app-splash');
  if (!splash || window.location.pathname.indexOf('/admin') === 0) {
    return;
  }

  var storageKey = 'teaneck-minyanim-ios-app-splash-dismissed';
  var currentVersion = splash.getAttribute('data-version') || 'ios-app-launch';

  try {
    if (window.localStorage.getItem(storageKey) === currentVersion) {
      return;
    }
  } catch (error) {
    // Browsers can block localStorage in private or restricted contexts.
  }

  function dismissSplash() {
    splash.setAttribute('hidden', '');
    splash.setAttribute('aria-hidden', 'true');

    try {
      window.localStorage.setItem(storageKey, currentVersion);
    } catch (error) {
      // Dismiss for the current page even when storage is unavailable.
    }
  }

  function showSplash() {
    splash.removeAttribute('hidden');
    splash.setAttribute('aria-hidden', 'false');
  }

  splash.querySelectorAll('[data-ios-splash-close]').forEach(function (button) {
    button.addEventListener('click', dismissSplash);
  });

  splash.addEventListener('click', function (event) {
    if (event.target === splash) {
      dismissSplash();
    }
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && !splash.hasAttribute('hidden')) {
      dismissSplash();
    }
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', showSplash);
  } else {
    showSplash();
  }
})();
