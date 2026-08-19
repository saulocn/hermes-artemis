# contracts/

O que atravessa o limite de um módulo, escrito uma vez.

Os três serviços Java são projetos Maven independentes de propósito: cada um constrói e
sobe sozinho. Isso significa que nenhum deles pode depender de uma classe do outro — então
o contrato entre eles não pode ser uma interface Java. Aqui ele é um arquivo, e os testes
dos dois lados leem o mesmo arquivo.

Nenhum acoplamento de build. A divergência, que antes só aparecia em produção, passa a
quebrar um teste.

## Os arquivos

| Arquivo | Atravessa | Quem escreve | Quem lê |
|---|---|---|---|
| `recipient-vo.json` | o broker (AMQP) | hermes-enqueuer | hermes-mailer |
| `mail-vo.json` | o Redis | hermes-api | hermes-mailer |
| `message-cache.properties` | o Redis (chave e TTL) | hermes-api | hermes-mailer |
| `recipient-states.json` | HTTP `/admin/recipients?state=` | hermes-api | hermes-web |
| `jobs.json` | HTTP `/admin/jobs/{job}` | hermes-api | hermes-web, hermes-enqueuer |

O esquema do banco não está aqui: ele já tem uma fonte única em `db/*.sql`. Os testes de
esquema de cada módulo leem aqueles arquivos.

## A regra

Um teste que lê um arquivo daqui **não pode** ter os valores esperados escritos também no
próprio teste — isso seria a sexta cópia. Ele lê o arquivo e compara com o que o código de
produção produz.

Mudar um contrato é mudar este diretório e ver os dois lados quebrarem juntos. É esse o
ponto.
