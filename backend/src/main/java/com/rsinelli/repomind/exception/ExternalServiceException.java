package com.rsinelli.repomind.exception;

/** Falha ao falar com GitHub ou Anthropic. Vira 502 — o defeito nao e do cliente. */
public class ExternalServiceException extends RuntimeException {

  private final String service;

  public ExternalServiceException(String service, String message) {
    super(message);
    this.service = service;
  }

  public ExternalServiceException(String service, String message, Throwable cause) {
    super(message, cause);
    this.service = service;
  }

  public String getService() {
    return service;
  }
}
