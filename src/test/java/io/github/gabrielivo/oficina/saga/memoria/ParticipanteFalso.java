package io.github.gabrielivo.oficina.saga.memoria;

import io.github.gabrielivo.oficina.application.mensageria.EnvelopeMensagem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base dos participantes falsos da Saga (Execution e Billing): recebem os comandos entregues pelo
 * {@link BarramentoEmMemoria}, guardam o que receberam e respondem publicando eventos do catálogo.
 */
public abstract class ParticipanteFalso {

    private final List<EnvelopeMensagem> comandosRecebidos = new ArrayList<>();
    protected final CatalogoContratos catalogo;
    private final Consumer<EnvelopeMensagem> publicarEvento;

    protected ParticipanteFalso(CatalogoContratos catalogo, Consumer<EnvelopeMensagem> publicarEvento) {
        this.catalogo = catalogo;
        this.publicarEvento = publicarEvento;
    }

    /** Fila consumida pelo participante (routing key do comando em {@code oficina.comandos}). */
    public abstract String fila();

    final void receber(EnvelopeMensagem comando) {
        comandosRecebidos.add(comando);
        reagir(comando);
    }

    protected abstract void reagir(EnvelopeMensagem comando);

    protected final void publicar(EnvelopeMensagem evento) {
        publicarEvento.accept(evento);
    }

    /** Tipos dos comandos recebidos para a OS, na ordem de chegada. */
    public List<String> comandosRecebidos(String osId) {
        return comandosRecebidos.stream()
                .filter(comando -> osId.equals(comando.osId()))
                .map(EnvelopeMensagem::type)
                .toList();
    }

    protected final boolean recebeu(String osId, String tipo) {
        return comandosRecebidos(osId).contains(tipo);
    }
}
