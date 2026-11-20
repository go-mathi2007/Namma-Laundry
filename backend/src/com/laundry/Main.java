package com.laundry;

import com.laundry.config.ConnectionPool;
import com.laundry.config.DatabaseConfig;
import com.laundry.dao.CustomerDao;
import com.laundry.dao.InvoiceDao;
import com.laundry.dao.OrderDao;
import com.laundry.dao.OrderItemDao;
import com.laundry.dao.PriceDao;
import com.laundry.handler.BillingHandler;
import com.laundry.handler.CustomerHandler;
import com.laundry.handler.OrderHandler;
import com.laundry.handler.QueueHandler;
import com.laundry.handler.StaticFileHandler;
import com.laundry.service.BillingService;
import com.laundry.service.CustomerService;
import com.laundry.service.OrderService;
import com.laundry.service.QueueService;
import com.laundry.structure.ProcessingPipeline;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application entry point for the Laundry Management System HTTP server.
 * Initializes JDBC ConnectionPool, rebuilds the FIFO ProcessingPipeline from MySQL,
 * and starts the JDK built-in HttpServer on port 8080 (or SERVER_PORT env var).
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        try {
            ConnectionPool pool = new ConnectionPool();

            CustomerDao customerDao = new CustomerDao(pool);
            OrderDao orderDao = new OrderDao(pool);
            OrderItemDao orderItemDao = new OrderItemDao(pool);
            PriceDao priceDao = new PriceDao(pool);
            InvoiceDao invoiceDao = new InvoiceDao(pool);

            ProcessingPipeline pipeline = new ProcessingPipeline();

            CustomerService customerService = new CustomerService(customerDao, orderDao);
            OrderService orderService = new OrderService(
                    pool, customerDao, orderDao, orderItemDao, priceDao, invoiceDao, pipeline
            );
            QueueService queueService = new QueueService(pool, orderDao, invoiceDao, pipeline);
            BillingService billingService = new BillingService(invoiceDao, orderDao, orderItemDao, customerDao);

            // Rebuild in-memory FIFO stage queues from MySQL so order sequence survives restarts
            queueService.rebuildQueuesOnStartup();

            int port = DatabaseConfig.getServerPort();
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/api/customers", new CustomerHandler(customerService));
            OrderHandler orderHandler = new OrderHandler(orderService);
            server.createContext("/api/orders", orderHandler);
            server.createContext("/api/prices", orderHandler);
            server.createContext("/api/queue", new QueueHandler(queueService));
            server.createContext("/api/billing", new BillingHandler(billingService));

            Path frontendPath = resolveFrontendDirectory();
            server.createContext("/", new StaticFileHandler(frontendPath));

            server.setExecutor(Executors.newFixedThreadPool(16));
            server.start();

            LOGGER.info("Laundry Management System running at http://localhost:" + port
                    + " (serving static assets from " + frontendPath + ")");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start Laundry Management System server", e);
            System.exit(1);
        }
    }

    private static Path resolveFrontendDirectory() {
        String configured = DatabaseConfig.getFrontendDir();
        Path direct = Paths.get(configured).toAbsolutePath().normalize();
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path parentFallback = Paths.get("..", configured).toAbsolutePath().normalize();
        if (Files.isDirectory(parentFallback)) {
            return parentFallback;
        }
        return direct;
    }
}
