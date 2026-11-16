package io.github.gabrielivo.oficina.infrastructure.seeder;

import io.github.gabrielivo.oficina.domain.usuario.Usuario;
import io.github.gabrielivo.oficina.domain.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        criarUsuarioAdminSeNecessario();
    }

    private void criarUsuarioAdminSeNecessario() {
        if (usuarioRepository.findByLogin("admin").isPresent()) {
            return;
        }

        Usuario admin = new Usuario("admin", passwordEncoder.encode("admin123"));
        usuarioRepository.save(admin);
        log.info("Usuário admin padrão criado.");
    }
}
