package io.github.gabrielivo.oficina.domain.ordemServico;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Uma linha do histórico de status da OS. Só é criada pelo agregado {@link OrdemServico} (toda transição passa por
 * {@code OrdemServico.alterarStatus}) e é imutável.
 */
@Entity
@Table(name = "historico_status_os")
public class HistoricoStatusOrdemServico {

    static final int TAMANHO_MAXIMO_MOTIVO = 255;

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_servico_id", nullable = false)
    private OrdemServico ordemServico;

    /** Posição da linha no histórico da OS (1 = abertura); desempata transições no mesmo instante. */
    @Column(nullable = false)
    private int sequencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", length = 30)
    private StatusOrdemServico statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false, length = 30)
    private StatusOrdemServico statusNovo;

    @Column(name = "alterado_em", nullable = false)
    private LocalDateTime alteradoEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemMudancaStatus origem;

    @Column(length = TAMANHO_MAXIMO_MOTIVO)
    private String motivo;

    protected HistoricoStatusOrdemServico() {}

    HistoricoStatusOrdemServico(
            OrdemServico ordemServico,
            int sequencia,
            StatusOrdemServico statusAnterior,
            StatusOrdemServico statusNovo,
            LocalDateTime alteradoEm,
            OrigemMudancaStatus origem,
            String motivo) {
        this.id = UUID.randomUUID().toString();
        this.ordemServico = ordemServico;
        this.sequencia = sequencia;
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        this.alteradoEm = alteradoEm;
        this.origem = origem;
        this.motivo = motivo;
    }

    public String getId() {
        return id;
    }

    public int getSequencia() {
        return sequencia;
    }

    public StatusOrdemServico getStatusAnterior() {
        return statusAnterior;
    }

    public StatusOrdemServico getStatusNovo() {
        return statusNovo;
    }

    public LocalDateTime getAlteradoEm() {
        return alteradoEm;
    }

    public OrigemMudancaStatus getOrigem() {
        return origem;
    }

    public String getMotivo() {
        return motivo;
    }
}
