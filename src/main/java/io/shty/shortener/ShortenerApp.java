package io.shty.shortener;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ShortenerApp {
    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private Server server;
    private UrlRepositorySqlImpl urlRepositorySqlImpl;

    public static void main(String[] args) throws IOException, InterruptedException {
        final var server = new ShortenerApp();
        server.start();
        server.blockUntilShutdown();
    }

    private void start() throws IOException {
        urlRepositorySqlImpl = new UrlRepositorySqlImpl("db",5432,"postgres","postgres","postgres");

        int port = 8081;
        server = ServerBuilder.forPort(port)
                .addService(new TinyUrlServiceImpl(urlRepositorySqlImpl))
                .build()
                .start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownHook));
    }

    private void shutdownHook() {
        System.err.println("*** shutting down gRPC server since JVM is shutting down");
        try {
            urlRepositorySqlImpl.stop();
            stop();
        } catch (InterruptedException e) {
            e.printStackTrace(System.err);
        }
        System.err.println("*** server shut down");
    }

    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

}
