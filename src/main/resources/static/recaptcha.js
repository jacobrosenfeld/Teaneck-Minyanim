(function () {
  'use strict';

  const currentScript = document.currentScript;
  const siteKey = currentScript ? (currentScript.dataset.siteKey || '').trim() : '';
  if (!siteKey) return;

  const API_URL = 'https://www.google.com/recaptcha/api.js?render=explicit';
  let apiPromise = null;
  let widgetId = null;
  let pending = null;

  window.teaneckRecaptcha = {
    isEnabled: true,
    execute: execute
  };

  document.addEventListener('submit', function (event) {
    const form = event.target;
    if (!form || !form.matches('[data-recaptcha-action]')) {
      return;
    }

    if (form.dataset.recaptchaVerified === 'true') {
      delete form.dataset.recaptchaVerified;
      return;
    }

    event.preventDefault();
    execute(form.dataset.recaptchaAction || 'submit')
      .then(function (token) {
        setFormToken(form, token);
        form.dataset.recaptchaVerified = 'true';
        if (typeof form.requestSubmit === 'function') {
          form.requestSubmit(event.submitter || undefined);
        } else {
          HTMLFormElement.prototype.submit.call(form);
        }
      })
      .catch(function () {
        setFormStatus(form, 'Please try submitting again.');
      });
  }, true);

  function execute(action) {
    return ensureWidget()
      .then(function () {
        if (pending && pending.reject) {
          pending.reject(new Error('A reCAPTCHA check is already in progress.'));
        }

        return new Promise(function (resolve, reject) {
          pending = {
            resolve: resolve,
            reject: reject
          };

          try {
            window.grecaptcha.reset(widgetId);
            window.grecaptcha.execute(widgetId);
          } catch (error) {
            pending = null;
            reject(error);
          }
        });
      });
  }

  function ensureWidget() {
    return loadApi().then(function () {
      if (widgetId !== null) {
        return widgetId;
      }

      const container = document.createElement('div');
      container.id = 'recaptcha-invisible-widget';
      container.className = 'recaptcha-invisible-widget';
      document.body.appendChild(container);

      widgetId = window.grecaptcha.render(container, {
        sitekey: siteKey,
        size: 'invisible',
        badge: 'bottomleft',
        callback: function (token) {
          if (pending && pending.resolve) {
            pending.resolve(token);
          }
          pending = null;
        },
        'expired-callback': function () {
          if (pending && pending.reject) {
            pending.reject(new Error('reCAPTCHA expired.'));
          }
          pending = null;
        },
        'error-callback': function () {
          if (pending && pending.reject) {
            pending.reject(new Error('reCAPTCHA failed.'));
          }
          pending = null;
        }
      });

      return widgetId;
    });
  }

  function loadApi() {
    if (window.grecaptcha && typeof window.grecaptcha.render === 'function') {
      return Promise.resolve();
    }
    if (apiPromise) {
      return apiPromise;
    }

    apiPromise = new Promise(function (resolve, reject) {
      const script = document.createElement('script');
      script.src = API_URL;
      script.async = true;
      script.defer = true;
      script.onload = function () {
        waitForApi(resolve, reject);
      };
      script.onerror = function () {
        reject(new Error('reCAPTCHA could not be loaded.'));
      };
      document.head.appendChild(script);
    });

    return apiPromise;
  }

  function waitForApi(resolve, reject) {
    let attempts = 0;
    const timer = window.setInterval(function () {
      attempts += 1;
      if (window.grecaptcha && typeof window.grecaptcha.render === 'function') {
        window.clearInterval(timer);
        resolve();
      } else if (attempts > 80) {
        window.clearInterval(timer);
        reject(new Error('reCAPTCHA did not become ready.'));
      }
    }, 50);
  }

  function setFormToken(form, token) {
    let input = form.querySelector('input[name="recaptchaToken"]');
    if (!input) {
      input = document.createElement('input');
      input.type = 'hidden';
      input.name = 'recaptchaToken';
      form.appendChild(input);
    }
    input.value = token;
  }

  function setFormStatus(form, message) {
    const statusId = form.dataset.recaptchaStatus;
    const statusEl = statusId ? document.getElementById(statusId) : null;
    if (statusEl) {
      statusEl.textContent = message;
    }
  }
})();
