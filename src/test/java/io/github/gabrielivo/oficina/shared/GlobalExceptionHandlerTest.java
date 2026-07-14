package io.github.gabrielivo.oficina.shared;

import io.github.gabrielivo.oficina.domain.cliente.ClienteException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void deveTratarClienteException() {
        var exception = new ClienteException("Cliente inválido");

        ResponseEntity<Map<String, String>> response = handler.handleCliente(exception);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("Cliente inválido", response.getBody().get("erro"));
    }

    @Test
    void deveTratarBadCredentialsExceptionComoUnauthorized() {
        var exception = new org.springframework.security.authentication.BadCredentialsException("Falha");

        ResponseEntity<Map<String, String>> response = handler.handleBadCredentials(exception);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Login ou senha inválidos.", response.getBody().get("erro"));
    }

    @Test
    void deveTratarValidationExceptionEExtrairMensagemDoPrimeiroErro() throws NoSuchMethodException {
        Method method = getClass().getMethod("dummyMethod", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        var bindingResult = new BeanPropertyBindingResult(new Object(), "obj");
        bindingResult.addError(new FieldError("obj", "nome", "Nome obrigatório"));
        var exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("nome: Nome obrigatório", response.getBody().get("erro"));
    }

    @Test
    void deveTratarIllegalArgumentExceptionComoBadRequest() {
        ResponseEntity<Map<String, String>> response = handler.handleIllegalArgument(new IllegalArgumentException("Valor inválido"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Valor inválido", response.getBody().get("erro"));
    }

    @Test
    void deveTratarResponseStatusException() {
        ResponseEntity<Map<String, String>> response = handler.handleResponseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso não encontrado"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Recurso não encontrado", response.getBody().get("erro"));
    }

    @Test
    void deveTratarExcecaoGenericaComoErroInterno() {
        ResponseEntity<Map<String, String>> response = handler.handleGeneric(new RuntimeException("Falha inesperada"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Erro interno inesperado.", response.getBody().get("erro"));
    }

    public void dummyMethod(String nome) {
        // usado apenas para criar MethodParameter.
    }
}
