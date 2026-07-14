package io.github.gabrielivo.oficina.shared;

import io.github.gabrielivo.oficina.domain.cliente.ClienteException;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServicoException;
import io.github.gabrielivo.oficina.domain.pagamento.PagamentoException;
import io.github.gabrielivo.oficina.domain.peca.PecaException;
import io.github.gabrielivo.oficina.domain.usuario.UsuarioException;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ClienteException.class)
    public ResponseEntity<Map<String, String>> handleCliente(ClienteException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst().orElse("Erro de validação");
        return ResponseEntity.badRequest().body(Map.of("erro", msg));
    }

    @ExceptionHandler(VeiculoException.class)
    public ResponseEntity<Map<String, String>> handleVeiculo(VeiculoException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(OrdemServicoException.class)
    public ResponseEntity<Map<String, String>> handleOS(OrdemServicoException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(PecaException.class)
    public ResponseEntity<Map<String, String>> handlePeca(PecaException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(PagamentoException.class)
    public ResponseEntity<Map<String, String>> handlePagamento(PagamentoException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("erro", "Login ou senha inválidos."));
    }

    @ExceptionHandler(UsuarioException.class)
    public ResponseEntity<Map<String, String>> handleUsuario(UsuarioException ex) {
         return ResponseEntity.unprocessableEntity()
                .body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("erro", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("erro", "Erro interno inesperado."));
    }
}