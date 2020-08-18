package io.shty.shortener;

import io.shty.shortener.exception.DuplicateLongUrlException;
import io.shty.shortener.exception.DuplicateShortUrlException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgException;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class UrlRepositorySqlImpl implements UrlRepository {

    private final PgPool client;

    public UrlRepositorySqlImpl(
            String host,
            int port,
            String database,
            String user,
            String password
    ) {


        client = PgPool.pool(
                new PgConnectOptions()
                        .setPort(port)
                        .setHost(host)
                        .setDatabase(database)
                        .setUser(user)
                        .setPassword(password),
                new PoolOptions()
                        .setMaxSize(5)
        );
        createTable();
    }

    private void createTable() {
        Utils.executeSync(
                client.query("CREATE TABLE IF NOT EXISTS urls (\n" +
                        " short varchar(10) primary key,\n" +
                        " long text NOT NULL\n" +
                        ");" +
                        "CREATE UNIQUE INDEX IF NOT EXISTS urls_ids ON urls (long);")

        );
    }

    @Override
    public CompletionStage<RowSet<Row>> add(String shortUrl, String longUrl) {
        return Utils.execute(client.query("INSERT INTO urls VALUES('" + shortUrl + "', '" + longUrl + "');"))
                .exceptionally(throwable -> {
                    if (throwable instanceof PgException) {
                        PgException pgException = (PgException) throwable;
                        String detail = pgException.getDetail();
                        if (detail.startsWith("Key (long)=")) {
                            throw new DuplicateLongUrlException(longUrl);
                        } else if (detail.startsWith("Key (short)=")) {
                            throw new DuplicateShortUrlException();
                        }
                    }
                    throw new RuntimeException(throwable);
                });
    }

    @Override
    public CompletionStage<String> getLong(String shortUrl) {
        return Utils
                .execute(client.query("SELECT long FROM urls WHERE short='" + shortUrl + "'"))
                .thenApply(rows -> {
                    var iterator = rows.iterator();
                    return iterator.hasNext() ? iterator.next().getString("long") : null;
                });
    }

    @Override
    public CompletionStage<String> getShort(String longUrl) {
        return Utils
                .execute(client.query("SELECT short FROM urls WHERE long='" + longUrl + "'"))
                .thenApply(rows -> {
                    var iterator = rows.iterator();
                    return iterator.hasNext() ? iterator.next().getString("short") : null;
                });
    }

    public void stop() {
        client.close();
    }

    private static class Utils {

        public static CompletionStage<RowSet<Row>> execute(Query<RowSet<Row>> query) {
            return future(query::execute);
        }

        public static void executeSync(Query<RowSet<Row>> query) {
            execute(query).toCompletableFuture().join();
        }

        private static CompletionStage<RowSet<Row>> future(Consumer<Handler<AsyncResult<RowSet<Row>>>> handlerConsumer) {
            var future = new CompletableFuture<RowSet<Row>>();
            Handler<AsyncResult<RowSet<Row>>> adapter = adapter(future);
            handlerConsumer.accept(adapter);
            return future;
        }

        private static Handler<AsyncResult<RowSet<Row>>> adapter(CompletableFuture<RowSet<Row>> future) {
            return event -> {
                if (event.succeeded()) {
                    future.complete(event.result());
                } else {
                    future.completeExceptionally(event.cause());
                }
            };
        }
    }
}
