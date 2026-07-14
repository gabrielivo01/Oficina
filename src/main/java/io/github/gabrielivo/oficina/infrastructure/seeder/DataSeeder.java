package io.github.gabrielivo.oficina.infrastructure.seeder;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.gabrielivo.oficina.application.ordemServico.CriarPecaCommand;
import io.github.gabrielivo.oficina.application.ordemServico.PecaService;
import io.github.gabrielivo.oficina.domain.peca.PecaRepository;
import io.github.gabrielivo.oficina.domain.usuario.Usuario;
import io.github.gabrielivo.oficina.domain.usuario.UsuarioRepository;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final List<CriarPecaCommand> PECAS_INICIAIS = List.of(
        new CriarPecaCommand("Filtro de Óleo", "Filtro de óleo para motores", new BigDecimal("25.50"), 50, 10),
        new CriarPecaCommand("Pastilha de Freio", "Pastilha de freio dianteira", new BigDecimal("120.00"), 20, 5),
        new CriarPecaCommand("Bateria 60Ah", "Bateria automotiva 60Ah", new BigDecimal("350.00"), 15, 3),
        new CriarPecaCommand("Óleo 5W30", "Óleo lubrificante sintético", new BigDecimal("45.00"), 30, 8),
        new CriarPecaCommand("Velas de Ignição", "Jogo com 4 velas", new BigDecimal("85.00"), 25, 6)
    );

    private final UsuarioRepository usuarioRepository;
    private final PecaRepository pecaRepository;
    private final PecaService pecaService;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UsuarioRepository usuarioRepository, PecaRepository pecaRepository, PecaService pecaService, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.pecaRepository = pecaRepository;
        this.pecaService = pecaService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        criarUsuarioAdminSeNecessario();
        criarPecasIniciaisSeNecessario();
    }

    private void criarUsuarioAdminSeNecessario() {
        if (usuarioRepository.findByLogin("admin").isPresent()) {
            return;
        }

        Usuario admin = new Usuario("admin", passwordEncoder.encode("admin123"));
        usuarioRepository.save(admin);
        log.info("Usuário admin padrão criado.");
    }

    private void criarPecasIniciaisSeNecessario() {
        if (pecaRepository.count() > 0) {
            return;
        }

        try {
            PECAS_INICIAIS.forEach(pecaService::criar);
            log.info("Peças iniciais carregadas com sucesso.");
        } catch (Exception e) {
            log.error("Erro ao criar peças iniciais.", e);
        }
    }
}
