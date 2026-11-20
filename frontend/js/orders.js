'use strict';

(function (global) {
  const Api = global.LaundryApi;

  const ITEM_TYPES = ['SHIRT', 'TROUSERS', 'SAREE', 'JACKET', 'BEDSHEET', 'TOWEL', 'DELICATE', 'BLANKET'];
  const SERVICE_TYPES = ['WASH_FOLD', 'DRY_CLEAN', 'STEAM_PRESS'];

  const state = {
    prices: [],
    priceMap: {},
    taxRatePercent: 18,
    currencySymbol: '₹',
    rowSeq: 0
  };

  function initOrdersModule() {
    const addBtn = document.getElementById('addOrderItemRowBtn');
    const form = document.getElementById('newOrderForm');
    const discountInput = document.getElementById('orderDiscount');
    const container = document.getElementById('orderItemsContainer');

    if (addBtn) {
      addBtn.addEventListener('click', function () {
        addOrderRow();
      });
    }

    if (discountInput) {
      discountInput.addEventListener('input', updateOrderTotalsPreview);
    }

    if (container) {
      container.addEventListener('change', function (e) {
        if (e.target.matches('select, input')) {
          updateOrderTotalsPreview();
        }
      });
      container.addEventListener('input', function (e) {
        if (e.target.matches('input[type="number"]')) {
          updateOrderTotalsPreview();
        }
      });
      container.addEventListener('click', function (e) {
        const removeBtn = e.target.closest('button[data-remove-row]');
        if (removeBtn) {
          const row = removeBtn.closest('.order-item-row');
          if (row && container.querySelectorAll('.order-item-row').length > 1) {
            row.remove();
            updateOrderTotalsPreview();
          } else {
            Api.showToast('An order must contain at least one garment item.', 'warning');
          }
        }
      });
    }

    if (form) {
      form.addEventListener('submit', handleSubmitOrder);
    }
  }

  async function loadNewOrderContext(selectedCustomerId) {
    try {
      const [pricePayload, customers] = await Promise.all([
        Api.request('/api/prices'),
        Api.request('/api/customers')
      ]);

      state.prices = pricePayload.prices || [];
      state.priceMap = {};
      state.prices.forEach(function (p) {
        state.priceMap[p.itemType + '|' + p.serviceType] = Number(p.unitPrice);
      });
      state.taxRatePercent = Number(pricePayload.taxRatePercent !== undefined ? pricePayload.taxRatePercent : 18);
      state.currencySymbol = pricePayload.currencySymbol || '₹';

      if (global.LaundryState) {
        global.LaundryState.currencySymbol = state.currencySymbol;
        global.LaundryState.shopName = pricePayload.shopName || 'Namma Laundry & Dry Care';
      }

      document.querySelectorAll('.currency-label').forEach(function (el) {
        el.textContent = state.currencySymbol;
      });
      const taxRateEl = document.getElementById('summaryTaxRate');
      if (taxRateEl) {
        taxRateEl.textContent = state.taxRatePercent.toFixed(0);
      }

      populateCustomerSelect(customers || [], selectedCustomerId);
      renderTariffReference();

      const container = document.getElementById('orderItemsContainer');
      if (container && container.children.length === 0) {
        addOrderRow();
      } else {
        updateOrderTotalsPreview();
      }
    } catch (err) {
      Api.showToast('Could not load pricing or customer data: ' + err.message, 'error');
    }
  }

  async function refreshCustomersDropdown(selectCustomerId) {
    try {
      const customers = await Api.request('/api/customers');
      populateCustomerSelect(customers || [], selectCustomerId);
    } catch (_err) {
      // Non-fatal refresh error
    }
  }

  function populateCustomerSelect(customers, selectedCustomerId) {
    const select = document.getElementById('orderCustomerSelect');
    if (!select) {
      return;
    }
    const previous = selectedCustomerId ? String(selectedCustomerId) : select.value;
    if (!customers.length) {
      select.innerHTML = '<option value="">No customers registered yet</option>';
      return;
    }
    select.innerHTML = '<option value="">Select a customer...</option>'
      + customers.map(function (c) {
        return '<option value="' + Api.escapeHtml(c.customerId) + '">'
          + Api.escapeHtml(c.name) + ' (' + Api.escapeHtml(c.phone) + ')'
          + '</option>';
      }).join('');
    if (previous) {
      select.value = previous;
    }
  }

  function renderTariffReference() {
    const el = document.getElementById('tariffReferenceBody');
    if (!el) {
      return;
    }
    if (!state.prices.length) {
      el.innerHTML = '<div class="muted-text">No tariff entries loaded.</div>';
      return;
    }
    el.innerHTML = state.prices.map(function (p) {
      return '<div class="tariff-row">'
        + '<span>' + Api.escapeHtml(Api.formatEnum(p.itemType)) + ' · ' + Api.escapeHtml(Api.formatEnum(p.serviceType)) + '</span>'
        + '<strong class="tabular-nums">' + Api.escapeHtml(Api.formatMoney(p.unitPrice, state.currencySymbol)) + '</strong>'
        + '</div>';
    }).join('');
  }

  function addOrderRow() {
    const container = document.getElementById('orderItemsContainer');
    if (!container) {
      return;
    }
    state.rowSeq += 1;
    const idx = state.rowSeq;
    const row = document.createElement('div');
    row.className = 'order-item-row';

    const itemOpts = ITEM_TYPES.map(function (t) {
      return '<option value="' + t + '">' + Api.escapeHtml(Api.formatEnum(t)) + '</option>';
    }).join('');
    const serviceOpts = SERVICE_TYPES.map(function (s) {
      return '<option value="' + s + '">' + Api.escapeHtml(Api.formatEnum(s)) + '</option>';
    }).join('');

    row.innerHTML = ''
      + '<div class="form-field">'
      + '  <label for="row-item-' + idx + '">Garment</label>'
      + '  <select id="row-item-' + idx + '" class="row-item-type">' + itemOpts + '</select>'
      + '</div>'
      + '<div class="form-field">'
      + '  <label for="row-service-' + idx + '">Service</label>'
      + '  <select id="row-service-' + idx + '" class="row-service-type">' + serviceOpts + '</select>'
      + '</div>'
      + '<div class="form-field">'
      + '  <label for="row-qty-' + idx + '">Qty</label>'
      + '  <input id="row-qty-' + idx + '" type="number" class="row-qty" min="1" step="1" value="1" required />'
      + '</div>'
      + '<div class="form-field">'
      + '  <label for="row-inst-' + idx + '">Instructions</label>'
      + '  <input id="row-inst-' + idx + '" type="text" class="row-instructions" maxlength="255" placeholder="Optional care note" />'
      + '</div>'
      + '<div class="item-line-preview">'
      + '  <span class="item-unit-price tabular-nums">@ ' + state.currencySymbol + '0.00</span>'
      + '  <strong class="item-line-total tabular-nums">' + state.currencySymbol + '0.00</strong>'
      + '</div>'
      + '<button type="button" class="btn btn-outline btn-sm" data-remove-row aria-label="Remove garment row">Remove</button>';

    container.appendChild(row);
    updateOrderTotalsPreview();
  }

  function roundMoney(num) {
    return Math.round((num + Number.EPSILON) * 100) / 100;
  }

  function updateOrderTotalsPreview() {
    const rows = document.querySelectorAll('#orderItemsContainer .order-item-row');
    let subtotal = 0;
    let totalCount = 0;

    rows.forEach(function (row) {
      const itemType = row.querySelector('.row-item-type').value;
      const serviceType = row.querySelector('.row-service-type').value;
      const qtyRaw = Number(row.querySelector('.row-qty').value || 0);
      const qty = qtyRaw > 0 ? Math.floor(qtyRaw) : 0;
      const unitPrice = state.priceMap[itemType + '|' + serviceType] || 0;
      const lineTotal = roundMoney(unitPrice * qty);

      subtotal = roundMoney(subtotal + lineTotal);
      totalCount += qty;

      row.querySelector('.item-unit-price').textContent = '@ ' + Api.formatMoney(unitPrice, state.currencySymbol);
      row.querySelector('.item-line-total').textContent = Api.formatMoney(lineTotal, state.currencySymbol);
    });

    const discountRaw = Number(document.getElementById('orderDiscount').value || 0);
    const discount = discountRaw > 0 ? roundMoney(discountRaw) : 0;
    const taxAmount = roundMoney(subtotal * state.taxRatePercent / 100);
    const gross = roundMoney(subtotal + taxAmount);
    const grandTotal = roundMoney(Math.max(0, gross - discount));

    document.getElementById('summaryItemCount').textContent = String(totalCount);
    document.getElementById('summarySubtotal').textContent = Api.formatMoney(subtotal, state.currencySymbol);
    document.getElementById('summaryTax').textContent = Api.formatMoney(taxAmount, state.currencySymbol);
    document.getElementById('summaryDiscount').textContent = '-' + Api.formatMoney(discount, state.currencySymbol);
    document.getElementById('summaryTotal').textContent = Api.formatMoney(grandTotal, state.currencySymbol);
  }

  function showOrderError(msg) {
    const el = document.getElementById('orderFormError');
    if (el) {
      el.textContent = msg;
      el.hidden = false;
    }
  }

  function hideOrderError() {
    const el = document.getElementById('orderFormError');
    if (el) {
      el.textContent = '';
      el.hidden = true;
    }
  }

  async function handleSubmitOrder(e) {
    e.preventDefault();
    hideOrderError();

    const customerId = Number(document.getElementById('orderCustomerSelect').value);
    if (!customerId || customerId <= 0) {
      showOrderError('Please select a customer before submitting.');
      return;
    }

    const rows = document.querySelectorAll('#orderItemsContainer .order-item-row');
    if (!rows.length) {
      showOrderError('Add at least one garment item.');
      return;
    }

    const items = [];
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i];
      const itemType = row.querySelector('.row-item-type').value;
      const serviceType = row.querySelector('.row-service-type').value;
      const quantity = Number(row.querySelector('.row-qty').value);
      const specialInstructions = row.querySelector('.row-instructions').value.trim();

      if (!Number.isInteger(quantity) || quantity <= 0) {
        showOrderError('Every garment row must have a whole-number quantity greater than 0.');
        return;
      }
      items.push({
        itemType: itemType,
        serviceType: serviceType,
        quantity: quantity,
        specialInstructions: specialInstructions
      });
    }

    const discount = Number(document.getElementById('orderDiscount').value || 0);
    if (discount < 0) {
      showOrderError('Discount cannot be negative.');
      return;
    }

    const notes = document.getElementById('orderNotes').value.trim();
    const submitBtn = document.getElementById('submitOrderBtn');
    Api.setButtonLoading(submitBtn, true, 'Creating Order...');

    try {
      const created = await Api.request('/api/orders', {
        method: 'POST',
        body: {
          customerId: customerId,
          notes: notes,
          discount: discount,
          items: items
        }
      });

      const order = created.order;
      const invoice = created.invoice;
      Api.showToast(
        'Order #' + order.orderId + ' enqueued (' + invoice.invoiceNumber + ').',
        'success'
      );

      document.getElementById('orderNotes').value = '';
      document.getElementById('orderDiscount').value = '0.00';
      const container = document.getElementById('orderItemsContainer');
      container.innerHTML = '';
      addOrderRow();

      if (global.LaundryApp) {
        await global.LaundryApp.switchTab('queue');
      }
    } catch (err) {
      showOrderError(err.message);
      Api.showToast(err.message, 'error');
    } finally {
      Api.setButtonLoading(submitBtn, false);
    }
  }

  global.LaundryOrders = {
    init: initOrdersModule,
    load: loadNewOrderContext,
    refreshCustomersDropdown: refreshCustomersDropdown
  };
})(window);
