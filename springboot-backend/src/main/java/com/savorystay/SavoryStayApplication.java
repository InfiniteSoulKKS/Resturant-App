package com.savorystay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SavoryStayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SavoryStayApplication.class, args);
        System.out.println("==========================================================================");
        System.out.println("SavoryStay Culinary Operations Spring Boot Backend Started Successfully!");
        System.out.println("Spring Security JWT active on port 8080");
        System.out.println("Stripe & PayPal Gateways initialized");
        System.out.println("==========================================================================");
    }
}
