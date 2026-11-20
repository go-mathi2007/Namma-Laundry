'use strict';

(function (global) {
  const DEFAULT_API_BASE_URL = 'http://localhost:8080';

  function resolveApiBaseUrl() {
    if (typeof global.API_BASE_URL === 'string') {
      return global.API_BASE_URL;
    }
    if (typeof window !== 'undefined' && window.location) {
      const { protocol, hostname, port } = window.location;
      if (protocol === 'file:') {
        return DEFAULT_API_BASE_URL;
      }
      if ((hostname === 'localhost' || hostname === '127.0.0.1') && port === '8080') {
        return DEFAULT_API_BASE_URL;
      }
      if (port === '3000' || hostname.endsWith('.run.app')) {
        return '';
      }
    }
    return DEFAULT_API_BASE_URL;
  }

  const API_BASE_URL = resolveApiBaseUrl();

  function escapeHtml(value) {
    if (value === null || value === undefined) {
      return '';
    }
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatMoney(amount, symbol) {
    const cur = symbol || (global.LaundryState && global.LaundryState.currencySymbol) || '₹';
    const num = Number(amount || 0);
    return cur + num.toFixed(2);
  }

  function formatDateTime(ts) {
    if (!ts) {
      return '—';
    }
    const date = new Date(ts);
    if (Number.isNaN(date.getTime())) {
      return String(ts);
    }
    return date.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  function formatEnum(val) {
    if (!val) {
      return '—';
    }
    const labels = {
      QUEUED: 'Queued',
      WASHING: 'Washing',
      IRONING: 'Ironing',
      READY_FOR_PICKUP: 'Ready for Pickup',
      DELIVERED: 'Delivered',
      CANCELLED: 'Cancelled',
      WASH_FOLD: 'Wash & Fold',
      DRY_CLEAN: 'Dry Clean',
      STEAM_PRESS: 'Steam Press',
      SHIRT: 'Shirt',
      TROUSERS: 'Trousers',
      SAREE: 'Saree',
      JACKET: 'Jacket',
      BEDSHEET: 'Bedsheet',
      TOWEL: 'Towel',
      DELICATE: 'Delicate',
      BLANKET: 'Blanket',
      PAID: 'Paid',
      UNPAID: 'Unpaid'
    };
    return labels[val] || String(val).replace(/_/g, ' ');
  }

  function setButtonLoading(btn, isLoading, loadingText) {
    if (!btn) {
      return;
    }
    if (isLoading) {
      if (!btn.dataset.originalText) {
        btn.dataset.originalText = btn.textContent;
      }
      btn.disabled = true;
      if (loadingText) {
        btn.textContent = loadingText;
      }
    } else {
      btn.disabled = false;
      if (btn.dataset.originalText) {
        btn.textContent = btn.dataset.originalText;
      }
    }
  }

  function showToast(message, type) {
    const container = document.getElementById('toastContainer');
    if (!container) {
      return;
    }
    const toast = document.createElement('div');
    const kind = type || 'info';
    toast.className = 'toast toast-' + kind;
    toast.setAttribute('role', 'status');

    const msgSpan = document.createElement('span');
    msgSpan.textContent = message;

    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'toast-close';
    closeBtn.setAttribute('aria-label', 'Dismiss notification');
    closeBtn.textContent = '×';
    closeBtn.addEventListener('click', function () {
      toast.remove();
    });

    toast.appendChild(msgSpan);
    toast.appendChild(closeBtn);
    container.appendChild(toast);

    setTimeout(function () {
      if (toast.parentNode) {
        toast.remove();
      }
    }, 4500);
  }

  async function apiRequest(path, options) {
    const opts = options ? Object.assign({}, options) : {};
    const headers = Object.assign({}, opts.headers || {});
    if (opts.body !== undefined && typeof opts.body !== 'string') {
      headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(opts.body);
    }
    opts.headers = headers;

    const url = API_BASE_URL + path;
    const response = await fetch(url, opts);
    let payload = null;
    try {
      payload = await response.json();
    } catch (_err) {
      throw new Error('Server returned invalid JSON (HTTP ' + response.status + ')');
    }

    if (!response.ok || !payload || payload.success === false) {
      const msg = (payload && payload.error) ? payload.error : ('Request failed (HTTP ' + response.status + ')');
      const err = new Error(msg);
      err.status = response.status;
      throw err;
    }

    return payload.data;
  }

  global.LaundryApi = {
    API_BASE_URL: API_BASE_URL,
    request: apiRequest,
    escapeHtml: escapeHtml,
    formatMoney: formatMoney,
    formatDateTime: formatDateTime,
    formatEnum: formatEnum,
    setButtonLoading: setButtonLoading,
    showToast: showToast
  };
})(window);
