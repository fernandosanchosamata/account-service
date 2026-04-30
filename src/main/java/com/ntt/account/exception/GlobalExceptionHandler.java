package com.ntt.account.exception;

import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(WebExchangeBindException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleBindException(WebExchangeBindException ex) {
    String errorMsg =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .message("Error de validación de Payload: " + errorMsg)
            .build();

    log.warn("Payload invalido: {}", errorMsg);
    return Mono.just(ResponseEntity.badRequest().body(response));
  }

  // El manejo de rxjava puro si ocurre alguna excepcion nativa en map/flatmap
  @ExceptionHandler(IllegalArgumentException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgumentException(
      IllegalArgumentException ex) {
    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .message(ex.getMessage())
            .build();

    log.warn("Solicitud rechazada por regla de negocio: {}", ex.getMessage());
    return Mono.just(ResponseEntity.badRequest().body(response));
  }
}
