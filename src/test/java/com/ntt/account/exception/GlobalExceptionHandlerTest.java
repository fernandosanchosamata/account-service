package com.ntt.account.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

  @Mock private WebExchangeBindException bindException;

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleBindExceptionReturnsBadRequestWithFieldErrors() {
    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new Object(), "request");
    bindingResult.addError(new FieldError("request", "customerId", "customerId no puede ser nulo"));
    bindingResult.addError(
        new FieldError("request", "initialBalance", "El saldo inicial es obligatorio"));
    when(bindException.getBindingResult()).thenReturn(bindingResult);

    var response = handler.handleBindException(bindException).block();

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(response.getBody().getMessage())
        .startsWith("Error de validaci")
        .contains("customerId: customerId no puede ser nulo")
        .contains("initialBalance: El saldo inicial es obligatorio");
  }

  @Test
  void handleIllegalArgumentExceptionReturnsBadRequestWithExceptionMessage() {
    var response =
        handler
            .handleIllegalArgumentException(new IllegalArgumentException("Cuenta no encontrada."))
            .block();

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(response.getBody().getMessage()).isEqualTo("Cuenta no encontrada.");
  }
}
