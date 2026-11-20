/**
 * REST controllers, one per persona area (customer, warehouse, finance, operations). Controllers
 * accept {@code model.dto.request} types, call a {@code service} interface and return {@code
 * model.dto.response} types; they never expose entities. Errors use {@code ErrorResponse} via
 * {@code GlobalExceptionHandler}.
 */
package com.returnright.controller;
