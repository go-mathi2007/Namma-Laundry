'use strict';

(function (global) {
  const Api = global.LaundryApi;

  const state = {
    customers: [],
    lastSearch: ''
  };

  function initCustomersModule() {
    const form = document.getElementById('customerForm');
    const searchForm = document.getElementById('customerSearchForm');
    const clearSearchBtn = document.getElementById('clearCustomerSearchBtn');
    const exportCsvBtn = document.getElementById('exportCustomersCsvBtn');
    const closeHistoryBtn = document.getElementById('closeHistoryBtn');

    if (form) {
      form.addEventListener('submit', handleRegisterCustomer);
      form.addEventListener('reset', function () {
        hideFormError();
      });
    }

    if (searchForm) {
      searchForm.addEventListener('submit', function (e) {
        e.preventDefault();
        const term = document.getElementById('customerSearchInput').value.trim();
        loadCustomers(term);
      });
    }

    if (clearSearchBtn) {
      clearSearchBtn.addEventListener('click', function () {
        document.getElementById('customerSearchInput').value = '';
        loadCustomers('');
      });
    }

    if (exportCsvBtn) {
      exportCsvBtn.addEventListener('click', exportCustomersCsv);
    }

    if (closeHistoryBtn) {
      closeHistoryBtn.addEventListener('click', function () {
        document.getElementById('customerHistoryPanel').hidden = true;
      });
    }

    const tbody = document.getElementById('customersTableBody');
    if (tbody) {
      tbody.addEventListener('click', function (e) {
        const btn = e.target.closest('button[data-history-id]');
        if (btn) {
          const customerId = Number(btn.getAttribute('data-history-id'));
          viewCustomerHistory(customerId);
        }
      });
    }

    const historyBody = document.getElementById('customerHistoryBody');
    if (historyBody) {
      historyBody.addEventListener('click', function (e) {
        const btn = e.target.closest('button[data-invoice-order]');
        if (btn && global.LaundryApp) {
          const orderId = Number(btn.getAttribute('data-invoice-order'));
          global.LaundryApp.openInvoiceForOrder(orderId);
        }
      });
    }
  }

  function showFormError(msg) {
    const el = document.getElementById('customerFormError');
    if (el) {
      el.textContent = msg;
      el.hidden = false;
    }
  }

  function hideFormError() {
    const el = document.getElementById('customerFormError');
    if (el) {
      el.textContent = '';
      el.hidden = true;
    }
  }

  async function handleRegisterCustomer(e) {
    e.preventDefault();
    hideFormError();

    const name = document.getElementById('custName').value.trim();
    const phone = document.getElementById('custPhone').value.trim();
    const email = document.getElementById('custEmail').value.trim();
    const address = document.getElementById('custAddress').value.trim();

    if (!name) {
      showFormError('Customer name is required.');
      return;
    }
    const normalizedPhone = phone.replace(/[\s()-]/g, '');
    if (!/^[+]?[0-9]{7,15}$/.test(normalizedPhone)) {
      showFormError('Phone number must contain 7 to 15 digits.');
      return;
    }
    if (email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
      showFormError('Please enter a valid email address.');
      return;
    }

    const saveBtn = document.getElementById('saveCustomerBtn');
    Api.setButtonLoading(saveBtn, true, 'Saving...');

    try {
      const created = await Api.request('/api/customers', {
        method: 'POST',
        body: { name, phone: normalizedPhone, email, address }
      });
      Api.showToast('Registered customer ' + created.name + '.', 'success');
      document.getElementById('customerForm').reset();
      await loadCustomers(state.lastSearch);
      if (global.LaundryOrders) {
        await global.LaundryOrders.refreshCustomersDropdown(created.customerId);
      }
    } catch (err) {
      showFormError(err.message);
      Api.showToast(err.message, 'error');
    } finally {
      Api.setButtonLoading(saveBtn, false);
    }
  }

  async function loadCustomers(searchTerm) {
    state.lastSearch = searchTerm || '';
    const tbody = document.getElementById('customersTableBody');
    if (!tbody) {
      return [];
    }

    try {
      const query = state.lastSearch ? '?search=' + encodeURIComponent(state.lastSearch) : '';
      const list = await Api.request('/api/customers' + query);
      state.customers = list || [];
      renderCustomersTable(state.customers);
      return state.customers;
    } catch (err) {
      tbody.innerHTML = '<tr class="empty-row"><td colspan="6">' + Api.escapeHtml(err.message) + '</td></tr>';
      Api.showToast(err.message, 'error');
      return [];
    }
  }

  function renderCustomersTable(customers) {
    const tbody = document.getElementById('customersTableBody');
    if (!tbody) {
      return;
    }
    if (!customers.length) {
      tbody.innerHTML = '<tr class="empty-row"><td colspan="6">No customers match your search.</td></tr>';
      return;
    }

    tbody.innerHTML = customers.map(function (c) {
      return '<tr>'
        + '<td class="tabular-nums">#' + Api.escapeHtml(c.customerId) + '</td>'
        + '<td><strong>' + Api.escapeHtml(c.name) + '</strong></td>'
        + '<td class="tabular-nums">' + Api.escapeHtml(c.phone) + '</td>'
        + '<td>' + Api.escapeHtml(c.email || '—') + '</td>'
        + '<td>' + Api.escapeHtml(c.address || '—') + '</td>'
        + '<td class="actions-col">'
        + '<button type="button" class="btn btn-outline btn-sm" data-history-id="' + Api.escapeHtml(c.customerId) + '">View History</button>'
        + '</td>'
        + '</tr>';
    }).join('');
  }

  async function viewCustomerHistory(customerId) {
    const panel = document.getElementById('customerHistoryPanel');
    const titleEl = document.getElementById('historyPanelTitle');
    const subEl = document.getElementById('historyPanelSubtitle');
    const tbody = document.getElementById('customerHistoryBody');
    if (!panel || !tbody) {
      return;
    }

    panel.hidden = false;
    tbody.innerHTML = '<tr class="empty-row"><td colspan="7">Loading order history...</td></tr>';

    try {
      const data = await Api.request('/api/customers/' + customerId + '/orders');
      const cust = data.customer;
      const orders = data.orders || [];

      titleEl.textContent = cust.name + ' — Order History';
      subEl.textContent = cust.phone + (cust.address ? ' · ' + cust.address : '');

      if (!orders.length) {
        tbody.innerHTML = '<tr class="empty-row"><td colspan="7">No orders placed for this customer yet.</td></tr>';
        return;
      }

      tbody.innerHTML = orders.map(function (o) {
        const payClass = o.paymentStatus === 'PAID' ? 'status-PAID' : 'status-UNPAID';
        return '<tr>'
          + '<td class="tabular-nums"><strong>#' + Api.escapeHtml(o.orderId) + '</strong></td>'
          + '<td><span class="status-badge status-' + Api.escapeHtml(o.status) + '">' + Api.escapeHtml(Api.formatEnum(o.status)) + '</span></td>'
          + '<td class="tabular-nums">' + Api.escapeHtml(o.itemCount) + '</td>'
          + '<td class="num-col">' + Api.escapeHtml(Api.formatMoney(o.totalAmount)) + '</td>'
          + '<td><span class="status-badge ' + payClass + '">' + Api.escapeHtml(Api.formatEnum(o.paymentStatus || 'UNPAID')) + '</span></td>'
          + '<td>' + Api.escapeHtml(Api.formatDateTime(o.createdAt)) + '</td>'
          + '<td class="actions-col">'
          + '<button type="button" class="btn btn-outline btn-sm" data-invoice-order="' + Api.escapeHtml(o.orderId) + '">Invoice</button>'
          + '</td>'
          + '</tr>';
      }).join('');
      panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    } catch (err) {
      tbody.innerHTML = '<tr class="empty-row"><td colspan="7">' + Api.escapeHtml(err.message) + '</td></tr>';
      Api.showToast(err.message, 'error');
    }
  }

  function escapeCsvCell(value) {
    if (value === null || value === undefined) {
      return '';
    }
    const str = String(value);
    if (/[",\r\n]/.test(str)) {
      return '"' + str.replace(/"/g, '""') + '"';
    }
    return str;
  }

  function buildCustomersCsv(customers) {
    const headers = ['Customer ID', 'Name', 'Phone', 'Email', 'Address', 'Created At'];
    const lines = [headers.join(',')];
    customers.forEach(function (c) {
      lines.push([
        escapeCsvCell(c.customerId),
        escapeCsvCell(c.name),
        escapeCsvCell(c.phone),
        escapeCsvCell(c.email || ''),
        escapeCsvCell(c.address || ''),
        escapeCsvCell(c.createdAt || '')
      ].join(','));
    });
    return '\uFEFF' + lines.join('\r\n');
  }

  function exportCustomersCsv() {
    if (!state.customers || !state.customers.length) {
      Api.showToast('No customers available to export.', 'warning');
      return;
    }
    const csvContent = buildCustomersCsv(state.customers);
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'namma-laundry-customers.csv';
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(function () {
      URL.revokeObjectURL(url);
    }, 1000);
    Api.showToast('Exported ' + state.customers.length + ' customers to CSV.', 'success');
  }

  global.LaundryCustomers = {
    init: initCustomersModule,
    load: loadCustomers,
    buildCsv: buildCustomersCsv
  };
})(window);
