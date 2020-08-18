package io.shty.shortener;

import io.grpc.stub.StreamObserver;
import io.shty.protocol.*;
import io.shty.shortener.exception.DuplicateLongUrlException;
import io.shty.shortener.exception.DuplicateShortUrlException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class TinyUrlServiceImpl extends TinyUrlServiceGrpc.TinyUrlServiceImplBase {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    private final UrlRepository urlRepository;

    public TinyUrlServiceImpl(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    @Override
    public void getMini(GetMiniRequest request, StreamObserver<GetMiniResponse> responseObserver) {
        String originalUrl = request.getOriginalUrl();
        var completionStage = insert(originalUrl);
        int retriesCount = 1;
        for (int i = 0; i < retriesCount; i++) {
            completionStage = completionStage
                    .thenApply(CompletableFuture::completedFuture)
                    .exceptionally(throwable -> {
                        if (throwable instanceof CompletionException) {
                            Throwable cause = throwable.getCause();
                            if (cause instanceof DuplicateLongUrlException) {
                                return urlRepository.getShort(((DuplicateLongUrlException) cause).longUrl)
                                        .toCompletableFuture();
                            } else if (cause instanceof DuplicateShortUrlException) {
                                return insert(originalUrl).toCompletableFuture();
                            }
                        }
                        logger.error("Unable to shorten '" + originalUrl + "'", throwable);
                        return CompletableFuture.completedFuture("");
                    })
                    .thenCompose(Function.identity());
        }
        completionStage.handle(voidWrapper((shortUrl, throwable) -> {
            if (StringUtils.isNoneBlank(shortUrl)) {
                responseObserver.onNext(miniResponse(shortUrl));
            } else if (throwable instanceof CompletionException) {
                responseObserver.onError(throwable.getCause());
            } else {
                responseObserver.onError(new RuntimeException());
            }
            responseObserver.onCompleted();
        }));
    }

    @Override
    public void getMaxi(GetMaxiRequest request, StreamObserver<GetMaxiResponse> responseObserver) {
        String minifiedUrl = request.getMinifiedUrl();
        CompletionStage<String> voidCompletionStage = urlRepository.getLong(minifiedUrl);
        voidCompletionStage
                .thenAccept(url -> {
                    responseObserver.onNext(maxiResponse(url));
                    responseObserver.onCompleted();

                })
                .exceptionally(voidWrapper(t -> {
                            responseObserver.onError(t);
                            responseObserver.onCompleted();
                        }
                ));
    }

    GetMiniResponse miniResponse(String shortUrl) {
        return GetMiniResponse.newBuilder().setMinifiedUrl(shortUrl).build();
    }

    GetMaxiResponse maxiResponse(String longUrl) {
        return GetMaxiResponse.newBuilder().setOriginalUrl(longUrl).build();
    }

    private CompletionStage<String> insert(String originalUrl) {
        final var shortUrl = RandomStringUtils.random(10, true, true);
        return urlRepository.add(shortUrl, originalUrl)
                .thenApply(rows -> shortUrl);
    }

    private Function<Throwable, Void> voidWrapper(Consumer<Throwable> consumer) {
        return throwable -> {
            consumer.accept(throwable);
            return null;
        };
    }

    private BiFunction<String, Throwable, Void> voidWrapper(BiConsumer<String, Throwable> consumer) {
        return (s, throwable) -> {
            consumer.accept(s, throwable);
            return null;
        };
    }
}
