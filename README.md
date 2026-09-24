# Chronos

Plugin per Paper/Folia (Java 21, Minecraft 1.21.x): logging completo, rollback con anteprima, incidenti raggruppati, snapshot dei giocatori e rilevamento di comportamenti sospetti. Pensato come alternativa più ricca a CoreProtect.

## Caratteristiche principali

- **Lookup con GUI**: menu paginato con filtri a click (giocatore, tempo, tipo di azione), teletrasporto diretto sul blocco.
- **Rollback con anteprima**: prima di applicare, i blocchi cambiati appaiono come "fantasmi" visibili solo a te, con un riepilogo e i pulsanti CONFERMA / ANNULLA.
- **Rollback selettivo**: per area (bacchetta di selezione o raggio), giocatori inclusi o esclusi, tipo di blocco, tempo o incidente. Con `undo`/`redo` su più livelli.
- **Incidenti**: TNT a catena, fuoco, lava, acqua, cristalli dell'End e Wither vengono ricondotti a chi li ha causati e raggruppati, così un solo comando annulla l'intero evento.
- **Dati completi**: i contenitori tornano identici a prima (incantesimi, nomi, shulker box annidate), stesso discorso per i cartelli.
- **Snapshot dei giocatori**: inventario, ender chest, XP e posizione salvati a morte, logout e a intervalli, con ripristino reversibile.
- **Rilevamento sospetti**: X-ray, rottura di massa, svuotamento di contenitori, con avvisi in game, in console e su Discord.
- **Prestazioni**: scrittura asincrona a batch, SQLite / MySQL / PostgreSQL, ID numerici al posto dei nomi ripetuti, retention automatica con archiviazione in CSV compresso.
- **Multilingua**: file di lingua in `plugins/Chronos/lang/` (incluse le traduzioni in inglese e italiano), impostabile in `config.yml`.

## Requisiti

- Paper (o una fork compatibile, es. Purpur, Folia) 1.21.x
- Java 21

## Installazione

1. Scarica `Chronos-1.0.0.jar` e mettilo nella cartella `plugins`.
2. Avvia il server: crea `plugins/Chronos/config.yml` e i file di lingua.
3. Modifica `config.yml` per lingua, database, filtri e soglie (vedi sotto).
4. Riavvia il server.

## Build dal sorgente

```
gradle build
```

Il jar compilato si trova in `build/libs/Chronos-1.0.0.jar`. HikariCP e il driver PostgreSQL vengono scaricati automaticamente da Paper all'avvio. Se usi una versione diversa di Minecraft, aggiorna la dipendenza `paper-api` in `build.gradle.kts`.

## Comandi (`/chronos`, alias `/chr`)

| Comando | Descrizione |
|---|---|
| `inspect` | inspector a click: sinistro = blocco, destro = blocco adiacente / contenitore |
| `lookup <filtri>` | menu GUI con filtri a click |
| `list <filtri>` | risultati in chat, paginati |
| `rollback` / `restore <filtri>` | anteprima fantasma + `confirm` / `cancel`; `now` applica subito |
| `undo` / `redo` | pila multilivello |
| `wand` | bacchetta di selezione (poi usa `r:sel`) |
| `incidents [u:] [t:]` | incidenti raggruppati, con [vedi] e [rollback] |
| `snapshot list/take/restore <giocatore> [id\|last\|death] [inv\|ender\|xp\|all]` | snapshot dei giocatori |
| `alerts` | silenzia/riattiva per te gli avvisi sospetti |
| `status` | stato coda e database |
| `purge <tempo>` | elimina i dati più vecchi (es. `60d`) |
| `reload` | ricarica config e lingua |

### Filtri

`u:` giocatori · `xu:` escludi giocatori · `b:` blocchi · `xb:` escludi blocchi · `t:` tempo (es. `2h30m`) · `r:` raggio o `sel` (con la bacchetta) · `a:` azione (`break`/`place`/`add`/`remove`/`kill`/`block`/`container`) · `i:` incidente · `w:` mondo · `at:x,y,z` un blocco preciso · `now` salta l'anteprima

Esempio: `/chronos rollback u:Steve t:2h r:30 xb:dirt`

## Permessi

`chronos.inspect`, `chronos.lookup`, `chronos.rollback`, `chronos.teleport`, `chronos.snapshot`, `chronos.alerts`, `chronos.admin`, `chronos.bypass.detect`

## Configurazione

Tutte le impostazioni sono in `plugins/Chronos/config.yml`, ogni voce è commentata: lingua, database, cosa registrare, rollback e anteprima, retention, snapshot, soglie del rilevamento sospetti e webhook Discord.

### Cambiare lingua

1. Apri `plugins/Chronos/config.yml`.
2. Imposta `language: it` (oppure `en`).
3. Usa `/chronos reload` oppure riavvia il server.

### Aggiungere una lingua

1. Copia `plugins/Chronos/lang/en.yml` in un nuovo file, es. `fr.yml`.
2. Traduci i testi, mantenendo invariati i segnaposto tra `{ }` e i tag `<click:...>`.
3. Imposta `language: fr` in `config.yml` e ricarica.

## Supporto

Segnala problemi o richieste sulla pagina del progetto (issue tracker / Discord, se presenti).

## Licenza

Da definire dall'autore (consigliata una licenza open source, es. MIT o GPLv3, per la pubblicazione su Modrinth).
