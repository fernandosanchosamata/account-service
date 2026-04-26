package com.ntt.account.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

  @Bean
  @LoadBalanced
  public WebClient.Builder webClientBuilder(
      @Value("${account.clients.timeout:2s}") Duration clientTimeout) {
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(clientTimeout.toMillis()))
            .responseTimeout(clientTimeout);

    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
  }
}
