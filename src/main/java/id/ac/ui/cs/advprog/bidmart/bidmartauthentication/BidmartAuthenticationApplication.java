package id.ac.ui.cs.advprog.bidmart.bidmartauthentication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class BidmartAuthenticationApplication {

    public static void main(String[] args) {
        SpringApplication.run(BidmartAuthenticationApplication.class, args);
    }

}
