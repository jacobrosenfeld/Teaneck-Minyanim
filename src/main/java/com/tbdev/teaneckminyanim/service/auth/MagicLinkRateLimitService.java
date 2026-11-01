package com.tbdev.teaneckminyanim.service.auth;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class MagicLinkRateLimitService {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int IP_LIMIT = 5;
    private static final int IDENTIFIER_LIMIT = 3;

    private final Cache<String, Bucket> ipBuckets = cache();
    private final Cache<String, Bucket> identifierBuckets = cache();

    public boolean allow(String ipHash, String identifierHash) {
        try {
            Bucket ipBucket = ipBuckets.get(ipHash, () -> bucket(IP_LIMIT));
            Bucket identifierBucket = identifierBuckets.get(identifierHash, () -> bucket(IDENTIFIER_LIMIT));
            if (ipBucket.getAvailableTokens() < 1 || identifierBucket.getAvailableTokens() < 1) {
                return false;
            }
            return ipBucket.tryConsume(1) && identifierBucket.tryConsume(1);
        } catch (ExecutionException e) {
            // A cache loader failure must fail closed for a login-link request.
            return false;
        }
    }

    private Cache<String, Bucket> cache() {
        return CacheBuilder.newBuilder()
                .maximumSize(20_000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
    }

    private Bucket bucket(int limit) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, WINDOW)))
                .build();
    }
}
