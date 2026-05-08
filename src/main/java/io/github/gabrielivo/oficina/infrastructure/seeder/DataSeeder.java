package io.github.gabrielivo.oficina.infrastructure.seeder;



import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import io.github.gabrielivo.oficina.application.ordemServico.CriarPecaCommand;
import io.github.gabrielivo.oficina.application.ordemServico.PecaService;
import io.github.gabrielivo.oficina.domain.peca.PecaRepository;
import io.github.gabrielivo.oficina.domain.usuario.Usuario;
import io.github.gabrielivo.oficina.domain.usuario.UsuarioRepository;

import java.math.BigDecimal;

@Component
public class DataSeeder implements ApplicationRunner {

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
        if (usuarioRepository.findByLogin("admin").isEmpty()) {
            Usuario admin = new Usuario("admin", passwordEncoder.encode("admin123"));
            usuarioRepository.save(admin);
            System.out.println("✅ Usuário admin criado: login=admin | senha=admin123");
        }

        if (pecaRepository.count() == 0) {
            criarPecasExemplo();
        }
    }

    private void criarPecasExemplo() {
        try {
            pecaService.criar(new CriarPecaCommand("Filtro de Óleo", "Filtro de óleo para motores", new BigDecimal("25.50"), 50, 10));
            pecaService.criar(new CriarPecaCommand("Pastilha de Freio", "Pastilha de freio dianteira", new BigDecimal("120.00"), 20, 5));
            pecaService.criar(new CriarPecaCommand("Bateria 60Ah", "Bateria automotiva 60Ah", new BigDecimal("350.00"), 15, 3));
            pecaService.criar(new CriarPecaCommand("Óleo 5W30", "Óleo lubrificante sintético", new BigDecimal("45.00"), 30, 8));
            pecaService.criar(new CriarPecaCommand("Velas de Ignição", "Jogo com 4 velas", new BigDecimal("85.00"), 25, 6));

            System.out.println("✅ Peças de exemplo criadas com controle de estoque");
        } catch (Exception e) {
            System.err.println("❌ Erro ao criar peças de exemplo: " + e.getMessage());
        }
    }}
