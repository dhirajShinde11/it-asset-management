package com.company.itam;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@OpenAPIDefinition(
        info = @Info(
                title = "IT Asset Management (ITAM) API",
                version = "1.0.0",
                description = "Production-grade RESTful API for tracking enterprise IT hardware, software licenses, employee assignments, and maintenance logs.",
                contact = @Contact(name = "IT Engineering & Ops", email = "itam-support@company.internal"),
                license = @License(name = "Apache 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0")
        )
)
public class ItAssetManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItAssetManagementApplication.class, args);
    }
}
