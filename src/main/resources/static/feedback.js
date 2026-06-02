(function () {
  'use strict';

  const form = document.getElementById('feedback-form');
  const modalEl = document.getElementById('feedback-modal');
  if (!form || !modalEl) return;

  const statusEl = document.getElementById('feedback-status');
  const submitButton = form.querySelector('.feedback-submit-button');
  const submitText = form.querySelector('.feedback-submit-text');
  const messageInput = document.getElementById('feedback-message');
  const emailInput = document.getElementById('feedback-email');

  form.addEventListener('submit', async function (event) {
    event.preventDefault();

    const message = messageInput.value.trim();
    const email = emailInput.value.trim();

    if (!message) {
      setStatus('Message is required.', 'error');
      messageInput.focus();
      return;
    }

    if (email && !emailInput.checkValidity()) {
      setStatus('Email is invalid.', 'error');
      emailInput.focus();
      return;
    }

    setLoading(true);
    setStatus('', '');

    try {
      const response = await fetch('/api/v1/feedback', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
          message: message,
          email: email || null,
          metadata: buildMetadata()
        })
      });
      const result = await response.json().catch(function () { return {}; });

      if (!response.ok) {
        const apiMessage = result && result.error && result.error.message;
        throw new Error(apiMessage || 'Feedback could not be sent.');
      }

      setStatus('Sent. Thank you.', 'success');
      form.reset();

      window.setTimeout(function () {
        const modal = window.bootstrap && window.bootstrap.Modal
          ? window.bootstrap.Modal.getInstance(modalEl)
          : null;
        if (modal) modal.hide();
      }, 900);
    } catch (error) {
      setStatus(error.message || 'Feedback could not be sent.', 'error');
    } finally {
      setLoading(false);
    }
  });

  modalEl.addEventListener('hidden.bs.modal', function () {
    setStatus('', '');
    setLoading(false);
  });

  function buildMetadata() {
    const screen = inferScreen();
    const organization = organizationContext();
    const minyan = minyanContext();
    const posthog = posthogContext();

    return {
      platform: 'web',
      screen: screen,
      page: screen,
      route: window.location.pathname,
      url: window.location.href,
      appVersion: modalEl.dataset.appVersion || '',
      browser: browserName(),
      userAgent: window.navigator.userAgent,
      osName: platformName(),
      selectedDate: modalEl.dataset.selectedDate || null,
      organization: organization,
      minyan: minyan,
      posthog: posthog,
      filters: filters(),
      routeParams: routeParams(screen),
      extra: extraContext()
    };
  }

  function inferScreen() {
    const path = window.location.pathname;
    if (path === '/' || path === '/zmanim' || path.startsWith('/zmanim/')) {
      return 'homepage';
    }
    if (path === '/privacy') {
      return 'privacy';
    }
    if (path === '/subscription') {
      return 'subscription';
    }
    if (window.shulname || path.startsWith('/org/')) {
      return 'organization-detail';
    }
    return path.replace(/^\/+/, '') || 'public-page';
  }

  function organizationContext() {
    const name = stringValue(window.shulname);
    if (!name) return null;

    return {
      id: '',
      slug: organizationSlug(),
      name: name
    };
  }

  function organizationSlug() {
    const segments = window.location.pathname.split('/').filter(Boolean);
    if (segments[0] === 'org' && segments[1]) {
      return segments[1];
    }
    if (segments.length === 1 && segments[0] !== 'zmanim' && segments[0] !== 'privacy' && segments[0] !== 'subscription') {
      return segments[0];
    }
    return '';
  }

  function minyanContext() {
    const type = stringValue(window.minyantype);
    const time = stringValue(window.minyantime);
    if (!type && !time) return null;

    return {
      id: '',
      type: type,
      time: time,
      date: modalEl.dataset.selectedDate || '',
      locationName: ''
    };
  }

  function posthogContext() {
    const posthog = window.posthog;
    if (!posthog) return null;

    const context = {
      distinctId: safeCall(posthog, 'get_distinct_id'),
      sessionId: safeCall(posthog, 'get_session_id'),
      sessionReplayUrl: safeCall(posthog, 'get_session_replay_url')
    };

    if (!context.sessionId && posthog.sessionManager && typeof posthog.sessionManager.checkAndGetSessionAndWindowId === 'function') {
      try {
        const session = posthog.sessionManager.checkAndGetSessionAndWindowId(true);
        context.sessionId = stringValue(session && session.sessionId);
      } catch (error) {
        context.sessionId = '';
      }
    }

    if (context.distinctId || context.sessionId || context.sessionReplayUrl) {
      return context;
    }
    return null;
  }

  function filters() {
    const values = {
      minyanType: activeMinyanTypeFilter(),
      shul: activeShulFilter()
    };
    return compact(values);
  }

  function routeParams(screen) {
    const params = {};
    const slug = organizationSlug();
    const query = new URLSearchParams(window.location.search);

    if (screen === 'organization-detail' && slug) {
      params.slug = slug;
    }
    if (query.has('after')) {
      params.after = query.get('after');
    }
    if (query.has('before')) {
      params.before = query.get('before');
    }
    return compact(params);
  }

  function extraContext() {
    return compact({
      displayDate: modalEl.dataset.displayDate || '',
      referrer: document.referrer || '',
      viewport: window.innerWidth + 'x' + window.innerHeight,
      devicePixelRatio: String(window.devicePixelRatio || ''),
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || '',
      language: window.navigator.language || '',
      colorScheme: window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light',
      currentHash: window.location.hash || '',
      currentUpcomingMinyanType: stringValue(window.minyantype),
      currentUpcomingMinyanTime: stringValue(window.minyantime)
    });
  }

  function activeMinyanTypeFilter() {
    const active = document.querySelector('.filter-pill.active[data-filter-type]');
    return active ? active.getAttribute('data-filter-type') : '';
  }

  function activeShulFilter() {
    const select = document.getElementById('shul-filter-select');
    return select ? select.value : '';
  }

  function safeCall(target, method) {
    if (!target || typeof target[method] !== 'function') return '';
    try {
      return stringValue(target[method]());
    } catch (error) {
      return '';
    }
  }

  function compact(values) {
    const compacted = {};
    Object.keys(values).forEach(function (key) {
      const value = stringValue(values[key]);
      if (value) {
        compacted[key] = value;
      }
    });
    return compacted;
  }

  function stringValue(value) {
    if (value === null || value === undefined) return '';
    return String(value).trim();
  }

  function browserName() {
    const ua = window.navigator.userAgent;
    if (ua.includes('Edg/')) return 'Edge';
    if (ua.includes('Chrome/')) return 'Chrome';
    if (ua.includes('Safari/') && !ua.includes('Chrome/')) return 'Safari';
    if (ua.includes('Firefox/')) return 'Firefox';
    return '';
  }

  function platformName() {
    if (window.navigator.userAgentData && window.navigator.userAgentData.platform) {
      return window.navigator.userAgentData.platform;
    }
    return window.navigator.platform || '';
  }

  function setLoading(isLoading) {
    if (!submitButton) return;
    submitButton.disabled = isLoading;
    submitButton.classList.toggle('is-loading', isLoading);
    if (submitText) submitText.textContent = isLoading ? 'Sending' : 'Send';
  }

  function setStatus(message, type) {
    if (!statusEl) return;
    statusEl.textContent = message;
    statusEl.className = 'feedback-status';
    if (type === 'error') {
      statusEl.classList.add('feedback-status-error');
    } else if (type === 'success') {
      statusEl.classList.add('feedback-status-success');
    }
  }
})();
