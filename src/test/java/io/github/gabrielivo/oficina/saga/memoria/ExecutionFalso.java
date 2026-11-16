package io.github.gabrielivo.oficina.saga.memoria;

import io.github.gabrielivo.oficina.application.mensageria.EnvelopeMensagem;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Execution Service falso. Reage sozinho aos comandos que o serviço real trata sem intervenção humana
 * ({@code IniciarExecucao}, {@code LiberarReservaPecas}, {@code CancelarExecucao}); o diagnóstico e a finalização,
 * que no serviço real dependem do mecânico, são disparados pelo teste. {@link #recusarInicioExecucao()} imita
 * {@code app.simulacao.recusar-inicio-execucao=true}.
 */
public final class ExecutionFalso extends ParticipanteFalso {

    private final Map<String, String> reservas = new HashMap<>();
    private final Map<String, String> tarefas = new HashMap<>();
    private boolean recusarInicioExecucao;

    public ExecutionFalso(CatalogoContratos catalogo, Consumer<EnvelopeMensagem> publicarEvento) {
        super(catalogo, publicarEvento);
    }

    @Override
    public String fila() {
        return "execution.comandos";
    }

    public void recusarInicioExecucao() {
        this.recusarInicioExecucao = true;
    }

    /** O mecânico inicia e conclui o diagnóstico; o estoque fica reservado. */
    public void concluirDiagnostico(String osId) {
        if (!recebeu(osId, "SolicitarDiagnostico")) {
            throw new IllegalStateException("O Execution não recebeu SolicitarDiagnostico para a OS " + osId);
        }
        String tarefaId = tarefa(osId);
        publicar(catalogo.evento("DiagnosticoIniciado", osId, Map.of("tarefaId", tarefaId)));
        publicar(catalogo.evento(
                "DiagnosticoConcluido", osId, Map.of("tarefaId", tarefaId, "reservaId", reserva(osId))));
    }

    /** O mecânico finaliza o reparo. */
    public void finalizarExecucao(String osId) {
        if (!recebeu(osId, "IniciarExecucao")) {
            throw new IllegalStateException("O Execution não recebeu IniciarExecucao para a OS " + osId);
        }
        publicar(catalogo.evento("ExecucaoFinalizada", osId, Map.of("tarefaId", tarefa(osId))));
    }

    @Override
    protected void reagir(EnvelopeMensagem comando) {
        String osId = comando.osId();
        switch (comando.type()) {
            case "SolicitarDiagnostico" -> tarefa(osId);
            case "IniciarExecucao" -> {
                if (recusarInicioExecucao) {
                    publicar(catalogo.evento("ExecucaoRecusada", osId, Map.of("reservaId", reserva(osId))));
                } else {
                    publicar(catalogo.evento(
                            "ExecucaoIniciada", osId, Map.of("tarefaId", tarefa(osId), "reservaId", reserva(osId))));
                }
            }
            case "LiberarReservaPecas" ->
                publicar(catalogo.evento("ReservaPecasLiberada", osId, Map.of("reservaId", reserva(osId))));
            case "CancelarExecucao" ->
                publicar(catalogo.evento(
                        "ExecucaoCancelada", osId, Map.of("tarefaId", tarefa(osId), "reservaId", reserva(osId))));
            default -> throw new IllegalArgumentException("Comando inesperado no Execution: " + comando.type());
        }
    }

    private String reserva(String osId) {
        return reservas.computeIfAbsent(osId, id -> UUID.randomUUID().toString());
    }

    private String tarefa(String osId) {
        return tarefas.computeIfAbsent(osId, id -> UUID.randomUUID().toString());
    }
}
