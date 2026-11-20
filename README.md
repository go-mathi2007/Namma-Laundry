# Namma Laundry — Laundry Management System

A full-stack Laundry Management System built with **Java 17+** (`com.sun.net.httpserver.HttpServer`), **pure JDBC + MySQL 8**, a hand-written **singly-linked-list FIFO Queue** (`LaundryQueue<T>`), a zero-dependency **recursive-descent JSON parser/serializer** (`JsonUtil`), and a **Vanilla HTML5 / CSS3 / JavaScript** single-page frontend.

---

## 1. Prerequisites

- **JDK 17 or newer** (`javac -version`, `java -version`)
- **MySQL 8.0+** (local server or Docker container)
- **MySQL Connector/J JAR** (e.g., `mysql-connector-j-8.4.0.jar` or `9.x`) downloaded from the official MySQL site into a `lib/` folder, for example:
  `lib/mysql-connector-j-8.4.0.jar`

---

## 2. Database Setup (`schema.sql` & `sample_data.sql`)

### Option A: MySQL CLI
```bash
mysql -u root -p < sql/schema.sql
mysql -u root -p < sql/sample_data.sql
```

### Option B: MySQL Workbench
1. Open MySQL Workbench and connect to your MySQL 8 instance.
2. Choose **File → Open SQL Script...**, select `sql/schema.sql`, and click **Execute** (lightning bolt icon).
3. Repeat with `sql/sample_data.sql` to load the 8 sample customers, 24-entry price list, and 12 orders across all statuses.

---

## 3. Database Credentials & Environment Variables

Default settings in `com.laundry.config.DatabaseConfig` can be overridden using environment variables:

| Variable | Default | Description |
| :--- | :--- | :--- |
| `DB_URL` | `jdbc:mysql://localhost:3306/laundry_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` | JDBC connection URL |
| `DB_USER` | `root` | MySQL username |
| `DB_PASS` | `""` (empty) | MySQL password |
| `DB_POOL_SIZE` | `10` | Custom `ConnectionPool` size |
| `SERVER_PORT` | `8080` | HTTP server port |
| `FRONTEND_DIR` | `frontend` | Static frontend folder path |

---

## 4. Compile & Run

From the repository root directory:

### Linux / macOS
```bash
mkdir -p out
javac -d out -cp "lib/mysql-connector-j-8.4.0.jar" $(find backend/src -name "*.java")
DB_USER=root DB_PASS=yourpassword java -cp "out:lib/mysql-connector-j-8.4.0.jar" com.laundry.Main
```

### Windows (PowerShell)
```powershell
New-Item -ItemType Directory -Force -Path out | Out-Null
$files = Get-ChildItem -Path backend\src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -d out -cp "lib\mysql-connector-j-8.4.0.jar" $files
$env:DB_USER="root"; $env:DB_PASS="yourpassword"
java -cp "out;lib\mysql-connector-j-8.4.0.jar" com.laundry.Main
```

### Open in Browser
Open **http://localhost:8080** in your browser. (`StaticFileHandler` serves `frontend/index.html` directly.)

---

## 5. REST API Summary

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/customers` | Register a customer (`409` on duplicate phone) |
| `GET` | `/api/customers?search=` | List all customers or search by name, phone, or ID |
| `GET` | `/api/customers/{id}` | Get customer details |
| `GET` | `/api/customers/{id}/orders` | Get customer order history with totals |
| `GET` | `/api/prices` | Get tariff table and shop/tax settings |
| `POST` | `/api/orders` | Create order, items, invoice & enqueue in one transaction |
| `GET` | `/api/orders?status=` | List orders with optional status filter |
| `GET` | `/api/orders/{id}` | Get order details, line items, invoice, and history |
| `DELETE` | `/api/orders/{id}` | Cancel an active order and remove from queue |
| `GET` | `/api/queue` | Snapshot of all four FIFO queues with 1-based positions |
| `POST` | `/api/queue/process-next` | Dequeue head of `QUEUED` into `WASHING` |
| `POST` | `/api/queue/advance/{orderId}` | Advance head order (`409` if not at head of its stage) |
| `GET` | `/api/billing/{orderId}` | Itemized invoice with shop and customer details |
| `PUT` | `/api/billing/{orderId}/pay` | Mark invoice `PAID` (`CASH`, `CARD`, or `UPI`) |
| `GET` | `/api/billing/summary` | Revenue collected, unpaid balance, and status counts |

---

## 6. How the FIFO Queue & Pipeline Work

- `LaundryQueue<T>` is a synchronized generic queue implemented on a singly linked list with explicit `head` and `tail` pointers (`enqueue`, `dequeue`, `peek`, `remove`, `toList`, `clear`).
- `ProcessingPipeline` maintains four active stage queues: `queuedQ` (`QUEUED`), `washingQ` (`WASHING`), `ironingQ` (`IRONING`), and `readyQ` (`READY_FOR_PICKUP`).
- On server startup, `QueueService.rebuildQueuesOnStartup()` queries all non-terminal orders ordered by `created_at ASC` and repopulates the four queues so FIFO order survives restarts.
- Advancing an order validates that the target `orderId` is strictly at `peek()` (position `#1`) of its current stage queue; non-head requests fail with HTTP `409`. Database status updates and `order_status_history` inserts commit in a single JDBC transaction, restoring the in-memory queue snapshot if the DB commit fails.
- Delivering an order with an `UNPAID` invoice succeeds and returns `unpaidWarning: true` so the staff is alerted to collect payment.

---

## 7. Troubleshooting

- **Connection refused (`Communications link failure`)**: Ensure MySQL 8 is running on port `3306` and `DB_URL`, `DB_USER`, and `DB_PASS` match your local server.
- **Driver not found (`ClassNotFoundException: com.mysql.cj.jdbc.Driver`)**: Verify the Connector/J JAR path in `-cp` (use `:` on Linux/macOS and `;` on Windows).
- **Port 8080 already in use (`BindException`)**: Stop the conflicting process or start with `SERVER_PORT=8081` (and update `window.API_BASE_URL` if opening `frontend/index.html` directly from `file://`).
- **CORS errors when opening `frontend/index.html` directly**: Ensure the Java backend is running on `http://localhost:8080`; every backend handler automatically returns `Access-Control-Allow-Origin: *` and handles `OPTIONS` preflight requests with `204`.
