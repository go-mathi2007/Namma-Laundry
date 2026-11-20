package com.laundry.service;

import com.laundry.config.ConnectionPool;
import com.laundry.dao.InvoiceDao;
import com.laundry.dao.OrderDao;
import com.laundry.model.Invoice;
import com.laundry.model.LaundryOrder;
import com.laundry.structure.ProcessingPipeline;
import com.laundry.util.HttpUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinates FIFO ProcessingPipeline operations with transactional JDBC updates.
 */
public class QueueService {

    private static final Logger LOGGER = Logger.getLogger(QueueService.class.getName());

    private final ConnectionPool pool;
    private final OrderDao orderDao;
    private final InvoiceDao invoiceDao;
    private final ProcessingPipeline pipeline;

    public QueueService(
            ConnectionPool pool,
            OrderDao orderDao,
            InvoiceDao invoiceDao,
            ProcessingPipeline pipeline
    ) {
        this.pool = pool;
        this.orderDao = orderDao;
        this.invoiceDao = invoiceDao;
        this.pipeline = pipeline;
    }

    /**
     * Rebuilds all four FIFO queues from active database orders ordered by created_at ASC.
     */
    public void rebuildQueuesOnStartup() throws SQLException {
        List<LaundryOrder> activeOrders = orderDao.findActiveOrdersFifo();
        pipeline.rebuildFromOrders(activeOrders);
        LOGGER.info("Rebuilt processing pipeline with " + activeOrders.size() + " active orders");
    }

    /**
     * Dequeues the head of QUEUED and advances it into WASHING.
     */
    public Map<String, Object> processNext() throws SQLException {
        synchronized (pipeline) {
            ProcessingPipeline.PipelineSnapshot snapshot = pipeline.captureSnapshot();
            ProcessingPipeline.TransitionResult transition = pipeline.advanceStage("QUEUED");
            return commitTransition(transition, snapshot);
        }
    }

    /**
     * Advances a specific order if and only if it is at the head of its stage queue.
     */
    public Map<String, Object> advanceOrder(long orderId) throws SQLException {
        synchronized (pipeline) {
            LaundryOrder existing = orderDao.findById(orderId);
            if (existing == null) {
                throw new HttpUtil.ApiException(404, "Order #" + orderId + " not found");
            }
            if ("DELIVERED".equals(existing.getStatus()) || "CANCELLED".equals(existing.getStatus())) {
                throw new HttpUtil.ApiException(409, "Order #" + orderId + " is already " + existing.getStatus());
            }

            ProcessingPipeline.PipelineSnapshot snapshot = pipeline.captureSnapshot();
            ProcessingPipeline.TransitionResult transition = pipeline.advanceOrder(orderId);
            return commitTransition(transition, snapshot);
        }
    }

    private Map<String, Object> commitTransition(
            ProcessingPipeline.TransitionResult transition,
            ProcessingPipeline.PipelineSnapshot snapshot
    ) throws SQLException {
        long orderId = transition.getOrderId();
        boolean markDelivered = "DELIVERED".equals(transition.getNewStatus());

        try (Connection conn = pool.borrowConnection()) {
            boolean prevAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                orderDao.updateStatus(conn, orderId, transition.getNewStatus(), markDelivered);
                orderDao.insertStatusHistory(conn, orderId, transition.getOldStatus(), transition.getNewStatus());
                LaundryOrder updated = orderDao.findById(conn, orderId);
                Invoice invoice = invoiceDao.findByOrderId(conn, orderId);
                conn.commit();

                boolean unpaidWarning = markDelivered
                        && invoice != null
                        && !"PAID".equalsIgnoreCase(invoice.getPaymentStatus());

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("order", updated.toMap(false));
                result.put("oldStatus", transition.getOldStatus());
                result.put("newStatus", transition.getNewStatus());
                result.put("unpaidWarning", unpaidWarning);
                if (unpaidWarning) {
                    result.put("warningMessage",
                            "Order #" + orderId + " was delivered with an UNPAID invoice ("
                                    + invoice.getInvoiceNumber() + "). Please collect payment.");
                }
                return result;
            } catch (SQLException ex) {
                pipeline.restoreSnapshot(snapshot);
                try {
                    conn.rollback();
                } catch (SQLException rbEx) {
                    LOGGER.log(Level.SEVERE, "Rollback failed during queue transition", rbEx);
                }
                throw ex;
            } finally {
                try {
                    conn.setAutoCommit(prevAuto);
                } catch (SQLException acEx) {
                    LOGGER.log(Level.WARNING, "Failed to restore autoCommit", acEx);
                }
            }
        }
    }

    /**
     * Returns a snapshot of all four FIFO queues enriched with order metadata and 1-based position.
     */
    public Map<String, Object> getQueueSnapshot() throws SQLException {
        Map<String, List<Long>> stageLists;
        synchronized (pipeline) {
            stageLists = pipeline.getStageLists();
        }

        List<LaundryOrder> activeOrders = orderDao.findActiveOrdersFifo();
        Map<Long, LaundryOrder> orderById = new LinkedHashMap<>();
        for (LaundryOrder o : activeOrders) {
            orderById.put(o.getOrderId(), o);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, List<Long>> entry : stageLists.entrySet()) {
            List<Long> ids = entry.getValue();
            List<Map<String, Object>> cards = new ArrayList<>(ids.size());
            for (int i = 0; i < ids.size(); i++) {
                Long id = ids.get(i);
                LaundryOrder order = orderById.get(id);
                if (order == null) {
                    order = orderDao.findById(id);
                }
                if (order == null) {
                    continue;
                }
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("position", i + 1);
                card.put("isHead", i == 0);
                card.put("orderId", order.getOrderId());
                card.put("customerId", order.getCustomerId());
                card.put("customerName", order.getCustomerName());
                card.put("customerPhone", order.getCustomerPhone());
                card.put("itemCount", order.getItemCount());
                card.put("totalAmount", order.getTotalAmount());
                card.put("paymentStatus", order.getPaymentStatus());
                card.put("invoiceNumber", order.getInvoiceNumber());
                card.put("status", order.getStatus());
                card.put("notes", order.getNotes());
                card.put("createdAt", order.getCreatedAt());
                cards.add(card);
            }
            snapshot.put(entry.getKey(), cards);
        }
        return snapshot;
    }
}
