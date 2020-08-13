package com.ffzs.webflux.webclient_demo;

import com.github.javafaker.Faker;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Locale;

@SpringBootApplication
public class WebclientDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebclientDemoApplication.class, args);
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class Employee {
    Long id;
    String name;
    Integer age;
    Integer salary;
    String phoneNumber;
    String address;
}


class EmployeeFaker extends Employee{
    private final Faker f = new Faker(Locale.CHINA);

    public EmployeeFaker (long i) {
        id = i;
        name = f.name().fullName();
        age = f.random().nextInt(20, 50);
        salary = f.number().numberBetween(1, 2000)*1000;
        phoneNumber = f.phoneNumber().cellPhone();
        address = f.address().streetName();
    }
}

@RestController
@RequestMapping("server")
class EmployeeController {

    @GetMapping(produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
    public Flux<Employee> getEmployee() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(EmployeeFaker::new);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> post (@RequestBody String info) {
        return Mono.just(info)
                .map(i -> "post info :  " + i);
    }

    @GetMapping(value = "uri", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> uri(@RequestParam("info") String info) {
        return Mono.just(info)
                .map(i -> "uri param -> key: info, value: " + i);
    }

    @GetMapping(value = "{info}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> uriPath(@PathVariable String info) {
        return Mono.just(info)
                .map(i -> "uri param -> key: info, value: " + i);
    }

}

@RestController
@RequestMapping("client")
@Slf4j
class EmployeeClient {

    @GetMapping(produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
    public Flux<Employee> proxyEmploy() {
        return WebClient.create("localhost:8080/server")
                .get()
                .retrieve()
                .bodyToFlux(Employee.class)
                .log()
                .filter(i -> i.getAge() < 25);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> proxyPost (@RequestBody Mono<String> info) {
        return WebClient.builder() // 除了使用create快速生成 外还可以使用builder创建
                .baseUrl("localhost:8080/server")
                .build()
                .post()
                .body(info, String.class)  // 将info body 写入 proxy请求的body中
                .retrieve()
                .bodyToMono(String.class)
                .map(i -> "proxy -> " + i);
    }

    @GetMapping(value = "uri", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> proxyUri (@RequestParam("info") String info) {
        return WebClient.builder()
                .baseUrl("http://localhost:8080/server")
                .build()
                .get()
                .uri(u -> u.path("/uri")
                        .queryParam("info", info)
                        .build()
                )
                .retrieve()
                .bodyToMono(String.class)
                .map(i -> "proxy -> " + i);
    }

    @GetMapping(value = "{info}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> proxyUriPath (@PathVariable String info) {
        return WebClient.builder()
                .baseUrl("http://localhost:8080/server")
                .filter((request, next) -> {
                    log.info("Sending request " + request.method() + " " + request.url());
                    return next.exchange(request);
                })
                .filter(logResponse())
                .build()
                .get()
                .uri(u -> u.path("/{info}")
                        .build(info)
                )
                .retrieve()
                .bodyToMono(String.class)
                .map(i -> "proxy -> " + i);
    }

    private ExchangeFilterFunction logResponse () {
        return ExchangeFilterFunction.ofResponseProcessor(c -> {
            c.headers().asHttpHeaders()
                    .forEach((k, vs) -> vs.forEach(v -> log.info("Response header: {} {}", k, v)));
            return Mono.just(c);
        });
    }
}


