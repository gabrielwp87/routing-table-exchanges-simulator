# README.md

## Projeto: Simulador de Troca de Tabelas de Roteamento e Envio de Mensagens

### Descrição
Este projeto implementa um simulador que troca tabelas de roteamento e permite o envio de mensagens de texto entre roteadores, utilizando sockets UDP. A aplicação suporta:
- Troca de tabelas de roteamento a cada 15 segundos.
- Atualização dinâmica das tabelas com base em mensagens recebidas.
- Roteamento de mensagens de texto com base nas tabelas.

O projeto foi desenvolvido seguindo as especificações descritas no trabalho final do curso.

---

### Como Rodar

1. **Compile os arquivos Java:**
   ```bash
   javac UDPServer.java Route.java
   ```

2. **Execute os roteadores:**
   Cada roteador requer um IP local e um arquivo de configuração. Exemplos:
   ```bash
   java UDPServer 127.0.0.1 configs/R1.txt
   java UDPServer 127.0.0.2 configs/R2.txt
   java UDPServer 127.0.0.3 configs/R3.txt
   ```

   > **Nota:** Os arquivos de configuração devem conter os IPs dos vizinhos do roteador, com um IP por linha.

---

### Protocolo de Comunicação

A aplicação utiliza dois tipos de mensagens:

1. **Anúncio de Rotas:**
   - Formato: `!IP:Métrica`
   - Exemplo: `!192.168.1.2:1!192.168.1.3:1`

2. **Anúncio de Roteador:**
   - Formato: `@IP`
   - Exemplo: `@192.168.1.1`

3. **Mensagens de Texto:**
   - Formato: `&IP_Origem%IP_Destino%Mensagem`
   - Exemplo: `&192.168.1.2%192.168.1.1%Oi tudo bem?`

---

### Funcionalidades

- Inicialização com arquivo de configuração para definição de vizinhos.
- Troca automática de tabelas de roteamento.
- Atualização de métricas e remoção de rotas inativas após 35 segundos.
- Roteamento de mensagens de texto entre roteadores.

---

### Requisitos

- **Java**: Versão 8 ou superior.
- **Configurações de Rede**: Cada máquina deve ter seu IP configurado no arquivo de inicialização.

---

### Observações Importantes

- Certifique-se de que a porta **19000** esteja disponível em todas as máquinas.
- Testes devem ser realizados em no mínimo 4 máquinas.
- A interoperabilidade entre grupos será avaliada, então siga rigorosamente o protocolo especificado.

---

### Autor(es)
Gustavo Willian Martins da Silva, Gabriel Wagner e Lorenzo More
