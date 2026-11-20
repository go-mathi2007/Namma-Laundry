import express, { Request, Response } from 'express';
import fs from 'fs';
import path from 'path';
import { createServer as createViteServer } from 'vite';
import {
  ActiveStage,
  calculateInvoiceTotals,
  CustomerRecord,
  DomainError,
  InvoiceRecord,
  ItemType,
  OrderItemRecord,
  OrderRecord,
  OrderStatus,
  PaymentMethod,
  PriceEntryRecord,
  ProcessingPipeline,
  roundMoney,
  ServiceType,
  StatusHistoryRecord,
} from './src/lib/laundryEngine';

const SETTINGS = {
  TAX_RATE_PERCENT: '18',
  CURRENCY_SYMBOL: '₹',
  SHOP_NAME: 'Namma Laundry & Dry Care',
  SHOP_ADDRESS: '42 Anna Salai, Teynampet, Chennai 600018',
};

const VALID_ITEM_TYPES = new Set<ItemType>([
  'SHIRT',
  'TROUSERS',
  'SAREE',
  'JACKET',
  'BEDSHEET',
  'TOWEL',
  'DELICATE',
  'BLANKET',
]);

const VALID_SERVICE_TYPES = new Set<ServiceType>(['WASH_FOLD', 'DRY_CLEAN', 'STEAM_PRESS']);
const VALID_STATUSES = new Set<OrderStatus>([
  'QUEUED',
  'WASHING',
  'IRONING',
  'READY_FOR_PICKUP',
  'DELIVERED',
  'CANCELLED',
]);
const VALID_PAYMENT_METHODS = new Set<PaymentMethod>(['CASH', 'CARD', 'UPI']);

const PRICES: PriceEntryRecord[] = [
  { priceId: 1, itemType: 'SHIRT', serviceType: 'WASH_FOLD', unitPrice: 40 },
  { priceId: 2, itemType: 'SHIRT', serviceType: 'DRY_CLEAN', unitPrice: 90 },
  { priceId: 3, itemType: 'SHIRT', serviceType: 'STEAM_PRESS', unitPrice: 25 },
  { priceId: 4, itemType: 'TROUSERS', serviceType: 'WASH_FOLD', unitPrice: 50 },
  { priceId: 5, itemType: 'TROUSERS', serviceType: 'DRY_CLEAN', unitPrice: 110 },
  { priceId: 6, itemType: 'TROUSERS', serviceType: 'STEAM_PRESS', unitPrice: 30 },
  { priceId: 7, itemType: 'SAREE', serviceType: 'WASH_FOLD', unitPrice: 120 },
  { priceId: 8, itemType: 'SAREE', serviceType: 'DRY_CLEAN', unitPrice: 260 },
  { priceId: 9, itemType: 'SAREE', serviceType: 'STEAM_PRESS', unitPrice: 80 },
  { priceId: 10, itemType: 'JACKET', serviceType: 'WASH_FOLD', unitPrice: 140 },
  { priceId: 11, itemType: 'JACKET', serviceType: 'DRY_CLEAN', unitPrice: 280 },
  { priceId: 12, itemType: 'JACKET', serviceType: 'STEAM_PRESS', unitPrice: 90 },
  { priceId: 13, itemType: 'BEDSHEET', serviceType: 'WASH_FOLD', unitPrice: 90 },
  { priceId: 14, itemType: 'BEDSHEET', serviceType: 'DRY_CLEAN', unitPrice: 180 },
  { priceId: 15, itemType: 'BEDSHEET', serviceType: 'STEAM_PRESS', unitPrice: 55 },
  { priceId: 16, itemType: 'TOWEL', serviceType: 'WASH_FOLD', unitPrice: 35 },
  { priceId: 17, itemType: 'TOWEL', serviceType: 'DRY_CLEAN', unitPrice: 75 },
  { priceId: 18, itemType: 'TOWEL', serviceType: 'STEAM_PRESS', unitPrice: 20 },
  { priceId: 19, itemType: 'DELICATE', serviceType: 'WASH_FOLD', unitPrice: 85 },
  { priceId: 20, itemType: 'DELICATE', serviceType: 'DRY_CLEAN', unitPrice: 190 },
  { priceId: 21, itemType: 'DELICATE', serviceType: 'STEAM_PRESS', unitPrice: 60 },
  { priceId: 22, itemType: 'BLANKET', serviceType: 'WASH_FOLD', unitPrice: 220 },
  { priceId: 23, itemType: 'BLANKET', serviceType: 'DRY_CLEAN', unitPrice: 380 },
  { priceId: 24, itemType: 'BLANKET', serviceType: 'STEAM_PRESS', unitPrice: 120 },
];

function createSeedStore() {
  const customers: CustomerRecord[] = [
    { customerId: 1, name: 'Aarav Sundaram', phone: '9840112233', email: 'aarav.sundaram@example.com', address: '14 Luz Church Rd, Mylapore, Chennai', createdAt: '2026-11-10T09:15:00Z' },
    { customerId: 2, name: 'Meera Krishnan', phone: '9840223344', email: 'meera.k@example.com', address: '28 Besant Avenue, Adyar, Chennai', createdAt: '2026-11-11T10:30:00Z' },
    { customerId: 3, name: 'Vikram Nair', phone: '9840334455', email: 'vikram.nair@example.com', address: '7 Boat Club Rd, RA Puram, Chennai', createdAt: '2026-11-12T11:45:00Z' },
    { customerId: 4, name: 'Priya Raman', phone: '9840445566', email: 'priya.raman@example.com', address: '102 Habibullah Rd, T. Nagar, Chennai', createdAt: '2026-11-13T14:20:00Z' },
    { customerId: 5, name: 'Karthik Raja', phone: '9840556677', email: 'karthik.raja@example.com', address: '55 2nd Main Rd, Anna Nagar, Chennai', createdAt: '2026-11-14T16:05:00Z' },
    { customerId: 6, name: 'Divya Subramanian', phone: '9840667788', email: 'divya.s@example.com', address: '19 Cathedral Rd, Gopalapuram, Chennai', createdAt: '2026-11-15T09:50:00Z' },
    { customerId: 7, name: 'Rohan Deshmukh', phone: '9840778899', email: 'rohan.d@example.com', address: '81 Harrington Rd, Chetpet, Chennai', createdAt: '2026-11-16T12:10:00Z' },
    { customerId: 8, name: 'Ananya Menon', phone: '9840889900', email: 'ananya.menon@example.com', address: '33 East Coast Rd, Thiruvanmiyur, Chennai', createdAt: '2026-11-17T15:40:00Z' },
  ];

  const orders: OrderRecord[] = [
    { orderId: 1, customerId: 1, status: 'DELIVERED', notes: 'Light starch on cotton shirts', createdAt: '2026-11-17T09:00:00Z', updatedAt: '2026-11-17T17:30:00Z', deliveredAt: '2026-11-17T17:30:00Z' },
    { orderId: 2, customerId: 2, status: 'DELIVERED', notes: 'Handle silk saree border gently', createdAt: '2026-11-17T10:15:00Z', updatedAt: '2026-11-18T11:00:00Z', deliveredAt: '2026-11-18T11:00:00Z' },
    { orderId: 3, customerId: 3, status: 'CANCELLED', notes: 'Customer rescheduled pickup', createdAt: '2026-11-17T11:30:00Z', updatedAt: '2026-11-17T12:10:00Z', deliveredAt: null },
    { orderId: 4, customerId: 4, status: 'READY_FOR_PICKUP', notes: 'Call before delivery', createdAt: '2026-11-18T09:10:00Z', updatedAt: '2026-11-19T10:00:00Z', deliveredAt: null },
    { orderId: 5, customerId: 5, status: 'READY_FOR_PICKUP', notes: 'Fold sweaters flat, no hangers', createdAt: '2026-11-18T10:25:00Z', updatedAt: '2026-11-19T11:15:00Z', deliveredAt: null },
    { orderId: 6, customerId: 6, status: 'IRONING', notes: 'Medium steam on linen trousers', createdAt: '2026-11-18T14:00:00Z', updatedAt: '2026-11-19T12:00:00Z', deliveredAt: null },
    { orderId: 7, customerId: 7, status: 'IRONING', notes: 'Pack shirts individually', createdAt: '2026-11-18T15:20:00Z', updatedAt: '2026-11-19T12:30:00Z', deliveredAt: null },
    { orderId: 8, customerId: 8, status: 'WASHING', notes: 'Use mild fragrance detergent', createdAt: '2026-11-19T08:30:00Z', updatedAt: '2026-11-19T13:00:00Z', deliveredAt: null },
    { orderId: 9, customerId: 1, status: 'WASHING', notes: 'Blanket cycle on gentle warm', createdAt: '2026-11-19T09:45:00Z', updatedAt: '2026-11-19T13:20:00Z', deliveredAt: null },
    { orderId: 10, customerId: 2, status: 'QUEUED', notes: 'Check collar cuff stain on white shirt', createdAt: '2026-11-19T14:10:00Z', updatedAt: '2026-11-19T14:10:00Z', deliveredAt: null },
    { orderId: 11, customerId: 3, status: 'QUEUED', notes: 'Express fold requested', createdAt: '2026-11-19T14:45:00Z', updatedAt: '2026-11-19T14:45:00Z', deliveredAt: null },
    { orderId: 12, customerId: 4, status: 'QUEUED', notes: 'Keep dupatta wrapped in tissue', createdAt: '2026-11-19T15:30:00Z', updatedAt: '2026-11-19T15:30:00Z', deliveredAt: null },
  ];

  const orderItems: OrderItemRecord[] = [
    { itemId: 1, orderId: 1, itemType: 'SHIRT', serviceType: 'WASH_FOLD', quantity: 4, unitPrice: 40, lineTotal: 160, specialInstructions: 'Light starch' },
    { itemId: 2, orderId: 1, itemType: 'TROUSERS', serviceType: 'WASH_FOLD', quantity: 2, unitPrice: 50, lineTotal: 100, specialInstructions: 'Standard fold' },
    { itemId: 3, orderId: 2, itemType: 'SAREE', serviceType: 'DRY_CLEAN', quantity: 2, unitPrice: 260, lineTotal: 520, specialInstructions: 'Zari care' },
    { itemId: 4, orderId: 2, itemType: 'DELICATE', serviceType: 'DRY_CLEAN', quantity: 1, unitPrice: 190, lineTotal: 190, specialInstructions: 'No high heat' },
    { itemId: 5, orderId: 3, itemType: 'SHIRT', serviceType: 'DRY_CLEAN', quantity: 2, unitPrice: 90, lineTotal: 180, specialInstructions: null },
    { itemId: 6, orderId: 4, itemType: 'SHIRT', serviceType: 'STEAM_PRESS', quantity: 3, unitPrice: 25, lineTotal: 75, specialInstructions: 'Hanger pack' },
    { itemId: 7, orderId: 4, itemType: 'BEDSHEET', serviceType: 'DRY_CLEAN', quantity: 2, unitPrice: 180, lineTotal: 360, specialInstructions: 'King size' },
    { itemId: 8, orderId: 5, itemType: 'JACKET', serviceType: 'DRY_CLEAN', quantity: 1, unitPrice: 280, lineTotal: 280, specialInstructions: 'Spot clean sleeves' },
    { itemId: 9, orderId: 5, itemType: 'TOWEL', serviceType: 'WASH_FOLD', quantity: 2, unitPrice: 35, lineTotal: 70, specialInstructions: 'Softener' },
    { itemId: 10, orderId: 6, itemType: 'TROUSERS', serviceType: 'STEAM_PRESS', quantity: 3, unitPrice: 30, lineTotal: 90, specialInstructions: 'Sharp crease' },
    { itemId: 11, orderId: 6, itemType: 'SHIRT', serviceType: 'WASH_FOLD', quantity: 2, unitPrice: 40, lineTotal: 80, specialInstructions: null },
    { itemId: 12, orderId: 7, itemType: 'SHIRT', serviceType: 'STEAM_PRESS', quantity: 5, unitPrice: 25, lineTotal: 125, specialInstructions: 'Individual sleeves' },
    { itemId: 13, orderId: 7, itemType: 'SAREE', serviceType: 'STEAM_PRESS', quantity: 1, unitPrice: 80, lineTotal: 80, specialInstructions: 'Box fold' },
    { itemId: 14, orderId: 8, itemType: 'BEDSHEET', serviceType: 'WASH_FOLD', quantity: 3, unitPrice: 90, lineTotal: 270, specialInstructions: 'Hot rinse' },
    { itemId: 15, orderId: 8, itemType: 'TOWEL', serviceType: 'WASH_FOLD', quantity: 4, unitPrice: 35, lineTotal: 140, specialInstructions: null },
    { itemId: 16, orderId: 9, itemType: 'BLANKET', serviceType: 'WASH_FOLD', quantity: 2, unitPrice: 220, lineTotal: 440, specialInstructions: 'Double woollen' },
    { itemId: 17, orderId: 10, itemType: 'SHIRT', serviceType: 'WASH_FOLD', quantity: 3, unitPrice: 40, lineTotal: 120, specialInstructions: 'Collar pre-treat' },
    { itemId: 18, orderId: 10, itemType: 'TROUSERS', serviceType: 'DRY_CLEAN', quantity: 2, unitPrice: 110, lineTotal: 220, specialInstructions: null },
    { itemId: 19, orderId: 11, itemType: 'BLANKET', serviceType: 'DRY_CLEAN', quantity: 1, unitPrice: 380, lineTotal: 380, specialInstructions: 'Comforter bag' },
    { itemId: 20, orderId: 11, itemType: 'DELICATE', serviceType: 'WASH_FOLD', quantity: 2, unitPrice: 85, lineTotal: 170, specialInstructions: 'Cold water' },
    { itemId: 21, orderId: 12, itemType: 'SAREE', serviceType: 'DRY_CLEAN', quantity: 1, unitPrice: 260, lineTotal: 260, specialInstructions: 'Tissue wrap' },
    { itemId: 22, orderId: 12, itemType: 'DELICATE', serviceType: 'STEAM_PRESS', quantity: 2, unitPrice: 60, lineTotal: 120, specialInstructions: 'Low steam' },
  ];

  const invoices: InvoiceRecord[] = [
    { invoiceId: 1, orderId: 1, invoiceNumber: 'INV-2026-0001', subtotal: 260, taxRate: 18, taxAmount: 46.8, discount: 0, totalAmount: 306.8, paymentStatus: 'PAID', paymentMethod: 'UPI', paidAt: '2026-11-17T17:28:00Z', createdAt: '2026-11-17T09:00:00Z' },
    { invoiceId: 2, orderId: 2, invoiceNumber: 'INV-2026-0002', subtotal: 710, taxRate: 18, taxAmount: 127.8, discount: 50, totalAmount: 787.8, paymentStatus: 'PAID', paymentMethod: 'CARD', paidAt: '2026-11-18T10:55:00Z', createdAt: '2026-11-17T10:15:00Z' },
    { invoiceId: 3, orderId: 3, invoiceNumber: 'INV-2026-0003', subtotal: 180, taxRate: 18, taxAmount: 32.4, discount: 0, totalAmount: 212.4, paymentStatus: 'UNPAID', paymentMethod: null, paidAt: null, createdAt: '2026-11-17T11:30:00Z' },
    { invoiceId: 4, orderId: 4, invoiceNumber: 'INV-2026-0004', subtotal: 435, taxRate: 18, taxAmount: 78.3, discount: 0, totalAmount: 513.3, paymentStatus: 'PAID', paymentMethod: 'CASH', paidAt: '2026-11-19T10:05:00Z', createdAt: '2026-11-18T09:10:00Z' },
    { invoiceId: 5, orderId: 5, invoiceNumber: 'INV-2026-0005', subtotal: 350, taxRate: 18, taxAmount: 63, discount: 0, totalAmount: 413, paymentStatus: 'UNPAID', paymentMethod: null, paidAt: null, createdAt: '2026-11-18T10:25:00Z' },
    { invoiceId: 6, orderId: 6, invoiceNumber: 'INV-2026-0006', subtotal: 170, taxRate: 18, taxAmount: 30.6, discount: 0, totalAmount: 200.6, paymentStatus: 'UNPAID', paymentMethod: null, paidAt: null, createdAt: '2026-11-18T14:00:00Z' },
    { invoiceId: 7, orderId: 7, invoiceNumber: 'INV-2026-0007', subtotal: 205, taxRate: 18, taxAmount: 36.9, discount: 0, totalAmount: 241.9, paymentStatus: 'PAID', paymentMethod: 'UPI', paidAt: '2026-11-18T15:25:00Z', createdAt: '2026-11-18T15:20:00Z' },
    { invoiceId: 8, orderId: 8, invoiceNumber: 'INV-2026-0008', subtotal: 410, taxRate: 18, taxAmount: 73.8, discount: 20, totalAmount: 463.8, paymentStatus: 'UNPAID', paymentMethod: null, paidAt: null, createdAt: '2026-11-19T08:30:00Z' },
    { invoiceId: 9, orderId: 9, invoiceNumber: 'INV-2026-0009', subtotal: 440, taxRate: 18, taxAmount: 79.2, discount: 0, totalAmount: 519.2, paymentStatus: 'UNPAID', paymentMethod: null, paidAt: null, createdAt: '2026-11-19T09:45:00Z' },
    { invoiceId: 10, orderId: 10, invoiceNumber: 'INV-2026-0010', subtotal: 340, taxRate: 18, taxAmount: 61.2, discount: 0, totalAmount: 401.2, paymentStatus: 'UNPAID', paymentMethod: null, paidAt: null, createdAt: '2026-11-19T14:10:00Z' },
    { invoiceId: 11, orderId: 11, invoiceNumber: 'INV-2026-0011', subtotal: 550, taxRate: 18, taxAmount: 99, discount: 0, totalAmount: 649, paymentStatus: 'PAID', paymentMethod: 'CARD', paidAt: '2026-11-19T14:46:00Z', createdAt: '2026-11-19T14:45:00Z' },
    { invoiceId: 12, orderId: 12, invoiceNumber: 'INV-2026-0012', subtotal: 380, taxRate: 18, taxAmount: 68.4, discount: 0, totalAmount: 448.4, paymentStatus: 'UNPAID', paymentMethod: null, paidAt: null, createdAt: '2026-11-19T15:30:00Z' },
  ];

  const history: StatusHistoryRecord[] = [];
  let histId = 1;
  for (const o of orders) {
    history.push({ historyId: histId++, orderId: o.orderId, oldStatus: null, newStatus: 'QUEUED', changedAt: o.createdAt });
    if (o.status !== 'QUEUED') {
      history.push({ historyId: histId++, orderId: o.orderId, oldStatus: 'QUEUED', newStatus: o.status, changedAt: o.updatedAt });
    }
  }

  const pipeline = new ProcessingPipeline();
  const activeOrders = orders
    .filter((o) => o.status !== 'DELIVERED' && o.status !== 'CANCELLED')
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.orderId - b.orderId);
  pipeline.rebuildFromOrders(activeOrders);

  return {
    customers,
    orders,
    orderItems,
    invoices,
    history,
    pipeline,
    nextCustomerId: 9,
    nextOrderId: 13,
    nextItemId: 23,
    nextInvoiceId: 13,
    nextHistoryId: histId,
  };
}

const store = createSeedStore();

function hydrateOrderSummary(order: OrderRecord) {
  const customer = store.customers.find((c) => c.customerId === order.customerId);
  const items = store.orderItems.filter((it) => it.orderId === order.orderId);
  const invoice = store.invoices.find((inv) => inv.orderId === order.orderId);
  const itemCount = items.reduce((sum, it) => sum + it.quantity, 0);

  return {
    orderId: order.orderId,
    customerId: order.customerId,
    customerName: customer ? customer.name : 'Unknown',
    customerPhone: customer ? customer.phone : '',
    status: order.status,
    notes: order.notes,
    createdAt: order.createdAt,
    updatedAt: order.updatedAt,
    deliveredAt: order.deliveredAt,
    itemCount,
    totalAmount: invoice ? invoice.totalAmount : 0,
    paymentStatus: invoice ? invoice.paymentStatus : 'UNPAID',
    invoiceNumber: invoice ? invoice.invoiceNumber : null,
  };
}

function sendSuccess(res: Response, statusCode: number, data: unknown) {
  res.status(statusCode).json({ success: true, data });
}

function sendError(res: Response, statusCode: number, message: string) {
  res.status(statusCode).json({ success: false, error: message });
}

function parseId(raw: string, label: string): number {
  const n = Number(raw);
  if (!Number.isInteger(n) || n <= 0) {
    throw new DomainError(400, `${label} must be a positive integer`);
  }
  return n;
}

async function startServer() {
  const app = express();
  app.use(express.json());

  app.use((req, res, next) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    if (req.method === 'OPTIONS') {
      res.status(204).end();
      return;
    }
    next();
  });

  // Customers
  app.get('/api/customers', (req: Request, res: Response) => {
    const search = typeof req.query.search === 'string' ? req.query.search.trim().toLowerCase() : '';
    const list = search
      ? store.customers.filter(
          (c) =>
            c.name.toLowerCase().includes(search) ||
            c.phone.includes(search) ||
            String(c.customerId) === search,
        )
      : [...store.customers];
    list.sort((a, b) => b.customerId - a.customerId);
    sendSuccess(res, 200, list);
  });

  app.post('/api/customers', (req: Request, res: Response) => {
    try {
      const name = typeof req.body?.name === 'string' ? req.body.name.trim() : '';
      if (!name) throw new DomainError(400, 'Customer name is required');
      const rawPhone = typeof req.body?.phone === 'string' ? req.body.phone.trim() : '';
      const phone = rawPhone.replace(/[\s()-]/g, '');
      if (!/^[+]?[0-9]{7,15}$/.test(phone)) {
        throw new DomainError(400, 'Phone number must contain 7 to 15 digits');
      }
      const email = typeof req.body?.email === 'string' && req.body.email.trim() ? req.body.email.trim() : null;
      if (email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
        throw new DomainError(400, 'Invalid email address format');
      }
      const address = typeof req.body?.address === 'string' && req.body.address.trim() ? req.body.address.trim() : null;

      if (store.customers.some((c) => c.phone === phone)) {
        throw new DomainError(409, `A customer with phone number ${phone} is already registered`);
      }

      const created: CustomerRecord = {
        customerId: store.nextCustomerId++,
        name,
        phone,
        email,
        address,
        createdAt: new Date().toISOString(),
      };
      store.customers.push(created);
      sendSuccess(res, 201, created);
    } catch (err) {
      if (err instanceof DomainError) return sendError(res, err.statusCode, err.message);
      sendError(res, 500, 'Internal server error');
    }
  });

  app.get('/api/customers/:id', (req: Request, res: Response) => {
    try {
      const id = parseId(req.params.id, 'customerId');
      const customer = store.customers.find((c) => c.customerId === id);
      if (!customer) throw new DomainError(404, `Customer #${id} not found`);
      sendSuccess(res, 200, customer);
    } catch (err) {
      if (err instanceof DomainError) return sendError(res, err.statusCode, err.message);
      sendError(res, 500, 'Internal server error');
    }
  });

  app.get('/api/customers/:id/orders', (req: Request, res: Response) => {
    try {
      const id = parseId(req.params.id, 'customerId');
      const customer = store.customers.find((c) => c.customerId === id);
      if (!customer) throw new DomainError(404, `Customer #${id} not found`);
      const orders = store.orders
        .filter((o) => o.customerId === id)
        .sort((a, b) => b.orderId - a.orderId)
        .map(hydrateOrderSummary);
      sendSuccess(res, 200, { customer, orders });
    } catch (err) {
      if (err instanceof DomainError) return sendError(res, err.statusCode, err.message);
      sendError(res, 500, 'Internal server error');
    }
  });

  // Prices
  app.get('/api/prices', (_req: Request, res: Response) => {
    sendSuccess(res, 200, {
      prices: PRICES,
      taxRatePercent: Number(SETTINGS.TAX_RATE_PERCENT),
      currencySymbol: SETTINGS.CURRENCY_SYMBOL,
      shopName: SETTINGS.SHOP_NAME,
      shopAddress: SETTINGS.SHOP_ADDRESS,
    });
  });

  // Orders
  app.get('/api/orders', (req: Request, res: Response) => {
    try {
      const status = typeof req.query.status === 'string' ? req.query.status.trim().toUpperCase() : '';
      if (status && !VALID_STATUSES.has(status as OrderStatus)) {
        throw new DomainError(400, `Invalid status filter: ${status}`);
      }
      const list = store.orders
        .filter((o) => !status || o.status === status)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.orderId - a.orderId)
        .map(hydrateOrderSummary);
      sendSuccess(res, 200, list);
    } catch (err) {
      if (err instanceof DomainError) return sendError(res, err.statusCode, err.message);
      sendError(res, 500, 'Internal server error');
    }
  });

  app.post('/api/orders', (req: Request, res: Response) => {
    try {
      const customerId = Number(req.body?.customerId);
      if (!Number.isInteger(customerId) || customerId <= 0) {
        throw new DomainError(400, 'customerId must be a positive integer');
      }
      if (!store.customers.some((c) => c.customerId === customerId)) {
        throw new DomainError(404, `Customer #${customerId} not found`);
      }

      const rawItems = req.body?.items;
      if (!Array.isArray(rawItems) || rawItems.length === 0) {
        throw new DomainError(400, 'Order must contain at least one garment item');
      }

      const discount = roundMoney(Number(req.body?.discount || 0));
      if (Number.isNaN(discount) || discount < 0) {
        throw new DomainError(400, 'Discount cannot be negative');
      }

      const notes = typeof req.body?.notes === 'string' && req.body.notes.trim() ? req.body.notes.trim() : null;
      const now = new Date().toISOString();
      const orderId = store.nextOrderId;

      const preparedItems: OrderItemRecord[] = [];
      for (const raw of rawItems) {
        const itemType = String(raw?.itemType || '').toUpperCase() as ItemType;
        const serviceType = String(raw?.serviceType || '').toUpperCase() as ServiceType;
        const quantity = Number(raw?.quantity);
        if (!VALID_ITEM_TYPES.has(itemType)) throw new DomainError(400, `Unsupported itemType: ${itemType}`);
        if (!VALID_SERVICE_TYPES.has(serviceType)) throw new DomainError(400, `Unsupported serviceType: ${serviceType}`);
        if (!Number.isInteger(quantity) || quantity <= 0) throw new DomainError(400, 'Quantity must be greater than 0');

        const price = PRICES.find((p) => p.itemType === itemType && p.serviceType === serviceType);
        if (!price) throw new DomainError(400, `No tariff configured for ${itemType} / ${serviceType}`);

        const lineTotal = roundMoney(price.unitPrice * quantity);
        preparedItems.push({
          itemId: 0,
          orderId,
          itemType,
          serviceType,
          quantity,
          unitPrice: price.unitPrice,
          lineTotal,
          specialInstructions:
            typeof raw?.specialInstructions === 'string' && raw.specialInstructions.trim()
              ? raw.specialInstructions.trim()
              : null,
        });
      }

      const taxRate = Number(SETTINGS.TAX_RATE_PERCENT);
      const { subtotal, taxAmount, totalAmount } = calculateInvoiceTotals(
        preparedItems.map((it) => it.lineTotal),
        taxRate,
        discount,
      );

      store.nextOrderId += 1;
      for (const item of preparedItems) {
        item.itemId = store.nextItemId++;
        store.orderItems.push(item);
      }

      const newOrder: OrderRecord = {
        orderId,
        customerId,
        status: 'QUEUED',
        notes,
        createdAt: now,
        updatedAt: now,
        deliveredAt: null,
      };
      store.orders.push(newOrder);

      const invoice: InvoiceRecord = {
        invoiceId: store.nextInvoiceId++,
        orderId,
        invoiceNumber: `INV-${new Date().getFullYear()}-${String(orderId).padStart(4, '0')}`,
        subtotal,
        taxRate,
        taxAmount,
        discount,
        totalAmount,
        paymentStatus: 'UNPAID',
        paymentMethod: null,
        paidAt: null,
        createdAt: now,
      };
      store.invoices.push(invoice);

      store.history.push({
        historyId: store.nextHistoryId++,
        orderId,
        oldStatus: null,
        newStatus: 'QUEUED',
        changedAt: now,
      });

      store.pipeline.enqueueNewOrder(orderId);

      sendSuccess(res, 201, {
        order: { ...hydrateOrderSummary(newOrder), items: preparedItems },
        invoice,
      });
    } catch (err) {
      if (err instanceof DomainError) return sendError(res, err.statusCode, err.message);
      sendError(res, 500, 'Internal server error');
    }
  });

  app.get('/api/orders/:id', (req: Request, res: Response) => {
    try {
      const id = parseId(req.params.id, 'orderId');
      const order = store.orders.find((o) => o.orderId === id);
      if (!order) throw new DomainError(404, `Order #${id} not found`);
      const items = store.orderItems.filter((it) => it.orderId === id);
      const invoice = store.invoices.find((inv) => inv.orderId === id) || null;
      const statusHistory = store.history.filter((h) => h.orderId === id);
      sendSuccess(res, 200, { ...hydrateOrderSummary(order), items, invoice, statusHistory });
    } catch (err) {
      if (err instanceof DomainError) return sendError(res, err.statusCode, err.message);
      sendError(res, 500, 'Internal server error');
    }
  });

  app.delete('/api/orders/:id', (req: Request, res: Response) => {
    try {
      const id = parseId(req.params.id, 'orderId');
      const order = store.orders.find((o) => o.orderId === id);
      if (!order) throw new DomainError(404, `Order #${id} not found`);
      if (order.status === 'DELIVERED') throw new DomainError(409, 'Delivered orders cannot be cancelled');
      if (order.status === 'CANCELLED') throw new DomainError(409, `Order #${id} is already cancelled`);

      const oldStatus = order.status;
      store.pipeline.cancelOrder(id);
      order.status = 'CANCELLED';
      order.updatedAt = new Date().toISOString();
      store.history.push({
        historyId: store.nextHistoryId++,
        orderId: id,
        oldStatus,
        newStatus: 'CANCELLED',
        changedAt: order.updatedAt,
      });
      sendSuccess(res, 200, hydrateOrderSummary(order));
    } catch (err) {
      if (err instanceof DomainError) return sendError(res, err.statusCode, err.message);
      sendError(res, 500, 'Internal server error');
    }
  });

  // Queue
  app.get('/api/queue', (_req: Request, res: Response) => {
    const stageLists = store.pipeline.getStageLists();
    const result: Record<string, unknown[]> = {};
    for (const stage of ['QUEUED', 'WASHING', 'IRONING', 'READY_FOR_PICKUP'] as ActiveStage[]) {
      const ids = stageLists[stage];
      result[stage] = ids
        .map((id, idx) => {
          const order = store.orders.find((o) => o.orderId === id);
          if (!order) return null;
          const hydrated = hydrateOrderSummary(order);
          return {
            position: idx + 1,
            isHead: idx === 0,
            ...hydrated,
          };
        })
        .filter(Boolean);
    }
    sendSuccess(res, 200, result);
  });

  function applyTransition(transition: { orderId: number; oldStatus: ActiveStage; newStatus: OrderStatus }) {
    const order = store.orders.find((o) => o.orderId === transition.orderId);
    if (!order) throw new DomainError(404, `Order #${transition.orderId} not found`);
    const now = new Date().toISOString();
    order.status = transition.newStatus;
    order.updatedAt = now;
    if (transition.newStatus === 'DELIVERED') {
      order.deliveredAt = now;
    }
    store.history.push({
      historyId: store.nextHistoryId++,
      orderId: order.orderId,
      oldStatus: transition.oldStatus,
      newStatus: transition.newStatus,
      changedAt: now,
    });
    const invoice = store.invoices.find((inv) => inv.orderId === order.orderId);
    const unpaidWarning = transition.newStatus === 'DELIVERED' && invoice?.paymentStatus !== 'PAID';

    return {
      order: hydrateOrderSummary(order),
      oldStatus: transition.oldStatus,
      newStatus: transition.newStatus,
      unpaidWarning,
      ...(unpaidWarning
        ? {
            warningMessage: `Order #${order.orderId} was delivered with an UNPAID invoice (${invoice?.invoiceNumber}). Please collect payment.`,
          }
        : {}),
    };
  }

  app.post('/api/queue/process-next', (_req: Request, res: Response) => {
    try {
      const transition = store.pipeline.advanceStage('QUEUED');
      sendSuccess(res, 200, applyTransition(transition));
    } catch (err) {
      if (err instanceof DomainError) return sendError(res, err.statusCode, err.message);
      sendError(res, 500, 'Internal server error');
    }
  });

  app.post('/api/queue/advance/:orderId', (req: Request, res: Response) => {
    try {
      const orderId = parseId(req.params.orderId, 'orderId');
      const order = store.orders.find((o) => o.orderId === orderId);
      if (!order) throw new DomainError(404, `Order #${orderId} not found`);
      if (order.status === 'DELIVERED' || order.status === 'CANCELLED') {
        throw new DomainError(409, `Order #${orderId} is already ${order.status}`);
      }
      const transition = store.pipeline.advanceOrder(orderId);
      sendSuccess(res, 200, applyTransition(transition));
    } catch (err) {
      if (err instanceof DomainError) return sendError(res, err.statusCode, err.message);
      sendError(res, 500, 'Internal server error');
    }
  });

  // Billing
  app.get('/api/billing/summary', (_req: Request, res: Response) => {
    let revenueCollected = 0;
    let unpaidAmount = 0;
    const statusCounts: Record<OrderStatus, number> = {
      QUEUED: 0,
      WASHING: 0,
      IRONING: 0,
      READY_FOR_PICKUP: 0,
      DELIVERED: 0,
      CANCELLED: 0,
    };

    for (const order of store.orders) {
      statusCounts[order.status] += 1;
    }
    for (const inv of store.invoices) {
      const order = store.orders.find((o) => o.orderId === inv.orderId);
      if (inv.paymentStatus === 'PAID') {
        revenueCollected = roundMoney(revenueCollected + inv.totalAmount);
      } else if (order && order.status !== 'CANCELLED') {
        unpaidAmount = roundMoney(unpaidAmount + inv.totalAmount);
      }
    }

    sendSuccess(res, 200, {
      revenueCollected,
      unpaidAmount,
      totalOrders: store.orders.length,
      statusCounts,
      currencySymbol: SETTINGS.CURRENCY_SYMBOL,
      shopName: SETTINGS.SHOP_NAME,
      shopAddress: SETTINGS.SHOP_ADDRESS,
      taxRatePercent: SETTINGS.TAX_RATE_PERCENT,
    });
  });

  app.get('/api/billing/:orderId', (req: Request, res: Response) => {
    try {
      const orderId = parseId(req.params.orderId, 'orderId');
      const order = store.orders.find((o) => o.orderId === orderId);
      if (!order) throw new DomainError(404, `Order #${orderId} not found`);
      const invoice = store.invoices.find((inv) => inv.orderId === orderId);
      if (!invoice) throw new DomainError(404, `Invoice for order #${orderId} not found`);
      const customer = store.customers.find((c) => c.customerId === order.customerId) || null;
      const items = store.orderItems.filter((it) => it.orderId === orderId);

      sendSuccess(res, 200, {
        shop: {
          name: SETTINGS.SHOP_NAME,
          address: SETTINGS.SHOP_ADDRESS,
          currencySymbol: SETTINGS.CURRENCY_SYMBOL,
        },
        customer,
        order: hydrateOrderSummary(order),
        items,
        invoice,
      });
    } catch (err) {
      if (err instanceof DomainError) return sendError(res, err.statusCode, err.message);
      sendError(res, 500, 'Internal server error');
    }
  });

  app.put('/api/billing/:orderId/pay', (req: Request, res: Response) => {
    try {
      const orderId = parseId(req.params.orderId, 'orderId');
      const method = String(req.body?.paymentMethod || '').trim().toUpperCase() as PaymentMethod;
      if (!VALID_PAYMENT_METHODS.has(method)) {
        throw new DomainError(400, 'Invalid paymentMethod. Allowed: CASH, CARD, UPI');
      }
      const order = store.orders.find((o) => o.orderId === orderId);
      if (!order) throw new DomainError(404, `Order #${orderId} not found`);
      if (order.status === 'CANCELLED') {
        throw new DomainError(409, 'Cannot collect payment for a cancelled order');
      }
      const invoice = store.invoices.find((inv) => inv.orderId === orderId);
      if (!invoice) throw new DomainError(404, `Invoice for order #${orderId} not found`);
      if (invoice.paymentStatus === 'PAID') {
        throw new DomainError(409, `Invoice ${invoice.invoiceNumber} is already marked PAID`);
      }

      invoice.paymentStatus = 'PAID';
      invoice.paymentMethod = method;
      invoice.paidAt = new Date().toISOString();

      const customer = store.customers.find((c) => c.customerId === order.customerId) || null;
      const items = store.orderItems.filter((it) => it.orderId === orderId);

      sendSuccess(res, 200, {
        shop: {
          name: SETTINGS.SHOP_NAME,
          address: SETTINGS.SHOP_ADDRESS,
          currencySymbol: SETTINGS.CURRENCY_SYMBOL,
        },
        customer,
        order: hydrateOrderSummary(order),
        items,
        invoice,
      });
    } catch (err) {
      if (err instanceof DomainError) return sendError(res, err.statusCode, err.message);
      sendError(res, 500, 'Internal server error');
    }
  });

  // Serve the Vanilla HTML5/CSS3/JS frontend directly
  const frontendDir = path.resolve(process.cwd(), 'frontend');
  if (fs.existsSync(frontendDir)) {
    app.use(express.static(frontendDir));
    app.get('/', (_req: Request, res: Response) => {
      res.sendFile(path.join(frontendDir, 'index.html'));
    });
  }

  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  }

  const port = 3000;
  app.listen(port, '0.0.0.0', () => {
    console.log(`Laundry Management System preview running at http://localhost:${port}`);
  });
}

startServer();
