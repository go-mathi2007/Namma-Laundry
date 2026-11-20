export type ItemType =
  | 'SHIRT'
  | 'TROUSERS'
  | 'SAREE'
  | 'JACKET'
  | 'BEDSHEET'
  | 'TOWEL'
  | 'DELICATE'
  | 'BLANKET';

export type ServiceType = 'WASH_FOLD' | 'DRY_CLEAN' | 'STEAM_PRESS';

export type OrderStatus =
  | 'QUEUED'
  | 'WASHING'
  | 'IRONING'
  | 'READY_FOR_PICKUP'
  | 'DELIVERED'
  | 'CANCELLED';

export type ActiveStage = 'QUEUED' | 'WASHING' | 'IRONING' | 'READY_FOR_PICKUP';

export type PaymentStatus = 'UNPAID' | 'PAID';
export type PaymentMethod = 'CASH' | 'CARD' | 'UPI';

export interface CustomerRecord {
  customerId: number;
  name: string;
  phone: string;
  email: string | null;
  address: string | null;
  createdAt: string;
}

export interface PriceEntryRecord {
  priceId: number;
  itemType: ItemType;
  serviceType: ServiceType;
  unitPrice: number;
}

export interface OrderItemRecord {
  itemId: number;
  orderId: number;
  itemType: ItemType;
  serviceType: ServiceType;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  specialInstructions: string | null;
}

export interface InvoiceRecord {
  invoiceId: number;
  orderId: number;
  invoiceNumber: string;
  subtotal: number;
  taxRate: number;
  taxAmount: number;
  discount: number;
  totalAmount: number;
  paymentStatus: PaymentStatus;
  paymentMethod: PaymentMethod | null;
  paidAt: string | null;
  createdAt: string;
}

export interface OrderRecord {
  orderId: number;
  customerId: number;
  status: OrderStatus;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  deliveredAt: string | null;
}

export interface StatusHistoryRecord {
  historyId: number;
  orderId: number;
  oldStatus: string | null;
  newStatus: string;
  changedAt: string;
}

export class DomainError extends Error {
  readonly statusCode: number;
  constructor(statusCode: number, message: string) {
    super(message);
    this.statusCode = statusCode;
  }
}

interface QueueNode<T> {
  data: T;
  next: QueueNode<T> | null;
}

/**
 * Hand-written generic FIFO queue backed by a singly linked list with head and tail pointers.
 */
export class LaundryQueue<T> {
  private head: QueueNode<T> | null = null;
  private tail: QueueNode<T> | null = null;
  private count = 0;

  enqueue(item: T): void {
    if (item === null || item === undefined) {
      throw new Error('Cannot enqueue null or undefined');
    }
    const node: QueueNode<T> = { data: item, next: null };
    if (!this.tail) {
      this.head = node;
      this.tail = node;
    } else {
      this.tail.next = node;
      this.tail = node;
    }
    this.count += 1;
  }

  dequeue(): T | null {
    if (!this.head) {
      return null;
    }
    const val = this.head.data;
    this.head = this.head.next;
    if (!this.head) {
      this.tail = null;
    }
    this.count -= 1;
    return val;
  }

  peek(): T | null {
    return this.head ? this.head.data : null;
  }

  isEmpty(): boolean {
    return this.count === 0;
  }

  size(): number {
    return this.count;
  }

  toList(): T[] {
    const out: T[] = [];
    let curr = this.head;
    while (curr) {
      out.push(curr.data);
      curr = curr.next;
    }
    return out;
  }

  contains(id: T): boolean {
    let curr = this.head;
    while (curr) {
      if (curr.data === id) {
        return true;
      }
      curr = curr.next;
    }
    return false;
  }

  remove(id: T): boolean {
    if (!this.head) {
      return false;
    }
    if (this.head.data === id) {
      this.dequeue();
      return true;
    }
    let prev = this.head;
    let curr = this.head.next;
    while (curr) {
      if (curr.data === id) {
        prev.next = curr.next;
        if (curr === this.tail) {
          this.tail = prev;
        }
        this.count -= 1;
        return true;
      }
      prev = curr;
      curr = curr.next;
    }
    return false;
  }

  clear(): void {
    this.head = null;
    this.tail = null;
    this.count = 0;
  }
}

export class ProcessingPipeline {
  readonly queuedQ = new LaundryQueue<number>();
  readonly washingQ = new LaundryQueue<number>();
  readonly ironingQ = new LaundryQueue<number>();
  readonly readyQ = new LaundryQueue<number>();

  rebuildFromOrders(activeOrders: Pick<OrderRecord, 'orderId' | 'status'>[]): void {
    this.queuedQ.clear();
    this.washingQ.clear();
    this.ironingQ.clear();
    this.readyQ.clear();
    for (const order of activeOrders) {
      const target = this.queueForStatus(order.status);
      if (target) {
        target.enqueue(order.orderId);
      }
    }
  }

  enqueueNewOrder(orderId: number): void {
    this.queuedQ.enqueue(orderId);
  }

  advanceStage(stage: ActiveStage): { orderId: number; oldStatus: ActiveStage; newStatus: OrderStatus } {
    const source = this.queueForStatus(stage);
    if (!source) {
      throw new DomainError(400, `Invalid processing stage: ${stage}`);
    }
    const headId = source.peek();
    if (headId === null) {
      throw new DomainError(409, `No orders waiting in stage ${stage}`);
    }
    const nextStatus = ProcessingPipeline.nextStatusFor(stage);
    source.dequeue();
    const target = this.queueForStatus(nextStatus);
    if (target) {
      target.enqueue(headId);
    }
    return { orderId: headId, oldStatus: stage, newStatus: nextStatus };
  }

  advanceOrder(orderId: number): { orderId: number; oldStatus: ActiveStage; newStatus: OrderStatus } {
    const stage = this.findStageContaining(orderId);
    if (!stage) {
      throw new DomainError(404, `Order #${orderId} is not in any active processing queue`);
    }
    const source = this.queueForStatus(stage)!;
    const headId = source.peek();
    if (headId !== orderId) {
      throw new DomainError(
        409,
        `Strict FIFO enforced: Order #${headId} is at the head of ${stage} and must be processed before Order #${orderId}.`,
      );
    }
    return this.advanceStage(stage);
  }

  cancelOrder(orderId: number): boolean {
    return (
      this.queuedQ.remove(orderId) ||
      this.washingQ.remove(orderId) ||
      this.ironingQ.remove(orderId) ||
      this.readyQ.remove(orderId)
    );
  }

  findStageContaining(orderId: number): ActiveStage | null {
    if (this.queuedQ.contains(orderId)) return 'QUEUED';
    if (this.washingQ.contains(orderId)) return 'WASHING';
    if (this.ironingQ.contains(orderId)) return 'IRONING';
    if (this.readyQ.contains(orderId)) return 'READY_FOR_PICKUP';
    return null;
  }

  getStageLists(): Record<ActiveStage, number[]> {
    return {
      QUEUED: this.queuedQ.toList(),
      WASHING: this.washingQ.toList(),
      IRONING: this.ironingQ.toList(),
      READY_FOR_PICKUP: this.readyQ.toList(),
    };
  }

  private queueForStatus(status: OrderStatus): LaundryQueue<number> | null {
    switch (status) {
      case 'QUEUED':
        return this.queuedQ;
      case 'WASHING':
        return this.washingQ;
      case 'IRONING':
        return this.ironingQ;
      case 'READY_FOR_PICKUP':
        return this.readyQ;
      default:
        return null;
    }
  }

  static nextStatusFor(status: ActiveStage): OrderStatus {
    switch (status) {
      case 'QUEUED':
        return 'WASHING';
      case 'WASHING':
        return 'IRONING';
      case 'IRONING':
        return 'READY_FOR_PICKUP';
      case 'READY_FOR_PICKUP':
        return 'DELIVERED';
    }
  }
}

export function roundMoney(val: number): number {
  return Math.round((val + Number.EPSILON) * 100) / 100;
}

export function calculateInvoiceTotals(
  lineTotals: number[],
  taxRatePercent: number,
  discount: number,
): { subtotal: number; taxAmount: number; totalAmount: number } {
  const subtotal = roundMoney(lineTotals.reduce((acc, v) => acc + v, 0));
  const taxAmount = roundMoney((subtotal * taxRatePercent) / 100);
  const gross = roundMoney(subtotal + taxAmount);
  if (discount < 0) {
    throw new DomainError(400, 'Discount cannot be negative');
  }
  if (discount > gross) {
    throw new DomainError(400, `Discount cannot exceed order gross total (${gross.toFixed(2)})`);
  }
  const totalAmount = roundMoney(gross - discount);
  return { subtotal, taxAmount, totalAmount };
}
