package io.github.gabrielivo.oficina.shared.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CpfValidatorTest {

    private CpfValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CpfValidator();
    }

    // -------------------------------------------------------------------------
    // CPFs VÁLIDOS
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Deve aceitar CPF válido sem formatação")
    void deveAceitarCpfValidoSemFormatacao() {
        assertThat(validator.isValid("52998224725", null)).isTrue();
    }

    @Test
    @DisplayName("Deve aceitar CPF válido com formatação (pontos e traço)")
    void deveAceitarCpfValidoComFormatacao() {
        assertThat(validator.isValid("529.982.247-25", null)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("Deve aceitar múltiplos CPFs válidos")
    @ValueSource(strings = {
        "52998224725",
        "11144477735",
        "98765432100",
        "529.982.247-25",
        "111.444.777-35"
    })
    void deveAceitarMultiplosCpfsValidos(String cpf) {
        assertThat(validator.isValid(cpf, null)).isTrue();
    }

    // -------------------------------------------------------------------------
    // CPFs INVÁLIDOS — dígitos verificadores errados
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Deve rejeitar CPF com primeiro dígito verificador errado")
    void deveRejeitarCpfComPrimeiroDigitoErrado() {
        // CPF válido seria 52998224725, trocamos o penúltimo dígito
        assertThat(validator.isValid("52998224715", null)).isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar CPF com segundo dígito verificador errado")
    void deveRejeitarCpfComSegundoDigitoErrado() {
        // CPF válido seria 52998224725, trocamos o último dígito
        assertThat(validator.isValid("52998224724", null)).isFalse();
    }

    // -------------------------------------------------------------------------
    // CPFs INVÁLIDOS — sequências repetidas
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @DisplayName("Deve rejeitar CPFs com todos os dígitos iguais")
    @ValueSource(strings = {
        "00000000000",
        "11111111111",
        "22222222222",
        "33333333333",
        "44444444444",
        "55555555555",
        "66666666666",
        "77777777777",
        "88888888888",
        "99999999999"
    })
    void deveRejeitarCpfComDigitosRepetidos(String cpf) {
        assertThat(validator.isValid(cpf, null)).isFalse();
    }

    // -------------------------------------------------------------------------
    // CPFs INVÁLIDOS — tamanho incorreto
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Deve rejeitar CPF com menos de 11 dígitos")
    void deveRejeitarCpfComMenosDe11Digitos() {
        assertThat(validator.isValid("1234567890", null)).isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar CPF com mais de 11 dígitos")
    void deveRejeitarCpfComMaisDe11Digitos() {
        assertThat(validator.isValid("123456789012", null)).isFalse();
    }

    // -------------------------------------------------------------------------
    // CPFs INVÁLIDOS — valores nulos ou vazios
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Deve rejeitar CPF nulo")
    void deveRejeitarCpfNulo() {
        assertThat(validator.isValid(null, null)).isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar CPF vazio")
    void deveRejeitarCpfVazio() {
        assertThat(validator.isValid("", null)).isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar CPF com apenas espaços")
    void deveRejeitarCpfComApenasEspacos() {
        assertThat(validator.isValid("   ", null)).isFalse();
    }

    // -------------------------------------------------------------------------
    // CPFs — caracteres especiais
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Deve aceitar CPF válido com caracteres especiais e removê-los")
    void deveAceitarCpfValidoComCaracteresEspeciais() {
        // Mesmo CPF com diferentes formatações
        assertThat(validator.isValid("529.982.247-25", null)).isTrue();
        assertThat(validator.isValid("52998224725", null)).isTrue();
    }

    @Test
    @DisplayName("Deve rejeitar CPF com letras")
    void deveRejeitarCpfComLetras() {
        assertThat(validator.isValid("5299822472A", null)).isFalse();
    }
}