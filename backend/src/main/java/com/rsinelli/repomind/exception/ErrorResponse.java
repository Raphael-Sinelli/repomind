package com.rsinelli.repomind.exception;

import java.util.List;

/**
 * Envelope unico de erro da API. O frontend le sempre {@code error.message} — nunca a
 * mensagem generica do cliente HTTP, que diria apenas "Request failed with status 400".
 */
public record ErrorResponse(ApiError error) {

  public record ApiError(String code, String message, int status, List<FieldError> details) {}

  public record FieldError(String field, String message) {}

  public static ErrorResponse of(String code, String message, int status) {
    return new ErrorResponse(new ApiError(code, message, status, null));
  }

  public static ErrorResponse of(
      String code, String message, int status, List<FieldError> details) {
    return new ErrorResponse(new ApiError(code, message, status, details));
  }
}
