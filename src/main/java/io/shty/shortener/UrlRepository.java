package io.shty.shortener;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

import java.util.concurrent.CompletionStage;

public interface UrlRepository {
    CompletionStage<RowSet<Row>> add(String shortUrl, String longUrl);

    CompletionStage<String> getLong(String shortUrl);

    CompletionStage<String> getShort(String longUrl);
}
