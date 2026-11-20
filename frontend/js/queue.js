'use strict';

(function (global) {
  const Api = global.LaundryApi;

  const STAGES = ['QUEUED', 'WASHING', 'IRONING', 'READY_FOR_PICKUP'];
  const state = {
    refreshTimer: null,
    pendingCancelOrderId: null,
    warningOrderId: null
  };

  function initQueueModule() {
    const processNextBtn = document.getElementById('processNextBtn');
    const refreshBtn = document.getElementById('refreshQueueBtn');
    const autoToggle = document.getElementById('queueAutoRefreshToggle');
    const board = document.getElementById('pipelineBoard');
    const dialog = document.getElementById('confirmCancelDialog');
    const dialogClose = document.getElementById('cancelDialogCloseBtn');
    const dialogConfirm = document.getElementById('cancelDialogConfirmBtn');
    const dismissWarningBtn = document.getElementById('dismissWarningBtn');
    const warningCollectBtn = document.getElementById('queueWarningCollectBtn');

    if (processNextBtn) {
      processNextBtn.addEventListener('click', handleProcessNext);
    }

    if (refreshBtn) {
      refreshBtn.addEventListener('click', function () {
        loadQueue();
      });
    }

    if (autoToggle) {
      autoToggle.addEventListener('change', syncAutoRefresh);
    }

    if (dismissWarningBtn) {
      dismissWarningBtn.addEventListener('click', function () {
        document.getElementById('queueUnpaidWarning').hidden = true;
      });
    }

    if (warningCollectBtn) {
      warningCollectBtn.addEventListener('click', function () {
        if (state.warningOrderId && global.LaundryApp) {
          global.LaundryApp.openInvoiceForOrder(state.warningOrderId);
        }
      });
    }

    if (board) {
      board.addEventListener('click', function (e) {
        const advBtn = e.target.closest('button[data-advance-id]');
        if (advBtn && !advBtn.disabled) {
          const orderId = Number(advBtn.getAttribute('data-advance-id'));
          handleAdvanceOrder(orderId, advBtn);
          return;
        }
        const cancelBtn = e.target.closest('button[data-cancel-id]');
        if (cancelBtn) {
          const orderId = Number(cancelBtn.getAttribute('data-cancel-id'));
          openCancelDialog(orderId);
        }
      });
    }

    if (dialogClose && dialog) {
      dialogClose.addEventListener('click', function () {
        state.pendingCancelOrderId = null;
        dialog.close();
      });
    }

    if (dialogConfirm && dialog) {
      dialogConfirm.addEventListener('click', async function () {
        if (!state.pendingCancelOrderId) {
          dialog.close();
          return;
        }
        const targetId = state.pendingCancelOrderId;
        Api.setButtonLoading(dialogConfirm, true, 'Cancelling...');
        try {
          await Api.request('/api/orders/' + targetId, { method: 'DELETE' });
          Api.showToast('Order #' + targetId + ' cancelled and removed from queue.', 'success');
          dialog.close();
          state.pendingCancelOrderId = null;
          await loadQueue();
        } catch (err) {
          Api.showToast(err.message, 'error');
        } finally {
          Api.setButtonLoading(dialogConfirm, false);
        }
      });
    }
  }

  function openCancelDialog(orderId) {
    const dialog = document.getElementById('confirmCancelDialog');
    const msg = document.getElementById('confirmCancelMessage');
    state.pendingCancelOrderId = orderId;
    if (msg) {
      msg.textContent = 'Cancel Order #' + orderId + '? This removes it from the active processing queue.';
    }
    if (dialog && typeof dialog.showModal === 'function') {
      dialog.showModal();
    }
  }

  async function loadQueue() {
    try {
      const snapshot = await Api.request('/api/queue');
      renderQueueBoard(snapshot || {});
    } catch (err) {
      Api.showToast('Failed to load queue: ' + err.message, 'error');
    }
  }

  function renderQueueBoard(snapshot) {
    STAGES.forEach(function (stage) {
      const cards = snapshot[stage] || [];
      const countEl = document.getElementById('count-' + stage);
      const colEl = document.getElementById('col-' + stage);
      if (countEl) {
        countEl.textContent = String(cards.length);
      }
      if (!colEl) {
        return;
      }

      if (!cards.length) {
        colEl.innerHTML = '<div class="empty-column">No orders in ' + Api.escapeHtml(Api.formatEnum(stage)) + '.</div>';
        return;
      }

      colEl.innerHTML = cards.map(function (card) {
        const isHead = Boolean(card.isHead);
        const actionLabel = stage === 'READY_FOR_PICKUP' ? 'Deliver' : 'Advance';
        const btnClass = stage === 'READY_FOR_PICKUP' ? 'btn-gold' : 'btn-primary';
        const disabledAttr = isHead ? '' : ' disabled title="Strict FIFO: process the head order (#1) first"';
        const payBadgeClass = card.paymentStatus === 'PAID' ? 'status-PAID' : 'status-UNPAID';

        return '<article class="queue-card' + (isHead ? ' is-head' : '') + '" data-order-id="' + Api.escapeHtml(card.orderId) + '">'
          + '<div class="queue-card-top">'
          + '  <span class="queue-position">#' + Api.escapeHtml(card.position) + ' · Order #' + Api.escapeHtml(card.orderId) + '</span>'
          + (isHead ? '  <span class="queue-head-tag">Head</span>' : '')
          + '</div>'
          + '<div class="queue-customer">' + Api.escapeHtml(card.customerName) + '</div>'
          + '<div class="queue-meta">'
          + '  <span class="tabular-nums">' + Api.escapeHtml(card.itemCount) + ' items</span>'
          + '  <span aria-hidden="true">·</span>'
          + '  <span class="tabular-nums">' + Api.escapeHtml(Api.formatMoney(card.totalAmount)) + '</span>'
          + '  <span aria-hidden="true">·</span>'
          + '  <span class="status-badge ' + payBadgeClass + '">' + Api.escapeHtml(Api.formatEnum(card.paymentStatus || 'UNPAID')) + '</span>'
          + '</div>'
          + '<div class="queue-meta">'
          + '  <span>Queued ' + Api.escapeHtml(Api.formatDateTime(card.createdAt)) + '</span>'
          + '</div>'
          + (card.notes ? '<div class="queue-notes">' + Api.escapeHtml(card.notes) + '</div>' : '')
          + '<div class="queue-card-actions">'
          + '  <button type="button" class="btn ' + btnClass + ' btn-sm btn-advance" data-advance-id="' + Api.escapeHtml(card.orderId) + '" aria-label="' + actionLabel + ' Order #' + Api.escapeHtml(card.orderId) + '"' + disabledAttr + '>'
          +      actionLabel
          + '  </button>'
          + '  <button type="button" class="btn btn-danger btn-sm" data-cancel-id="' + Api.escapeHtml(card.orderId) + '" aria-label="Cancel Order #' + Api.escapeHtml(card.orderId) + '">Cancel</button>'
          + '</div>'
          + '</article>';
      }).join('');
    });
  }

  async function handleProcessNext() {
    const btn = document.getElementById('processNextBtn');
    Api.setButtonLoading(btn, true, 'Processing...');
    try {
      const res = await Api.request('/api/queue/process-next', { method: 'POST' });
      Api.showToast(
        'Order #' + res.order.orderId + ' moved from Queued to Washing.',
        'success'
      );
      await loadQueue();
    } catch (err) {
      Api.showToast(err.message, 'error');
    } finally {
      Api.setButtonLoading(btn, false);
    }
  }

  async function handleAdvanceOrder(orderId, btnEl) {
    Api.setButtonLoading(btnEl, true, 'Updating...');
    try {
      const res = await Api.request('/api/queue/advance/' + orderId, { method: 'POST' });
      if (res.unpaidWarning) {
        state.warningOrderId = res.order.orderId;
        const banner = document.getElementById('queueUnpaidWarning');
        const textEl = document.getElementById('queueUnpaidWarningText');
        if (banner && textEl) {
          textEl.textContent = res.warningMessage || ('Order #' + orderId + ' was delivered with an UNPAID invoice.');
          banner.hidden = false;
        }
        Api.showToast(res.warningMessage || 'Delivered with UNPAID invoice.', 'warning');
      } else {
        Api.showToast(
          'Order #' + orderId + ' moved to ' + Api.formatEnum(res.newStatus) + '.',
          'success'
        );
      }
      await loadQueue();
    } catch (err) {
      Api.showToast(err.message, 'error');
      await loadQueue();
    } finally {
      Api.setButtonLoading(btnEl, false);
    }
  }

  function syncAutoRefresh() {
    stopAutoRefresh();
    const toggle = document.getElementById('queueAutoRefreshToggle');
    const queueSection = document.getElementById('view-queue');
    if (toggle && toggle.checked && queueSection && queueSection.classList.contains('is-active')) {
      state.refreshTimer = setInterval(loadQueue, 10000);
    }
  }

  function stopAutoRefresh() {
    if (state.refreshTimer) {
      clearInterval(state.refreshTimer);
      state.refreshTimer = null;
    }
  }

  global.LaundryQueueUI = {
    init: initQueueModule,
    load: function () {
      syncAutoRefresh();
      return loadQueue();
    },
    stopPolling: stopAutoRefresh
  };
})(window);
