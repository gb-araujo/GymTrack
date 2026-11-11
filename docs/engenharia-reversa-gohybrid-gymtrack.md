# Engenharia reversa: GO HYBRID e GymTrack

Data: 2026-11-11. Ferramenta: REA (rea-agents 6.8.0) via MCP, com Chrome 157 em perfil temporário.
Objetivo: levantar posicionamento, stack, rotas, API, modelo de dados e funcionalidades das duas plataformas para servir de base a uma plataforma nova.

Tudo o que está aqui veio de páginas públicas (sem login). Cada seção separa o que foi **observado** (DOM, rede, screenshots), o que foi **inferido** dos bundles JavaScript (estático, nunca executado) e o que ficou **desconhecido**.

Evidência REA retida em `scratchpad/rea-evidence.json` (16 MB, 16 registros). IDs principais:

| Evidência | Conteúdo |
|---|---|
| `ev_b8e4492a…` | GO HYBRID home (screenshot, a11y, rede) |
| `ev_fdc0b31b…` | GO HYBRID /visao, /casos, /app, /mapa/ |
| `ev_c4aa9ea3…` | GO HYBRID /diagnostico/, /protocolo, /mapa/ |
| `ev_c2862dce…` | GO HYBRID bundle JS (rotas estáticas) |
| `ev_33d8c137…` | GymTrack home (screenshot, a11y, rede) |
| `ev_cb4a3bfe…` | GymTrack /login/, /cadastro/, /cookies/ |
| `ev_8191aa38…` | GymTrack assistente de cadastro passo 2, /privacidade/, /precisa-de-ajuda/, /esqueceu-senha/ |
| `ev_9bacce4e…` | GymTrack bundle JS (21 chunks, rotas estáticas) |

Capturas de tela: `docs/reverse-engineering/screenshots/`.

---

## 1. Resumo executivo

| | GO HYBRID | GymTrack |
|---|---|---|
| O que é | Consultoria de treino + alimentação do João Scar (acompanhamento 1:1 e "Hybrid One" com equipe por 12 meses). O site é um funil de captação; o "Hybrid App" é o produto de execução diária. | SaaS B2B2C para personal trainers gerirem alunos, fichas, avaliações, dietas e cardio; o aluno usa app grátis. |
| Modelo de receita | Venda consultiva pelo WhatsApp (lead qualificado por quiz). | Assinatura do personal por faixa de alunos ativos (Stripe); aluno não paga; sem fidelidade. |
| Público | Pessoa física com rotina apertada (empresários, criadores de conteúdo). | Personal trainer (presencial e online) e seus alunos; há perfil admin. |
| Front-end | Vite + React, SPA multi-página estática (home, visão, casos, app) + 3 mini-apps separados (Hybrid Map, Diagnóstico, Protocolo). | Next.js (App Router, Turbopack, RSC), React, Radix UI, Recharts, zod, axios, lucide. PWA + wrapper Capacitor (iOS). |
| Back-end visível | `/api/events` (analytics própria) e `/api/leads`; texto de privacidade cita Supabase. | API REST em `https://gymtrack.com.br/api` (JWT em cookie `gymtrack-token`), Google OAuth, X OAuth, PostHog, Stripe. |
| Diferencial a copiar | Funil por questionário que gera um plano preliminar (Map/Diagnóstico) e vira lead; promessa de plano adaptativo (GIA, Plano B de 15 min); alimentação por foto e scanner. | Operação completa do personal (dashboard de retenção, radar de vencimentos, hall da fama, calendário), onboarding por perfil, treino offline, execução com cronômetro e registro de cargas. |

---

## 2. GO HYBRID (www.gohybrid.com.br)

### 2.1 Posicionamento e narrativa (observado)

- Headline da home: "Seu corpo / Sua rotina / Seu plano". Subtítulo: "Treino, alimentação e ajustes com uma equipe acompanhando sua evolução".
- CTAs: "Quero fazer parte do time" (WhatsApp +55 41 8496-1012 com mensagem pré-preenchida) e "Fazer Hybrid Map grátis".
- Produtos descritos em /visao ("O sistema GO HYBRID"):
  1. **Hybrid Map**: ponto de partida gratuito (quiz).
  2. **Hybrid App**: execução diária (treino, alimentação, registros).
  3. **Hybrid One**: 12 meses com personal, nutricionista e fisioterapeuta.
- Prova social em /casos: Peter Jordan, Avelino Morganti (−20 kg), Álvaro (−50 kg), Nanda Guardian, Thiago Azevedo, Victor Trindade; "1.900+ pessoas acompanhadas", "8+ anos", "12,92 toneladas" estimadas (1.900 × 6,8 kg).
- Conteúdo editorial: 6 leituras em /visao/* e 5 guias em /guias/* (SEO: treino híbrido, corrida + musculação, agenda imprevisível).

### 2.2 Rotas (observado + bundle)

| Rota | Tipo | Observação |
|---|---|---|
| `/`, `/visao`, `/casos`, `/app`, `/privacidade`, `/termos` | SPA principal (`assets/main-*.js`) | Roteamento por `history.pushState`. |
| `/visao/<slug>` (6) e `/guias/<slug>` (5) | Conteúdo | `seo-cluster-*.js`. |
| `/casos/peter-jordan`, `/casos/avelino-morganti` | Estudos de caso | |
| `/mapa/` | Mini-app separado (`assets/mapReact-*.js`) | Hybrid Map, 20 perguntas. |
| `/diagnostico/` | Mini-app separado (`diagnostico/assets/index-*.js`), `noindex` | 9 seções, mais longo. |
| `/protocolo` | Formulário de intake, `noindex` | "Seis partes, uma pergunta por vez, progresso salvo por 30 dias". Alias `/zeitgeist`. |
| `/admin` | Redireciona para `/` | Existe no router, acesso bloqueado no cliente. |
| `/ferramentas`, `/calculadoras`, `/jornadas` | Legado | Redirecionam para `/mapa/`. |

### 2.3 Funil de captação (observado)

**Hybrid Map** (20 passos, `01 / 20`): sexo, peso, altura, nome, "quanto movimento faz parte do seu dia", ocupação e horas/dias de trabalho, horas sentado, prioridade atual, relação com atividade física, há quanto tempo (começar / manter / estagnado / alternando), o que mais atrapalha, variação de horários, dias por semana disponíveis, minutos por treino, como percebe evolução, alimentação na rotina, horas de sono, como acorda, o que preserva quando a rotina muda. Termina com captura de lead (primeiro nome, e-mail, WhatsApp com DDD).

Saída do Map (inferido do bundle): "Sua estrutura para os próximos 21 dias": mapa semanal (`week`, `strengthDays`, sessões de cardio), calorias de manutenção e alvo (`maintenance`, `target`, `adjustment`), proteína, recuperação, data da primeira revisão (`reviewDate`), sugestão de suplementos (creatina, whey), seções "Adaptabilidade da rotina / Progressão do treino / Direção alimentar / Recuperação / Base repetível / Próximo avanço".

**Diagnóstico** (9 seções: "Seus objetivos", "Seu ponto de partida", "Seu dia", "Seu cuidado"…): além do Map, pergunta objetivo (gordura / massa / ambos / definição), onde quer performar (força / corrida / esporte / dia a dia), padrão de abandono, condições femininas (ciclo, menopausa, gravidez, pós-parto, amamentação), dor e limitação, passos por dia, energia ao longo do dia, jejum, padrão alimentar, dias de treino nas últimas 2 semanas, duração, variabilidade da agenda, barreiras alimentares, qualidade do despertar, peso-alvo, e "o que você quer ao terminar" (saber o que fazer amanhã / entender por que não rende / ter um time / só explorar).

**Protocolo**: formulário de intake para quem já fechou (6 partes), envia `POST /api/leads` com `type: "protocol-intake"` e `clientSubmissionId` (idempotência gerada no cliente).

**Formulário em /app**: nome, e-mail, WhatsApp, consentimento, botão "Salvar e abrir WhatsApp" (lead identificado como interesse no App).

### 2.4 Hybrid App (observado em /app, só descrição; o app não é acessível)

- Treino: vídeos por exercício, ajustado aos equipamentos da academia, "Plano B de 15 minutos", força + mobilidade + recuperação.
- Alimentação: foto do prato com estimativa de calorias/proteína, scanner de código de barras, lista de compras gerada do plano, acompanhamento de refeições/proteína.
- Evolução: "Plano vivo", "Memória operacional" (lembra academia, preferências, substituições, limitações), "Execution Pulse" (treino, proteína, água, sono, medidas viram um sinal de execução).
- GIA: assistente que propõe alternativa ("Tenho pouco tempo" → "treino curto de 12 min") e só altera o plano após confirmação.
- Fluxo declarado: Diagnóstico monta hipótese inicial → App executa, registra e prepara o próximo ajuste.

### 2.5 Stack, dados e analytics

- Observado: Vite (hashes `-XXXXXXXX.js`), fontes Manrope, Instrument Serif, DM Sans, Space Grotesk, Inter (Google Fonts), `site.webmanifest`, imagens WebP.
- Observado: `POST /api/events` (status 202) em cada página, com `keepalive` e fila local em `localStorage` (últimos 120 eventos). Evento `page_view` com `page`, `journeyId`, `toolId`.
- Inferido: `POST /api/leads` com retorno de `savedSubmissionId`; UTM e referrer guardados em `sessionStorage`.
- Observado (texto de privacidade): "o banco Supabase oferece criptografia em trânsito e em repouso. O painel exige autenticação e permissões por função".
- Design: fundo escuro com fotografia editorial de corredores, vermelho (#E53935 aprox.) como cor de ação, tipografia display grande, microcopy em caixa alta ("GO HYBRID · CASOS E RELATOS").

### 2.6 Desconhecido

- Nada do Hybrid App em si foi observado (só marketing). Backend do Supabase, schema, autenticação e preço do Hybrid One não são públicos.
- O chunk `HybridAppOffer-*.js` (192 KB) estourou o prazo de análise estática do REA; é o componente de oferta do app.

---

## 3. GymTrack (gymtrack.com.br)

### 3.1 Posicionamento (observado)

- Headline: "Transforme seus treinos com GymTrack. A plataforma completa para personal trainers e atletas."
- Números exibidos: 10k+ usuários, 50k+ treinos, 95% satisfação (não verificáveis).
- CTAs: "Começar Gratuitamente" (/cadastro/), "Já tenho uma conta" (/login/), "Sem cartão de crédito • Cancele quando quiser".
- Contato: ggprojetosmuriae@gmail.com (empresa de Muriaé/MG). Política de privacidade atualizada em outubro de 2026, cita LGPD, dados sensíveis de saúde, bioimpedância, fotos de progresso, assistente virtual de suporte.
- FAQ (texto no bundle): aluno não paga; atende presencial e online; sem fidelidade, cancela pelo painel; fichas de qualquer modalidade (crossfit, funcional, mobilidade, cardio) com métodos (bi-set, drop-set); ao ultrapassar o limite de alunos do plano o uso continua e o sistema sugere upgrade.

### 3.2 Perfis de usuário e mapa de rotas (inferido do bundle `04y-rltdo3tmb.js`, alta confiança: é a tabela de autorização do cliente)

`grupo_usuario`: **1 = admin**, **2 = personal**, **3 = aluno**. Home de cada um: `/dashboard/admin`, `/dashboard/personal`, `/dashboard/aluno`. Rotas públicas: `/`, `/login`, `/cadastro`, `/esqueceu-senha`, `/redefinir-senha`, `/confirmar-email`.

| Admin (1) | Personal (2) | Aluno (3) |
|---|---|---|
| /dashboard/admin | /dashboard/personal | /dashboard/aluno |
| /usuarios | /alunos | /ficha-atual |
| /relatorios | /fichas, /fichas-alunos, /previa-ficha, /ficha-atual | /fichas, /previa-ficha |
| /configuracoes | /avaliacao, /avaliacao-alunos | /avaliacao |
| /dashboard/admin/exercicios | /exercicios, /metodologias | /treino |
| /dashboard/admin/alimentos | /alimentos, /dietas-alunos, /dieta-atual, /dietas, /diario-alimentar | /dieta-atual, /dietas, /diario-alimentar |
| /dashboard/admin/seguranca | /modelos-anamnese | /cardio |
| /comunidade | /treino, /cardio, /historicoPeso, /balanco, /metas, /conquistas | /historicoPeso, /balanco, /metas, /conquistas |
| /perfil, /ajuda | /planos-personal, /planos-aluno, /experimente-gratis | /planos-aluno, /experimente-gratis |
| | /meus-feedbacks, /perfil, /ajuda, /comunidade | /meus-feedbacks, /perfil, /ajuda, /comunidade |

O personal também tem acesso a todas as rotas do aluno (cookie `gymtrack-viewMode` alterna a visão). Há módulo de **dieta e diário alimentar** e **comunidade**, que não aparecem no marketing.

### 3.3 API (inferido: chamadas axios no bundle; base `https://gymtrack.com.br/api`, `withCredentials`, timeout 30 s)

| Método | Rota | Uso |
|---|---|---|
| POST | /usuario/register | cadastro (envia `anonymous_id` se houver consentimento de analytics) |
| POST | /usuario/login | retorna `token` + `usuario` (`id_usuario`, `nome_usuario`, `grupo_usuario`, `cadastro_pendente`, `aguardando_confirmacao`, `aviso_vinculo`) |
| POST | /usuario/oauth/{provider} | Google e X (OAuth2 PKCE no cliente, troca de `id_token`/`code`) |
| GET | /usuario/getUser | revalida sessão no boot |
| PUT | /usuario/editar | perfil |
| POST | /usuario/logout | |
| GET / POST | /usuario/convite-personal, /usuario/convite-personal/vincular | vínculo aluno ↔ personal por `personal_invite_token` |
| POST | /email/confirmar, /email/reenviar, /email/corrigir | confirmação de e-mail |
| POST | /senha/email, /senha/reset | recuperação |
| POST | /treino/iniciar | abre `id_sessao_treino` (`iniciado_em`) |
| POST | /treino/registrar-exercicio | registra série/carga durante o treino |
| POST | /treino/checkin/{id}, /treino/finalizar/{id} | `finalizado_em`, `conquistas_desbloqueadas` |
| DELETE | /treino/cancelar/{id} | |
| POST | /analytics/event, /analytics/pageview | analytics própria (além de PostHog) |
| POST | /privacidade/consentimentos | `cookies_analytics`, `marketing_aceito`, `versao_termos`, `versao_politica`, `versao_cookies` |

Autenticação: JWT guardado em cookie `gymtrack-token` (90 dias, `secure`, `sameSite=lax`); interceptor renova o token quando a resposta traz `Authorization` novo; em 401 o cliente apaga o cookie. A política de cookies diz 7 dias, o código grava 90.

### 3.4 Modelo de dados (inferido das chaves snake_case em português no bundle)

Usuário: `id_usuario`, `nome_usuario`, `senha_usuario`, `telefone_usuario`, `data_nascimento_usuario`, `sexo_usuario`, `grupo_usuario`, `personal_usuario` (FK do aluno para o personal), `data_cadastro_usuario`, `ultimo_acesso_em`, `profile_image_url`, `logo_url` (marca do personal nas fichas), `menor_de_idade`, `receber_promocoes`, `receber_avisos_produto`.

Assinatura/onboarding: `plano_efetivo`, `possui_assinatura_stripe`, `data_fim_assinatura`, `usou_trial_gratuito`, `completou_onboarding`, `visualizou_modal_boas_vindas`, `usou_importacao_ia` (importação de ficha por IA), `convite_beta_fichas`.

Treino: `id_ficha`, `usuario_ficha`, `id_sessao_treino`, `iniciado_em`, `finalizado_em`, `conduzido_por`, `conquistas_desbloqueadas`.

Convenção: API em português, snake_case, sufixo `_usuario` nas colunas da tabela de usuário (padrão típico de PHP/Laravel ou Node com MySQL; o host alternativo `http://127.0.0.1:8000/api` no bundle sugere Laravel ou FastAPI em dev).

### 3.5 Onboarding (observado)

- Login: e-mail + senha, "Lembrar de mim", "Entrar com Google", links para cadastro, esqueci a senha e "Precisa de ajuda?".
- Cadastro: assistente de **7 passos** (vira 8 ao escolher personal): 1) perfil (aluno/atleta ou personal trainer, ou "Continuar com Google"); 2) nome completo com validação inline ("Corrija os campos destacados"). Passos seguintes não observados (exigiriam criar conta).
- /precisa-de-ajuda/: árvore de problemas de acesso (senha, e-mail não chegou, link vencido, muitas tentativas, conta via Google/Apple, conta excluída, cadastro, suporte técnico).
- Banner de cookies com três opções (aceitar, recusar não essenciais, gerenciar); PostHog só recebe eventos com consentimento; `gymtrack-anonymous-id` em localStorage.

### 3.6 Funcionalidades mostradas nas demos da landing (observado; são mockups renderizados em HTML, não dados reais)

**Dashboard do personal**: alerta para configurar logomarca; cards "Alunos ativos 24 de 30", "Renovações 3 (treinos vencendo em 15 dias)", "Avaliações atrasadas 2", "Aniversariantes 1"; **Alerta de evasão** (alunos sem treinar há 7+ dias, "Crítico: 12d", botão "Chamar"); **Renovação de treinos** (próximos 15 dias, com nome da ficha e vencimento); **Radar de atenção** (treino vencido / avaliação atrasada, botão "Resolver"); **Próximas avaliações** (botão "Registrar"); **Hall da Fama** (abas Consistência e Reis da Força, pódio por treinos em 30 dias); **Calendário operacional** (frequência, vencimentos, aniversários) com "Visão do mês"; gráfico "Evolução de treinos" dos últimos 7 dias.

**Dashboard do aluno**: "Ficha atual" (nome, objetivo "Hipertrofia A", Treino A com exercícios "1 séries x 15 - 5kg"), botões "Iniciar treino" e "Ver/Planejar ficha"; **Sequência de treinos** (streak em dias); **Cardio da semana** (km, min, dias, sessões, "Registrar cardio", metas); **Próxima avaliação** (data, dias restantes, progresso 45/90 dias); **Peso atual** com variação desde o último registro e desde o início.

### 3.7 Capacidades técnicas inferidas do bundle

- **Offline**: cache local da ficha do usuário (`loadFichaOfflineCacheDoUsuario`), tela de boot com "Abrir treino offline" que leva a `/treino/ativo`, service worker, ícones "WifiOff".
- **Cronômetro de descanso sincronizado** (`sincronizarRelogio`, `limparRelogio`) ligado ao token e ao nome do usuário.
- **Lembretes de refeição** (`cancelarLembretesRefeicao`) e módulo de dieta.
- **App nativo**: Capacitor (`isIOSApp`, `isNativeApp`), menções a Expo; login com Apple citado na ajuda.
- **Importação de ficha por IA** (`usou_importacao_ia`) e **assistente virtual de suporte** (política de privacidade).
- Feature flag `VENDA_ATIVA = false` no bundle público.

### 3.8 Desconhecido

- Preços e faixas de alunos por plano (rotas `/planos-personal`, `/experimente-gratis` exigem login).
- Passos 3 a 8 do cadastro, estrutura real de ficha/exercício/série, avaliação física, dieta: só os nomes das rotas e campos.
- Servidor: linguagem, banco e regras (o chunk `04jvxoid~vpxj.js` estourou o prazo de análise estática).

---

## 4. Comparação e o que aproveitar na plataforma nova

### 4.1 O que cada uma faz melhor

- GO HYBRID **adquire** melhor: quiz gratuito que devolve valor imediato (plano de 21 dias com calorias e semana montada) e transforma o visitante em lead com contexto (objetivo, rotina, barreiras). Narrativa de "plano que se adapta à vida real".
- GymTrack **opera** melhor: separação clara de perfis, dashboard orientado a retenção do negócio do personal (evasão, vencimentos, renovações), execução de treino com cronômetro e cargas, offline, vínculo por convite, comunidade, dieta.

### 4.2 Blueprint sugerido

**Perfis**: aluno, personal (com visão de aluno), admin. Adicionar "equipe" (nutricionista/fisio) se for seguir o modelo Hybrid One.

**Domínio mínimo**: usuário, vínculo personal↔aluno (por convite), ficha (versão, validade, objetivo) → treinos (A/B/C) → exercícios (biblioteca global + do personal, vídeo, método) → séries (reps, carga, descanso); sessão de treino (início, fim, registros por série, cancelamento); avaliação física (periodicidade, medidas, bioimpedância, fotos); peso/histórico; cardio (sessões, metas semanais); metas e conquistas; dieta/diário alimentar; consentimentos LGPD versionados.

**Funil (do GO HYBRID)**: questionário público de 15 a 20 perguntas → resultado com plano preliminar e calorias → cria conta do aluno já preenchida (onboarding curto) → opcional vínculo a um personal. Guardar `clientSubmissionId` para idempotência e UTM/referrer.

**Operação (do GymTrack)**: dashboard do personal com alertas de evasão (X dias sem treinar), vencimentos de ficha em 15 dias, avaliações atrasadas, aniversários, ranking de consistência, calendário; dashboard do aluno com ficha atual, streak, cardio da semana, próxima avaliação, peso.

**Adaptação (do GO HYBRID, diferencial)**: "Plano B" curto para cada treino; assistente que propõe troca de treino a partir de contexto (tempo, energia, academia) com confirmação do usuário; registro de alimentação por foto/código de barras.

**Técnico**: Next.js ou similar com PWA e offline para a sessão de treino; API REST com JWT em cookie httpOnly (o GymTrack lê o cookie no JS, o que o torna legível por scripts; prefira httpOnly + rota de refresh); OAuth Google/Apple; analytics própria + PostHog com consentimento; Stripe com planos por faixa de alunos; Supabase ou Postgres gerenciado.

**Evitar**: divergência entre política (7 dias) e código (90 dias) do token; números de prova social sem fonte; feature flags de venda expostas no bundle público.

---

## 5. Método e limitações

- `capture_browser_scenario` em modo launch (Chrome temporário, pt-BR, 1366×900) para home e páginas secundárias; `analyze_web_bundle` sobre um Chrome com porta de depuração para rotas estáticas; grep local sobre os bundles baixados para endpoints, chaves de dados e textos.
- Nenhuma ação foi feita além de navegar, clicar em opções do assistente de cadastro (sem enviar formulário) e escolher a primeira opção do Hybrid Map. Nenhuma conta foi criada, nenhum lead enviado.
- Rotas e endpoints do bundle são candidatos estáticos: o REA não executa JavaScript e não prova alcançabilidade nem comportamento do servidor.
- Para ir além (passos 3 a 8 do cadastro, telas internas, planos e preços) é preciso criar uma conta de teste no GymTrack e, no GO HYBRID, completar o Map/Diagnóstico com dados fictícios, o que gera um lead real para a equipe deles. Decisão sua.
