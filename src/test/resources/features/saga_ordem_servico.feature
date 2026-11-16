# language: pt
Funcionalidade: Orquestração da Saga de ordem de serviço
  A Saga coordena diagnóstico, orçamento, cobrança, execução e compensações.

  Cenário: Fluxo completo até a entrega
    Dado que uma OS foi aberta
    Quando o diagnóstico é concluído e o orçamento é gerado
    E o orçamento é aprovado
    E a cobrança é criada, paga e a execução é finalizada
    E a OS é entregue ao cliente
    Então a OS fica no status "ENTREGUE"
    E a Saga fica na situação "CONCLUIDA"

  Cenário: Orçamento recusado libera a reserva antes do cancelamento
    Dado que uma OS foi aberta
    Quando o diagnóstico é concluído e o orçamento é gerado
    E o orçamento é recusado
    E a reserva de peças é liberada
    Então a OS fica no status "CANCELADA"
    E a Saga fica na situação "COMPENSADA"

  Cenário: Pagamento expirado cancela a cobrança e libera a reserva
    Dado que uma OS foi aberta
    Quando a cobrança aguarda pagamento
    E o prazo do pagamento expira
    E a cobrança é cancelada
    E a reserva de peças é liberada
    Então a OS fica no status "CANCELADA"
    E a Saga fica na situação "COMPENSADA"

  Cenário: Execução recusada estorna o pagamento e libera a reserva
    Dado que uma OS foi aberta
    Quando a cobrança aguarda pagamento
    E o pagamento é confirmado
    E a execução é recusada
    E o pagamento é estornado
    E a reserva de peças é liberada
    Então a OS fica no status "CANCELADA"
    E a Saga fica na situação "COMPENSADA"
