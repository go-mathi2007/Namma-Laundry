package com.laundry.structure;

import com.laundry.model.LaundryOrder;
import com.laundry.util.HttpUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coordinates the four active FIFO processing stage queues:
 * QUEUED -> WASHING -> IRONING -> READY_FOR_PICKUP -> DELIVERED.
 * Enforces strict head-of-queue advancement and supports atomic rollback
 * of in-memory queue state if the backing JDBC transaction fails.
 */
public class ProcessingPipeline {

    private final LaundryQueue<Long> queuedQ = new LaundryQueue<>();
    private final LaundryQueue<Long> washingQ = new LaundryQueue<>();
    private final LaundryQueue<Long> ironingQ = new LaundryQueue<>();
    private final LaundryQueue<Long> readyQ = new LaundryQueue<>();

    /**
     * Rebuilds all four queues from active DB orders ordered by created_at ASC
     * so FIFO order survives server restarts.
     */
    public synchronized void rebuildFromOrders(List<LaundryOrder> activeOrders) {
        queuedQ.clear();
        washingQ.clear();
        ironingQ.clear();
        readyQ.clear();
        if (activeOrders == null) {
            return;
        }
        for (LaundryOrder order : activeOrders) {
            LaundryQueue<Long> target = queueForStatus(order.getStatus());
            if (target != null) {
                target.enqueue(order.getOrderId());
            }
        }
    }

    public synchronized void enqueueNewOrder(long orderId) {
        queuedQ.enqueue(orderId);
    }

    /**
     * Captures a full rollback snapshot of all four queues.
     */
    public synchronized PipelineSnapshot captureSnapshot() {
        return new PipelineSnapshot(
                queuedQ.toList(),
                washingQ.toList(),
                ironingQ.toList(),
                readyQ.toList()
        );
    }

    /**
     * Restores all four queues to an earlier snapshot after a failed DB commit.
     */
    public synchronized void restoreSnapshot(PipelineSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        restoreSingleQueue(queuedQ, snapshot.queued);
        restoreSingleQueue(washingQ, snapshot.washing);
        restoreSingleQueue(ironingQ, snapshot.ironing);
        restoreSingleQueue(readyQ, snapshot.ready);
    }

    private void restoreSingleQueue(LaundryQueue<Long> queue, List<Long> items) {
        queue.clear();
        for (Long id : items) {
            queue.enqueue(id);
        }
    }

    /**
     * Dequeues the head of the given stage queue and enqueues it into the next stage.
     *
     * @return TransitionResult describing orderId, oldStatus, and newStatus
     */
    public synchronized TransitionResult advanceStage(String stage) {
        if (stage == null || stage.isBlank()) {
            throw new HttpUtil.ApiException(400, "Stage is required");
        }
        String normalized = stage.trim().toUpperCase();
        LaundryQueue<Long> source = queueForStatus(normalized);
        if (source == null) {
            throw new HttpUtil.ApiException(400, "Invalid processing stage: " + stage);
        }
        Long headId = source.peek();
        if (headId == null) {
            throw new HttpUtil.ApiException(409, "No orders waiting in stage " + normalized);
        }
        String nextStatus = nextStatusFor(normalized);
        source.dequeue();
        LaundryQueue<Long> target = queueForStatus(nextStatus);
        if (target != null) {
            target.enqueue(headId);
        }
        return new TransitionResult(headId, normalized, nextStatus);
    }

    /**
     * Strict FIFO advancement for a specific orderId: only succeeds if orderId
     * is currently at the head of whichever stage queue holds it.
     */
    public synchronized TransitionResult advanceOrder(long orderId) {
        String stage = findStageContaining(orderId);
        if (stage == null) {
            throw new HttpUtil.ApiException(404, "Order #" + orderId + " is not in any active processing queue");
        }
        LaundryQueue<Long> source = queueForStatus(stage);
        Long headId = source.peek();
        if (headId == null || headId.longValue() != orderId) {
            throw new HttpUtil.ApiException(
                    409,
                    "Strict FIFO enforced: Order #" + headId + " is at the head of " + stage
                            + " and must be processed before Order #" + orderId + "."
            );
        }
        return advanceStage(stage);
    }

    /**
     * Removes an order from whichever active queue holds it (for cancellations).
     */
    public synchronized boolean cancelOrder(long orderId) {
        return queuedQ.remove(orderId)
                || washingQ.remove(orderId)
                || ironingQ.remove(orderId)
                || readyQ.remove(orderId);
    }

    public synchronized String findStageContaining(long orderId) {
        if (queuedQ.contains(orderId)) return "QUEUED";
        if (washingQ.contains(orderId)) return "WASHING";
        if (ironingQ.contains(orderId)) return "IRONING";
        if (readyQ.contains(orderId)) return "READY_FOR_PICKUP";
        return null;
    }

    public synchronized Map<String, List<Long>> getStageLists() {
        Map<String, List<Long>> result = new LinkedHashMap<>();
        result.put("QUEUED", queuedQ.toList());
        result.put("WASHING", washingQ.toList());
        result.put("IRONING", ironingQ.toList());
        result.put("READY_FOR_PICKUP", readyQ.toList());
        return result;
    }

    private LaundryQueue<Long> queueForStatus(String status) {
        if (status == null) return null;
        switch (status) {
            case "QUEUED":
                return queuedQ;
            case "WASHING":
                return washingQ;
            case "IRONING":
                return ironingQ;
            case "READY_FOR_PICKUP":
                return readyQ;
            default:
                return null;
        }
    }

    public static String nextStatusFor(String currentStatus) {
        switch (currentStatus) {
            case "QUEUED":
                return "WASHING";
            case "WASHING":
                return "IRONING";
            case "IRONING":
                return "READY_FOR_PICKUP";
            case "READY_FOR_PICKUP":
                return "DELIVERED";
            default:
                throw new HttpUtil.ApiException(409, "Cannot advance from status: " + currentStatus);
        }
    }

    public static final class TransitionResult {
        private final long orderId;
        private final String oldStatus;
        private final String newStatus;

        public TransitionResult(long orderId, String oldStatus, String newStatus) {
            this.orderId = orderId;
            this.oldStatus = oldStatus;
            this.newStatus = newStatus;
        }

        public long getOrderId() {
            return orderId;
        }

        public String getOldStatus() {
            return oldStatus;
        }

        public String getNewStatus() {
            return newStatus;
        }
    }

    public static final class PipelineSnapshot {
        private final List<Long> queued;
        private final List<Long> washing;
        private final List<Long> ironing;
        private final List<Long> ready;

        PipelineSnapshot(List<Long> queued, List<Long> washing, List<Long> ironing, List<Long> ready) {
            this.queued = queued;
            this.washing = washing;
            this.ironing = ironing;
            this.ready = ready;
        }
    }
}
