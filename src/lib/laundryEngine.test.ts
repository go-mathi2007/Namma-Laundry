import test from 'node:test';
import assert from 'node:assert/strict';
import {
  calculateInvoiceTotals,
  DomainError,
  LaundryQueue,
  ProcessingPipeline,
} from './laundryEngine';

test('LaundryQueue preserves strict FIFO order and supports middle/tail removal', () => {
  const q = new LaundryQueue<number>();
  assert.equal(q.isEmpty(), true);
  q.enqueue(10);
  q.enqueue(11);
  q.enqueue(12);
  assert.equal(q.size(), 3);
  assert.equal(q.peek(), 10);
  assert.deepEqual(q.toList(), [10, 11, 12]);

  assert.equal(q.remove(11), true);
  assert.deepEqual(q.toList(), [10, 12]);
  assert.equal(q.remove(99), false);

  assert.equal(q.dequeue(), 10);
  assert.equal(q.dequeue(), 12);
  assert.equal(q.dequeue(), null);
  assert.equal(q.isEmpty(), true);
});

test('ProcessingPipeline enforces strict head-of-queue FIFO advancement across stages', () => {
  const pipeline = new ProcessingPipeline();
  pipeline.rebuildFromOrders([
    { orderId: 10, status: 'QUEUED' },
    { orderId: 11, status: 'QUEUED' },
    { orderId: 8, status: 'WASHING' },
  ]);

  // Advancing non-head order #11 in QUEUED must fail with 409
  assert.throws(
    () => pipeline.advanceOrder(11),
    (err: unknown) => err instanceof DomainError && err.statusCode === 409,
  );

  // Process next dequeues head of QUEUED (#10) into WASHING tail
  const first = pipeline.advanceStage('QUEUED');
  assert.equal(first.orderId, 10);
  assert.equal(first.oldStatus, 'QUEUED');
  assert.equal(first.newStatus, 'WASHING');

  assert.deepEqual(pipeline.getStageLists().QUEUED, [11]);
  assert.deepEqual(pipeline.getStageLists().WASHING, [8, 10]);

  // Advancing #8 through remaining stages reaches DELIVERED
  assert.equal(pipeline.advanceOrder(8).newStatus, 'IRONING');
  assert.equal(pipeline.advanceOrder(8).newStatus, 'READY_FOR_PICKUP');
  assert.equal(pipeline.advanceOrder(8).newStatus, 'DELIVERED');
  assert.equal(pipeline.findStageContaining(8), null);
});

test('calculateInvoiceTotals computes subtotal, tax, and discount accurately', () => {
  const result = calculateInvoiceTotals([120, 220], 18, 0);
  assert.equal(result.subtotal, 340);
  assert.equal(result.taxAmount, 61.2);
  assert.equal(result.totalAmount, 401.2);

  assert.throws(
    () => calculateInvoiceTotals([100], 18, 150),
    (err: unknown) => err instanceof DomainError && err.statusCode === 400,
  );
});
