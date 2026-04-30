package com.ntt.account.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ntt.account.model.dto.CustomerSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class CustomerClientTest {

  @Mock private WebClient.Builder webClientBuilder;

  @Mock private WebClient webClient;

  @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

  @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;

  @Mock private WebClient.ResponseSpec responseSpec;

  private CustomerClient client;

  @BeforeEach
  void setUp() {
    client = new CustomerClient(webClientBuilder);
  }

  @Test
  void getCustomerSummaryCallsCustomerServiceSummaryEndpoint() {
    CustomerSummaryResponse response = new CustomerSummaryResponse();
    response.setId("customer-1");
    response.setStatus("ACTIVE");
    when(webClientBuilder.build()).thenReturn(webClient);
    when(webClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(
            eq("http://CUSTOMER-SERVICE/api/v1/customers/{customerId}/summary"), eq("customer-1")))
        .thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(CustomerSummaryResponse.class)).thenReturn(Mono.just(response));

    CustomerSummaryResponse result = client.getCustomerSummary("customer-1").blockingGet();

    assertThat(result).isSameAs(response);
    verify(responseSpec).bodyToMono(CustomerSummaryResponse.class);
  }
}
