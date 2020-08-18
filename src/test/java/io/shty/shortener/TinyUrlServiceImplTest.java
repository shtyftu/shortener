package io.shty.shortener;


import io.grpc.BindableService;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import io.shty.protocol.GetMaxiRequest;
import io.shty.protocol.GetMaxiResponse;
import io.shty.protocol.GetMiniRequest;
import io.shty.protocol.GetMiniResponse;
import io.shty.protocol.TinyUrlServiceGrpc;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.shty.shortener.TestUtils.assertThrows;
import static org.junit.Assert.assertEquals;


@RunWith(JUnit4.class)
public class TinyUrlServiceImplTest {

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @Test
    public void integrationTest() throws IOException {
        try (var postgres = new PostgreSQLTestContainer()) {
            var blockingStub = blockingStub(initServer(new TinyUrlServiceImpl(postgres.urlRepositorySql)));
            String originalUrl = "111111111111";

            String minifiedUrl0 = blockingStub.getMini(miniRequest(originalUrl)).getMinifiedUrl();

            GetMaxiResponse maxi = blockingStub.getMaxi(maxiRequest(minifiedUrl0));
            assertEquals(originalUrl, maxi.getOriginalUrl());

            String minifiedUrl1 = blockingStub.getMini(miniRequest(originalUrl)).getMinifiedUrl();
            assertEquals(minifiedUrl0, minifiedUrl1);

            assertThrows(
                    StatusRuntimeException.class,
                    () -> blockingStub.getMaxi(maxiRequest("invalidShortUrl"))
            );
        }
    }

    @Test
    public void stressTest() throws IOException {
        try (var postgres = new PostgreSQLTestContainer()) {
            var blockingStub = blockingStub(initServer(new TinyUrlServiceImpl(postgres.urlRepositorySql)));
            ExecutorService executorService = Executors.newFixedThreadPool(16);
            List<Future<GetMiniResponse>> prevFutures = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                List<Future<GetMiniResponse>> futures = new ArrayList<>();
                for (int j = 0; j < 100; j++) {
                    String originalUrl = RandomStringUtils.random(50, true, true);
                    futures.add(executorService.submit(() -> blockingStub.getMini(miniRequest(originalUrl))));
                }
                prevFutures.stream()
                        .map(this::value)
                        .limit(10)
                        .forEach(it -> {
                            for (int k = 0; k < 200; k++) {
                                executorService.submit(() -> blockingStub.getMaxi(maxiRequest(it)));
                            }
                        }
                );
                prevFutures = futures;
            }
        }
    }

    private String value(Future<GetMiniResponse> it) {
        try {
            return it.get().getMinifiedUrl();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TinyUrlServiceGrpc.TinyUrlServiceBlockingStub blockingStub(String serverName) {
        return TinyUrlServiceGrpc.newBlockingStub(
                grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
        );
    }

    @NotNull
    private String initServer(BindableService bindableService) throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        grpcCleanup.register(
                InProcessServerBuilder.forName(serverName).directExecutor()
                        .addService(bindableService)
                        .build().start()
        );
        return serverName;
    }

    private static GetMiniRequest miniRequest(String originalUrl) {
        return GetMiniRequest.newBuilder().setOriginalUrl(originalUrl).build();
    }

    private static GetMaxiRequest maxiRequest(String minifiedUrl) {
        return GetMaxiRequest.newBuilder().setMinifiedUrl(minifiedUrl).build();
    }
}
