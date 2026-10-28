# Padrão administrativo v1.0 — pratoja

> Plano de homologação. A existência de código não comprova contas provisionadas nem funcionamento em produção.

## Inventário técnico inicial
- `src/main/java/br/com/pratoja/pratoja/config/SecurityConfig.java`
- `src/main/java/br/com/pratoja/pratoja/web/AdminController.java`

## Foco específico
Validar PLATFORM_ADMIN/STORE_ADMIN, isolamento de loja e CSRF.

## Requisitos comuns
- [ ] Confirmar se painel é necessário e documentar sua URL/rota.
- [ ] Identificar mecanismo de criação/recuperação de administrador sem registrar senhas, tokens ou dados pessoais.
- [ ] Validar login, logout, expiração, troca de senha e acesso negado.
- [ ] Verificar autorização de cada operação no backend ou nas políticas do banco; esconder botões não basta.
- [ ] Testar menus, voltar, fechar, filtros, paginação, formulários, estados vazios e mensagens de erro onde aplicável.
- [ ] Testar desktop e celular, foco de teclado e contraste.
- [ ] Preservar fluxos existentes e identidade visual; evitar novos módulos sem necessidade.
- [ ] Executar testes automatizados existentes e acrescentar regressão para falhas comprovadas.
- [ ] Registrar evidências da homologação em ambiente autorizado.

## Regras de execução
1. Não executar bootstrap de administrador ou reset de senha em produção sem autorização e plano de recuperação.
2. Corrigir somente lacunas demonstradas por evidência e registrar testes no PR.
3. Não fazer merge, migration ou deploy antes de homologação e aprovação.

## Estado
Inventário estático realizado; homologação operacional e credenciais reais **não verificadas**.
