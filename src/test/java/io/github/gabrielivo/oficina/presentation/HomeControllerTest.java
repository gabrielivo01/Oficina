package io.github.gabrielivo.oficina.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HomeControllerTest {

    @Test
    void deveRedirecionarParaSwaggerUi() {
        HomeController controller = new HomeController();

        String redirect = controller.home();

        assertEquals("redirect:/swagger-ui.html", redirect);
    }
}
