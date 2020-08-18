package io.shty.shortener;


import io.shty.shortener.exception.DuplicateLongUrlException;
import io.shty.shortener.exception.DuplicateShortUrlException;
import org.junit.Test;

import static io.shty.shortener.TestUtils.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


public class UrlRepositorySqlImplTest {

    @Test
    public void testSimpleCases() {
        try (var postgres = new PostgreSQLTestContainer()) {
            String shortUrl0 = "a";
            String longUrl0 = "111";
            String shortUrl1 = "b";
            String longUrl1 = "222";
            String shortUrl2 = "c";

            UrlRepository sqlClient = postgres.urlRepositorySql;

            sqlClient.add(shortUrl0, longUrl0);
            sqlClient.add(shortUrl1, longUrl1);

            assertEquals(longUrl0, TestUtils.runBlocking(sqlClient.getLong(shortUrl0)));
            assertEquals(longUrl1, TestUtils.runBlocking(sqlClient.getLong(shortUrl1)));

            assertNull(TestUtils.runBlocking(sqlClient.getLong(shortUrl2)));
        }
    }

    @Test
    public void testDuplicateLongUrlException() {
        try (var postgres = new PostgreSQLTestContainer()) {
            UrlRepository sqlClient = postgres.urlRepositorySql;

            String longUrl = "111";
            String shortUrl0 = "a";
            String shortUrl1 = "b";

            TestUtils.runBlocking(sqlClient.add(shortUrl0, longUrl));
            assertThrows(
                    DuplicateLongUrlException.class,
                    () -> TestUtils.runBlocking(sqlClient.add(shortUrl1, longUrl))
            );
        }
    }



    @Test
    public void testDuplicateShortUrlException() {
        try (var postgres = new PostgreSQLTestContainer()) {
            UrlRepository sqlClient = postgres.urlRepositorySql;

            String shortUrl = "a";
            String longUrl0 = "111";
            String longUrl1 = "222";

            TestUtils.runBlocking(sqlClient.add(shortUrl, longUrl0));

            assertThrows(
                    DuplicateShortUrlException.class,
                    () -> TestUtils.runBlocking(sqlClient.add(shortUrl, longUrl1))
            );
        }
    }

}
