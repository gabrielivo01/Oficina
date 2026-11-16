package io.github.gabrielivo.oficina.presentation.cliente;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class ClienteRequestValidationTest {

    @Test
    void emailEObrigatorioEDeveTerFormatoValido() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var semEmail = new ClienteRequest("52998224725", "Maria", "11999999999", null, null);
        var emailInvalido = new ClienteRequest("52998224725", "Maria", "11999999999", "invalido", null);

        assertTrue(validator.validate(semEmail).stream()
                .anyMatch(error -> error.getPropertyPath().toString().equals("email")));
        assertTrue(validator.validate(emailInvalido).stream()
                .anyMatch(error -> error.getPropertyPath().toString().equals("email")));
    }
}
