package com.ntt.account.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class CreditClientTest {

  @Mock private WebClient.Builder webClientBuilder;

  @Mock private WebClient webClient;

  @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

  @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;

  @Mock private WebClient.ResponseSpec responseSpec;

  private CreditClient client;

  @BeforeEach
  void setUp() {
    client = new CreditClient(webClientBuilder);
  }

  @Test
  void hasOverdueDebtMonoCallsCreditServiceEndpoint() {
    stubGetBooleanEndpoint(
        "http://CREDIT-SERVICE/api/v1/credits/customer/{customerId}/has-overdue-debt",
        Boolean.TRUE);

    Boolean result = client.hasOverdueDebtMono("customer-1").block();

    assertThat(result).isTrue();
    verify(responseSpec).bodyToMono(Boolean.class);
  }

  @Test
  void hasOverdueDebtAdaptsMonoToSingle() {
    stubGetBooleanEndpoint(
        "http://CREDIT-SERVICE/api/v1/credits/customer/{customerId}/has-overdue-debt",
        Boolean.FALSE);

    Boolean result = client.hasOverdueDebt("customer-1").blockingGet();

    assertThat(result).isFalse();
  }

  @Test
  void fallbackHasOverdueDebtMonoReturnsTrueToBlockAccountCreation() {
    Boolean result =
        client.fallbackHasOverdueDebtMono("customer-1", new RuntimeException("timeout")).block();

    assertThat(result).isTrue();
  }

  @Test
  void hasActiveCreditCardMonoCallsCreditServiceEndpoint() {
    stubGetBooleanEndpoint(
        "http://CREDIT-SERVICE/api/v1/credits/customer/{customerId}/has-active-credit-card",
        Boolean.TRUE);

    Boolean result = client.hasActiveCreditCardMono("customer-1").block();

    assertThat(result).isTrue();
    verify(responseSpec).bodyToMono(Boolean.class);
  }

  @Test
  void hasActiveCreditCardAdaptsMonoToSingle() {
    stubGetBooleanEndpoint(
        "http://CREDIT-SERVICE/api/v1/credits/customer/{customerId}/has-active-credit-card",
        Boolean.FALSE);

    Boolean result = client.hasActiveCreditCard("customer-1").blockingGet();

    assertThat(result).isFalse();
  }

  @Test
  void fallbackHasActiveCreditCardMonoReturnsFalseWhenCreditServiceFails() {
    Boolean result =
        client
            .fallbackHasActiveCreditCardMono("customer-1", new RuntimeException("timeout"))
            .block();

    assertThat(result).isFalse();
  }

  private void stubGetBooleanEndpoint(String uri, Boolean response) {
    when(webClientBuilder.build()).thenReturn(webClient);
    when(webClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(eq(uri), eq("customer-1"))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(Boolean.class)).thenReturn(Mono.just(response));
  }
}
