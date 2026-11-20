'use strict';

(function (global) {
  const Api = global.LaundryApi;

  const state = {
    selectedOrderId: null,
    currencySymbol: '₹'
  };

  function initBillingModule() {
    const lookupForm = document.getElementById('invoiceLookupForm');
    const orderList = document.getElementById('billingOrderList');
    const markPaidBtn = document.getElementById('markPaidBtn');
    const printBtn = document.getElementById('printInvoiceBtn');

    if (lookupForm) {
      lookupForm.addEventListener('submit', function (e) {
        e.preventDefault();
        const orderId = Number(document.getElementById('invoiceOrderIdInput').value);
        if (orderId > 0) {
          loadInvoice(orderId);
        }
      });
    }

    if (orderList) {
      orderList.addEventListener('click', function (e) {
        const btn = e.target.closest('button[data-bill-order]');
        if (btn) {
          const orderId = Number(btn.getAttribute('data-bill-order'));
          loadInvoice(orderId);
        }
      });
    }

    if (markPaidBtn) {
      markPaidBtn.addEventListener('click', handleMarkPaid);
    }

    if (printBtn) {
      printBtn.addEventListener('click', function () {
        window.print();
      });
    }
  }

  async function loadBillingView(preferredOrderId) {
    try {
      const orders = await Api.request('/api/orders');
      renderOrderPicker(orders || []);
      const targetId = preferredOrderId || state.selectedOrderId || (orders && orders.length ? orders[0].orderId : null);
      if (targetId) {
        await loadInvoice(targetId);
      }
    } catch (err) {
      Api.showToast('Failed to load billing list: ' + err.message, 'error');
    }
  }

  function renderOrderPicker(orders) {
    const listEl = document.getElementById('billingOrderList');
    if (!listEl) {
      return;
    }
    if (!orders.length) {
      listEl.innerHTML = '<div class="muted-text">No orders available.</div>';
      return;
    }
    listEl.innerHTML = orders.map(function (o) {
      const isSelected = Number(state.selectedOrderId) === Number(o.orderId);
      const payBadge = o.paymentStatus === 'PAID' ? 'status-PAID' : 'status-UNPAID';
      return '<button type="button" class="invoice-pick-btn' + (isSelected ? ' is-selected' : '') + '" data-bill-order="' + Api.escapeHtml(o.orderId) + '">'
        + '<div>'
        + '  <div class="invoice-pick-title">Order #' + Api.escapeHtml(o.orderId) + ' · ' + Api.escapeHtml(o.customerName) + '</div>'
        + '  <div class="invoice-pick-sub">' + Api.escapeHtml(o.invoiceNumber || '') + ' · ' + Api.escapeHtml(Api.formatMoney(o.totalAmount)) + '</div>'
        + '</div>'
        + '<span class="status-badge ' + payBadge + '">' + Api.escapeHtml(Api.formatEnum(o.paymentStatus || 'UNPAID')) + '</span>'
        + '</button>';
    }).join('');
  }

  async function loadInvoice(orderId) {
    const emptyCard = document.getElementById('invoiceEmptyState');
    const sheet = document.getElementById('invoiceSheet');
    const lookupInput = document.getElementById('invoiceOrderIdInput');
    if (lookupInput) {
      lookupInput.value = String(orderId);
    }

    try {
      const data = await Api.request('/api/billing/' + orderId);
      state.selectedOrderId = orderId;
      state.currencySymbol = (data.shop && data.shop.currencySymbol) || '₹';

      renderInvoiceSheet(data);
      if (emptyCard) emptyCard.hidden = true;
      if (sheet) sheet.hidden = false;

      // Refresh picker selection highlight
      document.querySelectorAll('#billingOrderList .invoice-pick-btn').forEach(function (btn) {
        const id = Number(btn.getAttribute('data-bill-order'));
        btn.classList.toggle('is-selected', id === Number(orderId));
      });
    } catch (err) {
      Api.showToast(err.message, 'error');
    }
  }

  function renderInvoiceSheet(data) {
    const shop = data.shop || {};
    const cust = data.customer || {};
    const order = data.order || {};
    const inv = data.invoice || {};
    const items = data.items || [];
    const cur = shop.currencySymbol || state.currencySymbol || '₹';

    document.getElementById('invShopName').textContent = shop.name || 'Namma Laundry & Dry Care';
    document.getElementById('invShopAddress').textContent = shop.address || '';
    document.getElementById('invNumber').textContent = inv.invoiceNumber || ('INV-' + order.orderId);
    document.getElementById('invDate').textContent = Api.formatDateTime(inv.createdAt);

    const badgeWrap = document.getElementById('invPaymentBadgeWrap');
    const isPaid = inv.paymentStatus === 'PAID';
    badgeWrap.innerHTML = '<span class="status-badge ' + (isPaid ? 'status-PAID' : 'status-UNPAID') + '">'
      + Api.escapeHtml(Api.formatEnum(inv.paymentStatus))
      + '</span>';

    document.getElementById('invCustomerName').textContent = cust.name || order.customerName || '—';
    document.getElementById('invCustomerPhone').textContent = cust.phone || order.customerPhone || '—';
    document.getElementById('invCustomerAddress').textContent = cust.address || 'Walk-in customer';

    document.getElementById('invOrderId').textContent = '#' + order.orderId;
    document.getElementById('invOrderStatus').textContent = Api.formatEnum(order.status);

    const paidMetaEl = document.getElementById('invPaidMeta');
    if (isPaid) {
      paidMetaEl.textContent = 'Paid via ' + (inv.paymentMethod || '—') + ' on ' + Api.formatDateTime(inv.paidAt);
    } else if (order.status === 'CANCELLED') {
      paidMetaEl.textContent = 'Order cancelled';
    } else {
      paidMetaEl.textContent = 'Payment pending';
    }

    const itemsBody = document.getElementById('invItemsBody');
    itemsBody.innerHTML = items.map(function (it) {
      return '<tr>'
        + '<td>' + Api.escapeHtml(Api.formatEnum(it.itemType)) + '</td>'
        + '<td>' + Api.escapeHtml(Api.formatEnum(it.serviceType)) + '</td>'
        + '<td>' + Api.escapeHtml(it.specialInstructions || '—') + '</td>'
        + '<td class="num-col">' + Api.escapeHtml(it.quantity) + '</td>'
        + '<td class="num-col">' + Api.escapeHtml(Api.formatMoney(it.unitPrice, cur)) + '</td>'
        + '<td class="num-col"><strong>' + Api.escapeHtml(Api.formatMoney(it.lineTotal, cur)) + '</strong></td>'
        + '</tr>';
    }).join('');

    document.getElementById('invSubtotal').textContent = Api.formatMoney(inv.subtotal, cur);
    document.getElementById('invTaxRate').textContent = Number(inv.taxRate || 0).toFixed(0);
    document.getElementById('invTaxAmount').textContent = Api.formatMoney(inv.taxAmount, cur);
    document.getElementById('invDiscount').textContent = '-' + Api.formatMoney(inv.discount, cur);
    document.getElementById('invTotalAmount').textContent = Api.formatMoney(inv.totalAmount, cur);

    const payControls = document.getElementById('payControlsGroup');
    if (payControls) {
      payControls.hidden = isPaid || order.status === 'CANCELLED';
    }
  }

  async function handleMarkPaid() {
    if (!state.selectedOrderId) {
      return;
    }
    const method = document.getElementById('paymentMethodSelect').value;
    const btn = document.getElementById('markPaidBtn');
    Api.setButtonLoading(btn, true, 'Recording...');

    try {
      const updated = await Api.request('/api/billing/' + state.selectedOrderId + '/pay', {
        method: 'PUT',
        body: { paymentMethod: method }
      });
      renderInvoiceSheet(updated);
      Api.showToast('Invoice marked PAID via ' + method + '.', 'success');
      const orders = await Api.request('/api/orders');
      renderOrderPicker(orders || []);
    } catch (err) {
      Api.showToast(err.message, 'error');
    } finally {
      Api.setButtonLoading(btn, false);
    }
  }

  global.LaundryBilling = {
    init: initBillingModule,
    load: loadBillingView,
    openOrder: loadInvoice
  };
})(window);
