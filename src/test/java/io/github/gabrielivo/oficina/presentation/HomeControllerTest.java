package io.github.gabrielivo.oficina.presentation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HomeControllerTest {

    @Test
    void deveRedirecionarParaSwaggerUi() {
        HomeController controller = new HomeController();

        String redirect = controller.home();

        assertEquals("redirect:/swagger-ui.html", redirect);
    }
}
