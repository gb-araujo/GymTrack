# Track

App Android nativo de acompanhamento de treinos de musculação, escrito em **Kotlin + Jetpack Compose**.

Reescrito a partir do MVP web (React/TS/Dexie); o app agora é 100% nativo, offline-first, sem WebView.

## Stack

- **Kotlin** + **Jetpack Compose** (Material 3, tema dark)
- **Room** para persistência local (`track.db`)
- **kotlinx.serialization** para as estruturas aninhadas (dias/séries) armazenadas como JSON
- **Navigation Compose** + `ViewModel`/`StateFlow` (padrão MVVM)
- `minSdk` 24 · `targetSdk`/`compileSdk` 35

## Funcionalidades

- **Início** — próximo treino do ciclo, iniciar treino, último treino, recordes pessoais e estatísticas
- **Fichas** — criar/editar/duplicar/ativar/excluir; importar e exportar em JSON (via compartilhamento)
- **Execução** — abas por exercício, timer de descanso, registro de séries (carga/reps), substituição de exercício, sugestão de progressão de carga, resumo ao finalizar
- **Histórico** — sessões agrupadas por mês, com detalhe de cada treino
- **Progresso** — recordes, evolução de carga, volume e frequência (gráficos em Canvas)
- **Metas** — metas de treinos/frequência/volume com barra de progresso

Na primeira execução, o banco é semeado com ~45 exercícios e uma ficha exemplo "Hipertrofia" (A/B/C).

## Build

Requer o Android SDK (`local.properties` aponta para `sdk.dir`) e JDK 17.

```bash
./gradlew :app:assembleDebug      # gera o APK de debug
./gradlew installDebug            # instala em um dispositivo/emulador conectado
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

## Estrutura

```
app/src/main/java/com/gabriel/track/
├── data/            # modelos, entidades Room, DAO, conversores, seed, repositório
└── ui/
    ├── theme/       # paleta, tipografia, tema
    ├── components/  # Card, botões, stepper numérico, gráfico de linha
    ├── screens/     # Home, Fichas, Editor, Execução, Resumo, Histórico, Progresso, Metas
    ├── TrackApp.kt      # navegação + bottom bar
    └── TrackViewModel.kt # estado e regras de negócio
```
