package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${rate-limit.login.max-attempts:5}")
    private int maxAttempts;

    @Value("${rate-limit.login.window-seconds:900}")
    private int windowSeconds;

    public boolean isLoginAllowed(String ipAddress) {
        Bucket bucket = buckets.computeIfAbsent(ipAddress, k -> createBucket());
        return bucket.tryConsume(1);
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(maxAttempts)
                .refillIntervally(maxAttempts, Duration.ofSeconds(windowSeconds))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
