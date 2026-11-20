'use strict';

(function (global) {
  const Api = global.LaundryApi;

  global.LaundryState = {
    currencySymbol: '₹',
    shopName: 'Namma Laundry & Dry Care'
  };

  const VALID_TABS = ['dashboard', 'customers', 'new-order', 'queue', 'billing'];

  function initApp() {
    if (global.LaundryCustomers) global.LaundryCustomers.init();
    if (global.LaundryOrders) global.LaundryOrders.init();
    if (global.LaundryQueueUI) global.LaundryQueueUI.init();
    if (global.LaundryBilling) global.LaundryBilling.init();

    document.querySelectorAll('.nav-tab[data-tab]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        switchTab(btn.getAttribute('data-tab'));
      });
    });

    document.querySelectorAll('[data-jump-tab]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        switchTab(btn.getAttribute('data-jump-tab'));
      });
    });

    const quickNewBtn = document.getElementById('quickNewOrderBtn');
    if (quickNewBtn) {
      quickNewBtn.addEventListener('click', function () {
        switchTab('new-order');
      });
    }

    const brandHome = document.getElementById('brandHomeLink');
    if (brandHome) {
      brandHome.addEventListener('click', function (e) {
        e.preventDefault();
        switchTab('dashboard');
      });
    }

    const refreshDashBtn = document.getElementById('refreshDashboardBtn');
    if (refreshDashBtn) {
      refreshDashBtn.addEventListener('click', loadDashboard);
    }

    const dashFilter = document.getElementById('dashboardStatusFilter');
    if (dashFilter) {
      dashFilter.addEventListener('change', loadDashboardOrders);
    }

    const dashOrdersBody = document.getElementById('dashboardOrdersBody');
    if (dashOrdersBody) {
      dashOrdersBody.addEventListener('click', function (e) {
        const invBtn = e.target.closest('button[data-dash-invoice]');
        if (invBtn) {
          openInvoiceForOrder(Number(invBtn.getAttribute('data-dash-invoice')));
        }
      });
    }

    const initialHash = (window.location.hash || '').replace('#', '');
    const initialTab = VALID_TABS.indexOf(initialHash) >= 0 ? initialHash : 'dashboard';
    switchTab(initialTab);
  }

  async function switchTab(tabName) {
    if (VALID_TABS.indexOf(tabName) < 0) {
      tabName = 'dashboard';
    }

    if (tabName !== 'queue' && global.LaundryQueueUI) {
      global.LaundryQueueUI.stopPolling();
    }

    document.querySelectorAll('.nav-tab').forEach(function (btn) {
      btn.classList.toggle('is-active', btn.getAttribute('data-tab') === tabName);
    });
    document.querySelectorAll('.view-section').forEach(function (sec) {
      sec.classList.toggle('is-active', sec.id === 'view-' + tabName);
    });

    if (window.location.hash !== '#' + tabName) {
      window.history.replaceState(null, '', '#' + tabName);
    }

    if (tabName === 'dashboard') {
      await loadDashboard();
    } else if (tabName === 'customers' && global.LaundryCustomers) {
      await global.LaundryCustomers.load('');
    } else if (tabName === 'new-order' && global.LaundryOrders) {
      await global.LaundryOrders.load();
    } else if (tabName === 'queue' && global.LaundryQueueUI) {
      await global.LaundryQueueUI.load();
    } else if (tabName === 'billing' && global.LaundryBilling) {
      await global.LaundryBilling.load();
    }
  }

  async function openInvoiceForOrder(orderId) {
    await switchTab('billing');
    if (global.LaundryBilling) {
      await global.LaundryBilling.openOrder(orderId);
    }
  }

  async function loadDashboard() {
    try {
      const summary = await Api.request('/api/billing/summary');
      const cur = summary.currencySymbol || '₹';
      global.LaundryState.currencySymbol = cur;
      global.LaundryState.shopName = summary.shopName || 'Namma Laundry & Dry Care';

      const subEl = document.getElementById('shopSubtitle');
      if (subEl) {
        subEl.textContent = (summary.shopName || 'Namma Laundry & Dry Care')
          + (summary.shopAddress ? ' · ' + summary.shopAddress : '');
      }

      document.getElementById('kpiRevenue').textContent = Api.formatMoney(summary.revenueCollected, cur);
      document.getElementById('kpiUnpaid').textContent = Api.formatMoney(summary.unpaidAmount, cur);
      document.getElementById('kpiTotalOrders').textContent = String(summary.totalOrders || 0);

      const counts = summary.statusCounts || {};
      const activeCount = (counts.QUEUED || 0)
        + (counts.WASHING || 0)
        + (counts.IRONING || 0)
        + (counts.READY_FOR_PICKUP || 0);
      document.getElementById('kpiActive').textContent = String(activeCount);

      const statuses = ['QUEUED', 'WASHING', 'IRONING', 'READY_FOR_PICKUP', 'DELIVERED', 'CANCELLED'];
      const grid = document.getElementById('statusCountsGrid');
      if (grid) {
        grid.innerHTML = statuses.map(function (st) {
          return '<div class="status-count-box">'
            + '<span class="status-badge status-' + st + '">' + Api.escapeHtml(Api.formatEnum(st)) + '</span>'
            + '<strong class="count-num tabular-nums">' + Api.escapeHtml(counts[st] || 0) + '</strong>'
            + '</div>';
        }).join('');
      }

      await loadDashboardOrders();
    } catch (err) {
      Api.showToast('Failed to load dashboard: ' + err.message, 'error');
    }
  }

  async function loadDashboardOrders() {
    const filterEl = document.getElementById('dashboardStatusFilter');
    const tbody = document.getElementById('dashboardOrdersBody');
    if (!tbody) {
      return;
    }
    const status = filterEl ? filterEl.value : '';
    const query = status ? '?status=' + encodeURIComponent(status) : '';

    try {
      const orders = await Api.request('/api/orders' + query);
      if (!orders || !orders.length) {
        tbody.innerHTML = '<tr class="empty-row"><td colspan="8">No orders match this status filter.</td></tr>';
        return;
      }

      tbody.innerHTML = orders.map(function (o) {
        const payClass = o.paymentStatus === 'PAID' ? 'status-PAID' : 'status-UNPAID';
        return '<tr>'
          + '<td class="tabular-nums"><strong>#' + Api.escapeHtml(o.orderId) + '</strong></td>'
          + '<td>' + Api.escapeHtml(o.customerName) + '<div class="muted-text tabular-nums">' + Api.escapeHtml(o.customerPhone) + '</div></td>'
          + '<td class="tabular-nums">' + Api.escapeHtml(o.itemCount) + '</td>'
          + '<td><span class="status-badge status-' + Api.escapeHtml(o.status) + '">' + Api.escapeHtml(Api.formatEnum(o.status)) + '</span></td>'
          + '<td class="num-col">' + Api.escapeHtml(Api.formatMoney(o.totalAmount)) + '</td>'
          + '<td><span class="status-badge ' + payClass + '">' + Api.escapeHtml(Api.formatEnum(o.paymentStatus || 'UNPAID')) + '</span></td>'
          + '<td>' + Api.escapeHtml(Api.formatDateTime(o.createdAt)) + '</td>'
          + '<td class="actions-col">'
          + '<button type="button" class="btn btn-outline btn-sm" data-dash-invoice="' + Api.escapeHtml(o.orderId) + '">Invoice</button>'
          + '</td>'
          + '</tr>';
      }).join('');
    } catch (err) {
      tbody.innerHTML = '<tr class="empty-row"><td colspan="8">' + Api.escapeHtml(err.message) + '</td></tr>';
    }
  }

  global.LaundryApp = {
    switchTab: switchTab,
    openInvoiceForOrder: openInvoiceForOrder,
    refreshDashboard: loadDashboard
  };

  document.addEventListener('DOMContentLoaded', initApp);
})(window);
