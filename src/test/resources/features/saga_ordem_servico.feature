# language: pt
Funcionalidade: Orquestração da Saga de ordem de serviço
  A Saga do OS Service coordena diagnóstico, orçamento, cobrança e execução trocando comandos e eventos
  com o Execution Service e o Billing Service. Quando uma etapa falha, ela dispara as compensações
  e a OS termina cancelada sem deixar peças reservadas nem pagamento retido.

  Cenário: Fluxo completo até a entrega
    Dado que o cliente abriu uma OS
    Quando o mecânico conclui o diagnóstico
    E o cliente aprova o orçamento
    E o cliente paga a cobrança
    E o mecânico finaliza a execução
    E a OS é entregue ao cliente
    Então a OS fica no status "ENTREGUE"
    E a Saga fica na situação "CONCLUIDA"

  Cenário: Orçamento recusado libera a reserva antes do cancelamento
    Dado que o cliente abriu uma OS
    Quando o mecânico conclui o diagnóstico
    E o cliente recusa o orçamento
    Então o Execution Service recebe o comando "LiberarReservaPecas"
    E a OS fica no status "CANCELADA"
    E a Saga fica na situação "COMPENSADA"

  Cenário: Pagamento não confirmado no prazo cancela a cobrança e libera a reserva
    Dado que as cobranças vencem assim que são criadas
    E que o cliente abriu uma OS
    Quando o mecânico conclui o diagnóstico
    E o cliente aprova o orçamento
    E o prazo de pagamento expira sem confirmação
    Então o Billing Service recebe o comando "CancelarCobranca"
    E o Execution Service recebe o comando "LiberarReservaPecas"
    E a OS fica no status "CANCELADA"
    E a Saga fica na situação "COMPENSADA"

  Cenário: Execução recusada estorna o pagamento e libera a reserva
    Dado que o Execution Service recusa o início da execução
    E que o cliente abriu uma OS
    Quando o mecânico conclui o diagnóstico
    E o cliente aprova o orçamento
    E o cliente paga a cobrança
    Então o Billing Service recebe o comando "EstornarPagamento"
    E o Execution Service recebe o comando "LiberarReservaPecas"
    E a OS fica no status "CANCELADA"
    E a Saga fica na situação "COMPENSADA"
