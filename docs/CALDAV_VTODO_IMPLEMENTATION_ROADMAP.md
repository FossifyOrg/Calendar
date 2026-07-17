# Fossify Calendar: roadmap прямой синхронизации CalDAV VTODO с Baïkal

## 1. Назначение документа

Этот документ — исполнимый план разработки личного форка `MakarovSoftware/Calendar`, добавляющего прямую двустороннюю синхронизацию задач `VTODO` с CalDAV-сервером Baïkal.

План намеренно разбит на независимые JIRA-задачи по правилу:

> Одна JIRA-задача = одна feature-ветка = один проверяемый PR в `main`.

Каждая задача должна быть достаточно локальной, чтобы её можно было передать новому чату без истории предыдущих обсуждений. Новый чат должен получить:

1. ссылку или текст конкретной JIRA-задачи из этого файла;
2. путь к репозиторию `D:\Artem\Git\Calendar`;
3. указание начать с актуального `main`;
4. запрет выполнять следующие задачи roadmap в том же PR;
5. требование запустить перечисленные в задаче проверки.

Документ описывает целевую архитектуру, порядок PR, контракты между слоями, критерии готовности и тестовую стратегию. Если реализация конкретной задачи обнаруживает необходимость изменить утверждённый контракт, исполнитель не должен молча расширять PR: нужно остановиться, описать расхождение и предложить обновление roadmap.

---

## 2. Исходное состояние

Репозиторий:

- GitHub fork: `https://github.com/MakarovSoftware/Calendar`
- Локальная копия: `D:\Artem\Git\Calendar`
- `origin`: `MakarovSoftware/Calendar`
- `upstream`: `FossifyOrg/Calendar`
- основная ветка: `main`
- базовый коммит на момент составления плана: `9bfd12bc225152802ad51107f8b238401fc5cb00`

Текущая архитектура Fossify Calendar:

- события синхронизируются через Android `CalendarContract` и внешний sync adapter, обычно DAVx⁵;
- приложение не подключается к CalDAV-серверу напрямую;
- локальные задачи представлены объектом `Event` с `type = TYPE_TASK`;
- задачи сохраняются только в Room;
- выбор CalDAV-календаря для задачи запрещён;
- `insertTask()` не вызывает CalDAV-код;
- обновление задачи всегда выполняется с `updateAtCalDAV = false`;
- импорт/экспорт файлов уже частично понимает `VTODO`, но не реализует сетевую синхронизацию.

Текущий технический стек:

- Kotlin 2.3.10;
- Android Gradle Plugin 9.2.1;
- minSdk 26, target/compile SDK 36;
- Java/JVM 17;
- Room 2.8.4;
- WorkManager 2.11.2;
- JUnit 4.13.2;
- instrumentation tests AndroidX;
- три product flavor: `core`, `foss`, `gplay`.

---

## 3. Зафиксированная целевая архитектура

### 3.1. Разделение ответственности

```text
DAVx⁵
  ├── VEVENT  ⇄ Baïkal
  └── CardDAV ⇄ Baïkal

Fossify Calendar fork
  ├── события ← Android Calendar Provider ← DAVx⁵
  └── задачи  ⇄ собственный CalDAV VTODO client ⇄ Baïkal
```

Правила:

1. Новый код синхронизирует только `VTODO`.
2. `VEVENT` остаётся в существующем пути Android Calendar Provider/DAVx⁵.
3. Нельзя добавлять второй прямой синхронизатор событий.
4. Настройки прямого CalDAV-аккаунта относятся только к задачам.
5. DAVx⁵ можно продолжать обновлять из официального канала.
6. Tasks.org, jtx Board и OpenTasks не требуются.
7. Серверная реализация не должна быть жёстко привязана к URL Baïkal: используется стандарт CalDAV, но обязательная тестовая среда первого релиза — Baïkal.

### 3.2. Слои нового кода

Предлагаемая структура пакетов:

```text
org.fossify.calendar.caldav
├── account
│   ├── CalDavAccount.kt
│   ├── CalDavAccountStore.kt
│   └── CredentialStore.kt
├── discovery
│   ├── CalDavDiscoveryService.kt
│   └── DiscoveredCollection.kt
├── http
│   ├── CalDavHttpClient.kt
│   ├── CalDavRequest.kt
│   └── CalDavHttpException.kt
├── ical
│   ├── VTodoCodec.kt
│   ├── VTodoDocument.kt
│   └── PreservedProperties.kt
├── sync
│   ├── VTodoSyncEngine.kt
│   ├── SyncPlanner.kt
│   ├── SyncResult.kt
│   └── VTodoSyncWorker.kt
└── repository
    ├── SyncedTaskRepository.kt
    └── TaskSyncMetadataRepository.kt
```

Названия можно уточнить в PR, но границы ответственности менять без явного решения нельзя.

### 3.3. Локальная модель

Существующая таблица `events` остаётся пользовательской моделью задачи. Серверные поля не следует бесконтрольно добавлять в `Event`: для синхронизации вводится отдельная Room-таблица metadata с отношением 0..1 к локальной задаче.

Предлагаемый минимальный контракт:

```text
TaskSyncMetadata
├── taskId: Long (FK → events.id, UNIQUE)
├── accountId: Long
├── collectionId: Long
├── remoteHref: String?
├── remoteEtag: String?
├── remoteUid: String
├── remoteSequence: Int?
├── remotePayload: String?
├── localFingerprint: String?
├── syncState: CLEAN | DIRTY_CREATE | DIRTY_UPDATE | DIRTY_DELETE | CONFLICT
├── lastSyncedAt: Long?
└── lastError: String?
```

`remotePayload` нужен для сохранения неизвестных и пока не поддерживаемых свойств VTODO при round-trip. Пароль, токен и другие секреты в Room не хранятся.

### 3.4. Семантика даты задачи

Для MVP:

- выбранные пользователем дата и время задачи сериализуются как `DUE`;
- all-day задача сериализуется как `DUE;VALUE=DATE`;
- импортированный `DTSTART` сохраняется в raw payload;
- если VTODO имеет только `DTSTART`, оно используется как отображаемая дата;
- если имеются и `DTSTART`, и `DUE`, в UI используется `DUE`;
- `DTSTART` нельзя уничтожать при последующем сохранении;
- отдельный UI для start/due не входит в MVP.

### 3.5. Семантика completion

- незавершённая задача: `STATUS:NEEDS-ACTION`;
- завершённая задача: `STATUS:COMPLETED` и `COMPLETED:<UTC timestamp>`;
- локальный `FLAG_TASK_COMPLETED` остаётся источником отображения;
- timestamp выполнения хранится отдельно, поскольку текущий флаг его не содержит;
- снятие completion удаляет `COMPLETED` и возвращает `STATUS:NEEDS-ACTION`;
- `IN-PROCESS`, `CANCELLED` и `PERCENT-COMPLETE` сохраняются при round-trip, но отдельный UI для них не входит в MVP.

### 3.6. Безопасность

- только HTTPS в production-настройке;
- запрещено глобально отключать TLS verification;
- self-signed сертификат должен поддерживаться через явное доверие пользовательскому CA/сертификату, а не через permissive TrustManager;
- пароль хранится через Android Keystore-backed механизм;
- секреты запрещено писать в Logcat, crash messages, Room, backup и тестовые snapshots;
- Basic Auth допускается только поверх проверенного HTTPS;
- сетевые логи должны редактировать `Authorization`, cookies и содержимое секретных полей.

---

## 4. Definition of Done для каждого PR

Каждый PR считается готовым только при выполнении всех применимых пунктов:

- создан от свежего `main`;
- не включает изменения из следующих roadmap-задач;
- не содержит случайных форматирований и переводов вне scope;
- добавлены unit/instrumentation tests для нового поведения;
- Room migration имеет migration test;
- сетевой код тестируется на mock server/fixture, а не только вручную;
- выполнена сборка как минимум `assembleFossDebug`;
- выполнены релевантные unit tests;
- выполнен Detekt для затронутого модуля;
- instrumentation tests выполнены, если PR меняет Room, Android Keystore, Worker или Activity;
- в описании PR перечислены команды проверки и их результат;
- нет паролей, URL частного сервера и персональных данных в коммитах;
- изменённые UI-строки добавлены как минимум в базовый `values/strings.xml`; массовые переводы не входят в функциональный PR;
- поведение без настроенного CalDAV-аккаунта осталось прежним;
- существующая синхронизация VEVENT через DAVx⁵ не затронута.

Рекомендуемые команды, уточняемые по доступным Gradle tasks:

```powershell
.\gradlew.bat :app:testFossDebugUnitTest
.\gradlew.bat :app:assembleFossDebug
.\gradlew.bat :app:detekt
.\gradlew.bat :app:connectedFossDebugAndroidTest
```

---

## 5. Карта зависимостей JIRA-задач

```text
CALDAV-001 Architecture baseline
  ├── CALDAV-002 HTTP/iCalendar dependency spike
  │     ├── CALDAV-004 HTTP client
  │     └── CALDAV-006 VTODO codec
  ├── CALDAV-003 Account and credential storage
  │     └── CALDAV-005 Discovery and collections
  └── CALDAV-007 Room sync metadata
        ├── CALDAV-008 Read-only pull sync
        │     ├── CALDAV-009 Task-list UI integration
        │     └── CALDAV-010 Local change tracking
        │            ├── CALDAV-011 Create/update push
        │            ├── CALDAV-012 Completion and delete push
        │            └── CALDAV-013 Conflict handling
        └──────────── CALDAV-014 Manual sync UX
                       └── CALDAV-015 Background sync
                              └── CALDAV-016 Hardening and Baïkal E2E

Post-MVP:
CALDAV-017 Recurrence
CALDAV-018 VALARM
CALDAV-019 Multiple accounts/collections UX
CALDAV-020 Priority and percent complete
  └── CALDAV-021 Extended task statuses
        └── CALDAV-022 Subtasks and RELATED-TO
              └── CALDAV-023 Undated tasks and separate DTSTART/DUE
                    └── CALDAV-024 Categories and extended metadata
                          └── CALDAV-025 Full interoperability release gate
```

Порядок допускает параллельную работу только там, где PR не зависит от ещё не слитого контракта. Для последовательных чатов безопаснее выполнять задачи по номеру.

---

# 6. JIRA backlog: MVP

## CALDAV-001 — Зафиксировать архитектурный baseline и тестовые seams

### Цель

Подготовить кодовую основу без пользовательской функциональности: документировать границы VTODO sync, создать пустые интерфейсы портов и обеспечить возможность тестировать будущий код без Android/сети.

### Scope PR

- добавить пакет `org.fossify.calendar.caldav`;
- определить интерфейсы без реализации:
  - `CalDavAccountRepository`;
  - `CalDavClient`;
  - `VTodoCodec`;
  - `VTodoSyncRepository`;
- определить базовые value objects и sealed result/error types;
- добавить package-level архитектурную документацию;
- добавить unit tests для equality/validation value objects;
- зафиксировать, что API работает только с VTODO.

### Не входит

- зависимости HTTP/iCalendar;
- Room migration;
- экран настроек;
- реальные запросы;
- WorkManager.

### Критерии приёмки

- проект собирается без изменения поведения;
- интерфейсы не зависят от Activity/Context;
- ошибки разделены хотя бы на auth, TLS, protocol, network, parse и conflict;
- account/collection/task identifiers не представлены неструктурированными nullable String повсюду;
- unit tests проходят на JVM.

### Тестирование

- unit tests value objects;
- `assembleFossDebug`;
- Detekt.

### Ветка/PR

- ветка: `feature/CALDAV-001-architecture-baseline`
- PR: `CALDAV-001: add VTODO sync architecture baseline`

---

## CALDAV-002 — Выбрать и подключить HTTP/iCalendar библиотеки

### Цель

Сделать проверяемый dependency spike и принять окончательное решение: использовать зрелые CalDAV/iCalendar библиотеки либо ограниченный собственный слой поверх HTTP-клиента.

### Обязательное исследование

- проверить актуальные версии, minSdk, Kotlin/Java compatibility;
- проверить лицензии и транзитивные зависимости;
- оценить `dav4jvm` и `ical4android`;
- оценить OkHttp/MockWebServer, если они не приходят транзитивно;
- проверить поддержку:
  - PROPFIND/REPORT;
  - ETag;
  - Basic/Digest auth;
  - redirects;
  - VTODO parsing/serialization;
  - сохранения неизвестных X-properties;
  - timezone definitions;
  - Android API 26.

### Scope PR

- добавить выбранные зависимости в version catalog и Gradle;
- добавить короткий ADR `docs/adr/0001-caldav-stack.md`;
- создать минимальный compile-only adapter/probe;
- добавить fixture VTODO и JVM test parse→serialize→parse;
- документировать лицензионное решение.

### Не входит

- production HTTP client;
- account UI;
- серверная синхронизация.

### Критерии приёмки

- dependency resolution стабилен для всех flavor;
- APK собирается на minSdk 26;
- VTODO fixture с неизвестным `X-*` свойством переживает round-trip либо ADR явно описывает стратегию его сохранения;
- отсутствует дублирование несовместимых XML/HTTP библиотек;
- выбранный стек не требует форка DAVx⁵.

### Тестирование

- Gradle dependency insight;
- JVM codec probe;
- сборка `core`, `foss`, `gplay` debug;
- license review вручную.

### Ветка/PR

- ветка: `feature/CALDAV-002-library-selection`
- PR: `CALDAV-002: select CalDAV and iCalendar stack`

---

## CALDAV-003 — Безопасное хранение аккаунта и credentials

### Цель

Добавить локальную модель одного task-only CalDAV-аккаунта и безопасное хранение секрета.

### Scope PR

- модель аккаунта: id, base URL, username, display name, enabled;
- нормализация URL;
- Keystore-backed credential storage;
- non-secret account settings storage;
- API save/load/delete;
- исключение секрета из backup;
- redaction для `toString()` и ошибок;
- instrumentation tests хранения и удаления.

### Решения

- MVP поддерживает один аккаунт;
- пароль не входит в data class, который может случайно логироваться;
- credential lookup возвращает opaque secret wrapper;
- удаление аккаунта удаляет credential material.

### Не входит

- login screen;
- сетевой тест соединения;
- self-signed certificate UX;
- несколько аккаунтов.

### Критерии приёмки

- пароль не обнаруживается поиском в XML preferences/Room после сохранения;
- пароль не попадает в `toString()`;
- удаление аккаунта делает credential недоступным;
- некорректные URL отклоняются до сохранения;
- HTTP URL запрещён для production-конфигурации.

### Тестирование

- instrumentation test save/load/delete;
- backup rules inspection;
- test URL validation;
- ручная проверка Logcat.

### Ветка/PR

- ветка: `feature/CALDAV-003-secure-account-storage`
- PR: `CALDAV-003: add secure CalDAV account storage`

---

## CALDAV-004 — Реализовать базовый CalDAV HTTP client

### Цель

Реализовать транспортный слой без бизнес-логики синхронизации.

### Scope PR

- HTTPS client factory;
- Basic auth challenge/preemptive policy согласно выбранной библиотеке;
- методы OPTIONS, PROPFIND, REPORT, GET, PUT, DELETE;
- Depth header;
- If-Match/If-None-Match;
- получение status, headers, ETag и body;
- redirects с запретом утечки Authorization на другой host;
- таймауты;
- typed exceptions;
- redacted logging;
- mock-server tests.

### Не входит

- discovery workflow;
- VTODO parsing;
- retry policy WorkManager;
- permissive TLS.

### Критерии приёмки

- Authorization отсутствует в логах;
- redirect на другой origin не получает credentials;
- 401, 403, 404, 409, 412, 429 и 5xx корректно типизированы;
- ETag сохраняется без повреждения weak/quoted формы;
- XML content types с charset принимаются;
- cancellation корутины отменяет HTTP call.

### Тестирование

- MockWebServer happy/error cases;
- redirect credential-leak test;
- timeout/cancellation test;
- TLS failure test.

### Ветка/PR

- ветка: `feature/CALDAV-004-http-client`
- PR: `CALDAV-004: implement secure CalDAV HTTP transport`

---

## CALDAV-005 — Discovery principal, home-set и VTODO collections

### Цель

По URL аккаунта обнаруживать доступные task collections Baïkal.

### Scope PR

- обработать `/.well-known/caldav` и redirect;
- получить `current-user-principal`;
- получить `calendar-home-set`;
- выполнить Depth=1 PROPFIND home-set;
- распарсить:
  - href;
  - displayname;
  - resourcetype/calendar;
  - supported-calendar-component-set;
  - current-user-privilege-set;
  - sync-token/ctag, если есть;
  - calendar color, если есть;
- классифицировать collections: VTODO writable/read-only/not-supported;
- нормализовать relative href;
- fixture tests на типичные Baïkal XML responses.

### Правила

- если `supported-calendar-component-set` отсутствует, коллекция считается потенциально поддерживающей VTODO согласно RFC, но маркируется как capability unknown;
- коллекция только VEVENT не показывается как task target;
- mixed VEVENT+VTODO допускается;
- malformed отдельная response не должна уничтожать валидные соседние responses.

### Критерии приёмки

- discovery работает от base URL и principal URL;
- relative/absolute href дают одинаковый canonical URL;
- read-only collection распознаётся;
- auth/TLS/protocol errors различаются;
- unit tests не требуют реального сервера.

### Ручная проверка

- выполнить discovery на тестовом Baïkal;
- сохранить redacted response fixture без host/username;
- подтвердить наличие task collection и writable privilege.

### Ветка/PR

- ветка: `feature/CALDAV-005-discovery`
- PR: `CALDAV-005: discover VTODO CalDAV collections`

---

## CALDAV-006 — Реализовать VTODO codec и mapping MVP

### Цель

Надёжно преобразовывать iCalendar VTODO ↔ внутренняя transport model без сети и Room.

### Поддерживаемые поля MVP

- UID;
- DTSTAMP;
- SUMMARY;
- DESCRIPTION;
- DUE DATE/DATE-TIME/TZID/UTC;
- DTSTART как fallback/preserved property;
- STATUS;
- COMPLETED;
- LAST-MODIFIED;
- SEQUENCE;
- CATEGORIES;
- RRULE/EXDATE только preserve на данном этапе;
- VALARM только preserve;
- неизвестные IANA/X-properties preserve.

### Scope PR

- immutable `VTodoDocument`;
- parser and serializer adapter;
- mapping transport model ↔ минимальная task domain model;
- CRLF и line folding;
- escaping text;
- timezone/date-only tests;
- round-trip fixture suite из разных клиентов.

### Fixtures

Минимум:

- Baïkal/Thunderbird basic task;
- Tasks.org-style task;
- all-day due;
- UTC timed due;
- TZID timed due;
- completed task;
- unknown X-property;
- task с DTSTART+DUE;
- task без даты;
- malformed task.

### Критерии приёмки

- обязательные UID/DTSTAMP валидируются;
- serializer генерирует новый UID только для новой локальной задачи;
- неизвестные свойства не пропадают при round-trip;
- completion получает UTC timestamp;
- all-day дата не сдвигается из-за timezone;
- parser не падает на одной плохой записи при пакетной обработке.

### Ветка/PR

- ветка: `feature/CALDAV-006-vtodo-codec`
- PR: `CALDAV-006: add loss-aware VTODO codec`

---

## CALDAV-007 — Добавить Room metadata и migrations

### Цель

Создать устойчивую локальную модель аккаунтов, collections и sync metadata, не меняя пока поведение UI.

### Таблицы

- `caldav_accounts` — только non-secret данные;
- `caldav_task_collections`;
- `task_sync_metadata`;
- при необходимости `caldav_sync_runs` только если обосновано ADR.

### Scope PR

- entities, DAO, indices, foreign keys;
- database version increment;
- migration с текущей production schema;
- cascade/restrict semantics;
- enum converters;
- DAO tests;
- migration instrumentation test;
- запрет удаления локальной задачи при временном исчезновении metadata.

### Ключевые ограничения

- `taskId` unique;
- `(accountId, collectionHref, remoteUid)` либо эквивалентная уникальность;
- `remoteHref` уникален внутри collection, когда не null;
- удаление task удаляет metadata;
- удаление account не должно молча удалить пользовательские локальные задачи: они отвязываются или операция требует явной миграционной политики.

### Критерии приёмки

- migration сохраняет существующие events/tasks/calendars;
- downgrade не заявляется как поддерживаемый;
- индексы покрывают lookup по taskId, href и UID;
- secrets отсутствуют в schema;
- schema JSON обновлена.

### Ветка/PR

- ветка: `feature/CALDAV-007-room-sync-metadata`
- PR: `CALDAV-007: add VTODO sync metadata schema`

---

## CALDAV-008 — Реализовать read-only pull sync

### Цель

Загружать VTODO из одной выбранной collection и создавать/обновлять локальные задачи без отправки изменений на сервер.

### Алгоритм MVP

1. Получить список href+ETag через REPORT/PROPFIND.
2. Сравнить с локальной metadata.
3. Получить новые/изменённые resources через calendar-multiget или GET.
4. Распарсить VTODO.
5. В транзакции upsert локальную задачу и metadata.
6. Пометить отсутствующие remote resources как кандидаты на удаление только после полного успешного listing.
7. Для MVP remote delete удаляет/архивирует локальную синхронизированную задачу по зафиксированной политике; локальные несинхронизированные задачи не затрагиваются.

### Scope PR

- `VTodoSyncEngine.pull()`;
- sync planner;
- transactional repository;
- batch/multiget;
- pagination/batch limit;
- per-item parse errors;
- deterministic sync result metrics;
- tests с in-memory database и mock server.

### Не входит

- push;
- UI settings;
- background worker;
- recurrence expansion.

### Критерии приёмки

- повторный pull без server changes идемпотентен;
- changed ETag обновляет существующую задачу, а не создаёт duplicate;
- UID не используется как единственный идентификатор resource;
- partial network failure не приводит к массовому удалению;
- malformed один VTODO отражается в result, остальные импортируются;
- неизвестные свойства сохраняются в metadata;
- task collection не появляется как Android event calendar.

### Тесты

- empty collection;
- initial import;
- second no-op sync;
- remote update;
- remote delete;
- duplicate UID protocol anomaly;
- multiget partial 404;
- malformed resource;
- transaction rollback.

### Ветка/PR

- ветка: `feature/CALDAV-008-pull-sync`
- PR: `CALDAV-008: implement read-only VTODO pull sync`

---

## CALDAV-009 — Подключить remote task collections к Task UI

### Цель

Отобразить синхронизированные списки задач в существующем UI и позволить выбирать collection для новых задач, пока без push.

### Scope PR

- представить task collection как доступный календарь/список в Task UI без регистрации в Android Calendar Provider;
- обновить выбор календаря в `TaskActivity`;
- убрать hardcoded `showCalDAVCalendars = false` только для новых direct-VTODO collections, не для VEVENT CalendarContract calendars;
- показывать название аккаунта/collection;
- показывать read-only состояние;
- показывать локальный/offline источник корректно;
- запретить сохранение новой задачи в remote collection до CALDAV-011 либо маркировать её local pending согласно согласованному feature flag;
- сохранить поведение обычных локальных задач.

### Важное различие

Существующие `CalendarEntity.caldavCalendarId` относятся к Android Calendar Provider/DAVx⁵ и не должны переиспользоваться как direct collection ID. Нужен явный source type либо отдельная abstraction списка задач.

### Критерии приёмки

- imported remote tasks открываются в TaskActivity;
- remote collection видна отдельно от одноимённого VEVENT calendar;
- read-only task нельзя изменить/удалить;
- local task flow не изменён;
- EventActivity не предлагает VTODO collections для VEVENT.

### Тестирование

- UI/instrumentation tests list selection;
- same-name event/task collections;
- read-only state;
- no-account regression;
- rotation/process recreation state.

### Ветка/PR

- ветка: `feature/CALDAV-009-task-list-ui`
- PR: `CALDAV-009: expose CalDAV task collections in task UI`

---

## CALDAV-010 — Отслеживать локальные изменения как sync intents

### Цель

Сделать local-first change tracking без отправки в сеть.

### Scope PR

- после создания remote-bound task ставить `DIRTY_CREATE`;
- после редактирования clean task ставить `DIRTY_UPDATE`;
- удаление remote-bound task превращать в tombstone `DIRTY_DELETE`, не теряя href/ETag;
- несколько локальных edits схлопывать;
- create→delete до первого sync удалять локально без remote operation;
- completion/uncompletion считать update;
- fingerprint/hash только стабильных синхронизируемых полей;
- транзакционно менять task+metadata;
- tests state machine.

### Критерии приёмки

- изменение чистой задачи становится DIRTY_UPDATE;
- повторное сохранение без изменений не создаёт dirty state;
- DIRTY_CREATE остаётся DIRTY_CREATE после редактирования;
- DIRTY_CREATE→delete не вызывает будущий DELETE;
- DIRTY_UPDATE→delete сохраняет remote href/etag;
- crash между task update и metadata update не создаёт рассогласования благодаря транзакции.

### Ветка/PR

- ветка: `feature/CALDAV-010-local-change-tracking`
- PR: `CALDAV-010: track local VTODO sync intents`

---

## CALDAV-011 — Push создания и обновления VTODO

### Цель

Отправлять `DIRTY_CREATE` и `DIRTY_UPDATE` на сервер безопасно и идемпотентно.

### Create

- заранее сгенерированный стабильный UID;
- deterministic безопасное имя resource, например UUID `.ics`;
- `PUT` с `If-None-Match: *`;
- получение ETag из response либо последующий HEAD/PROPFIND;
- повтор после неизвестного результата проверяет наличие href, не создаёт duplicate.

### Update

- merge с preserved remote payload;
- `PUT` с точным `If-Match: <etag>`;
- 412 переводит объект в conflict, не перезаписывает сервер;
- успешный ответ обновляет ETag и CLEAN state.

### Scope PR

- push planner;
- serializer integration;
- per-item result;
- retry-safe operations;
- mock server + Room integration tests.

### Критерии приёмки

- create появляется в Baïkal и читается Thunderbird/другим VTODO-клиентом;
- update не уничтожает неизвестные свойства;
- retry create не создаёт duplicate;
- wrong ETag никогда не приводит к unconditional overwrite;
- read-only collection не получает PUT;
- сетевой failure оставляет dirty state.

### Ветка/PR

- ветка: `feature/CALDAV-011-push-create-update`
- PR: `CALDAV-011: push VTODO creates and updates`

---

## CALDAV-012 — Синхронизировать completion и удаления

### Цель

Закрыть полный CRUD жизненного цикла задачи.

### Scope PR

- completion → STATUS:COMPLETED + COMPLETED UTC;
- uncomplete → STATUS:NEEDS-ACTION без COMPLETED;
- DELETE с `If-Match`;
- обработка 404 как already deleted success;
- 412 как conflict;
- tombstone cleanup после успеха;
- сохранить notification/local completed instances semantics насколько возможно для non-recurring MVP;
- tests.

### Не входит

- completion отдельных occurrence recurring VTODO;
- soft-delete UI history;
- restore remote task.

### Критерии приёмки

- completion виден в Thunderbird/Tasks.org;
- remote completion загружается обратно;
- uncomplete корректно убирает timestamp;
- удаление не выполняется без известного href;
- локальная tombstone переживает перезапуск до успешного sync;
- 404 cleanup идемпотентен.

### Ветка/PR

- ветка: `feature/CALDAV-012-complete-delete`
- PR: `CALDAV-012: sync VTODO completion and deletion`

---

## CALDAV-013 — Конфликты и безопасный merge

### Цель

Не терять данные при одновременном изменении задачи на телефоне и другом клиенте.

### Политика MVP

- remote changed + local clean → принять remote;
- remote unchanged + local dirty → push local;
- remote changed + local dirty → CONFLICT;
- remote deleted + local dirty → CONFLICT;
- remote deleted + local clean → удалить/архивировать local;
- конфликт не разрешается автоматически через last-write-wins.

### Scope PR

- three-way comparison: last remote payload, current remote, current local;
- conflict entity/state;
- сохранить обе версии;
- UI-safe API получения conflict summary;
- операции resolve keep-local / keep-server;
- keep-local использует актуальный ETag после явного решения;
- tests conflict matrix.

### Критерии приёмки

- ни один conflict path не выполняет unconditional PUT;
- обе версии доступны до решения;
- повторный sync не стирает conflict;
- keep-server очищает dirty state и применяет remote;
- keep-local отправляет local поверх явно подтверждённого актуального remote ETag;
- resolution идемпотентен.

### Ветка/PR

- ветка: `feature/CALDAV-013-conflict-resolution`
- PR: `CALDAV-013: add safe VTODO conflict handling`

---

## CALDAV-014 — Экран аккаунта, collections и ручная синхронизация

### Цель

Дать пользователю законченный минимальный workflow настройки и ручного sync.

### Scope PR

- settings entry `CalDAV tasks`;
- add/edit/delete account;
- test connection;
- discovery progress;
- выбор одной task collection;
- отображение writable/read-only;
- manual Sync now;
- last success, last error, counts;
- понятные auth/TLS/protocol/network messages;
- подтверждение при отвязке аккаунта;
- conflict entry point.

### UX требования

- явно написать, что настройка относится только к Tasks;
- не обещать синхронизацию событий/контактов;
- не показывать пароль после сохранения;
- ошибки не содержат credentials;
- disable account не удаляет задачи;
- delete account предлагает оставить задачи локально либо отменить действие; destructive remote delete не выполняется.

### Критерии приёмки

- новый пользователь может настроить Baïkal без adb;
- invalid credentials дают отличимую ошибку;
- VTODO collection можно выбрать;
- manual sync показывает итог;
- приложение без аккаунта ведёт себя как upstream;
- rotation и process death не отправляют повторно destructive action.

### Тестирование

- instrumentation UI happy path с fake service;
- auth failure;
- no VTODO collections;
- read-only collection;
- account removal;
- manual Baïkal smoke test.

### Ветка/PR

- ветка: `feature/CALDAV-014-settings-manual-sync`
- PR: `CALDAV-014: add CalDAV task account and manual sync UI`

---

## CALDAV-015 — Фоновая синхронизация WorkManager

### Цель

Добавить надёжный периодический и event-triggered sync без foreground service.

### Scope PR

- unique periodic work per account;
- network constraint;
- exponential backoff;
- manual one-time work reuse того же engine;
- enqueue при локальном dirty change с debounce;
- mutex/unique work против параллельных sync;
- boot/app update re-scheduling при необходимости;
- уведомление только для длительных/требующих внимания случаев согласно Android policy;
- battery-aware defaults;
- worker tests.

### Политика ошибок

- auth/TLS permanent until settings change — failure/attention, не tight retry;
- timeout/5xx/429 — retry с backoff и Retry-After;
- parse одной записи — partial success;
- conflict — successful sync with attention, не бесконечный retry;
- disabled account — no-op success.

### Критерии приёмки

- одновременно работает максимум один sync одного аккаунта;
- dirty task вызывает отложенный push;
- airplane mode не теряет dirty state;
- 429 соблюдает backoff;
- WorkManager не синхронизирует VEVENT;
- период можно менять/отключать без orphan workers.

### Ветка/PR

- ветка: `feature/CALDAV-015-background-sync`
- PR: `CALDAV-015: schedule resilient VTODO background sync`

---

## CALDAV-016 — Hardening, миграция локальных задач и Baïkal E2E release gate

### Цель

Подготовить первую реально используемую личную сборку и закрыть критические сценарии потери данных.

### Scope PR

- явная операция перемещения/копирования существующей локальной задачи в remote collection;
- batch migration с preview и confirmation;
- export backup перед массовой миграцией;
- end-to-end test checklist;
- telemetry отсутствует; локальный diagnostic export redacted;
- обработка clock skew;
- large collection batching;
- сетевые cancellation/resume;
- release notes и известные ограничения;
- version bump согласно политике fork.

### Обязательная E2E матрица

1. Создать в Fossify → увидеть в Baïkal/Thunderbird.
2. Изменить в Thunderbird → получить в Fossify.
3. Завершить на телефоне → увидеть completed remote.
4. Снять completion remote → получить локально.
5. Удалить с обеих сторон.
6. Одновременное изменение → conflict без потери.
7. Offline create/update/delete → успешный sync после сети.
8. Неверный пароль → данные остаются.
9. Смена сертификата → безопасная ошибка, без bypass.
10. 1000+ задач → ограниченное потребление памяти.
11. Unicode, emoji, multiline, escaped comma/semicolon/backslash.
12. All-day и timed task в Asia/Yerevan, UTC и DST timezone.
13. App upgrade с чистого upstream schema.
14. Process kill во время pull и push.
15. DAVx⁵ продолжает синхронизировать события без duplicate.

### Критерии релиза MVP

- нет известных путей silent data loss;
- все destructive requests conditional;
- backup/restore локальных задач проверен;
- базовые сценарии Baïkal проходят на двух клиентах;
- debug logs redacted;
- ограничения recurrence/alarms/advanced fields явно указаны пользователю.

### Ветка/PR

- ветка: `feature/CALDAV-016-mvp-hardening`
- PR: `CALDAV-016: harden Baikal VTODO sync for MVP`

---

# 7. JIRA backlog: после MVP

## CALDAV-017 — Повторяющиеся VTODO

### Цель

Поддержать RRULE/RDATE/EXDATE и completion отдельных экземпляров без потери совместимости.

### Предварительное условие

До кода нужен отдельный ADR по модели recurring VTODO и проверка поведения Baïkal, Thunderbird и Tasks.org.

### Scope

- master recurrence;
- exceptions через RECURRENCE-ID;
- completed occurrences;
- edit this/future/all mapping;
- timezone tests;
- сохранение server representation.

### Критерии

- ни одна операция не разворачивает бесконечную recurrence в серверные копии;
- исключения не превращаются в независимые задачи;
- действия Fossify `this/future/all` имеют документированное CalDAV-представление.

---

## CALDAV-018 — VALARM и server alarm round-trip

### Цель

Синхронизировать task reminders как VALARM и согласовать их с локальными уведомлениями.

### Scope

- DISPLAY alarm;
- trigger relative to DUE/START;
- несколько alarms;
- preserve unsupported EMAIL/AUDIO;
- предотвращение duplicate local notifications;
- timezone/all-day behavior.

---

## CALDAV-019 — Несколько аккаунтов и collections

### Цель

Расширить MVP одного аккаунта до нескольких аккаунтов и task lists.

### Scope

- account list;
- collection enable/disable;
- per-collection sync schedule;
- перемещение задачи между collections через safe create-then-delete;
- одинаковые UID в разных collections;
- per-account worker isolation.

---

## CALDAV-020 — Приоритет и процент выполнения

### Цель

Добавить двустороннюю поддержку стандартных свойств `PRIORITY` и `PERCENT-COMPLETE`, включая UI, локальное хранение и синхронизацию без потери данных других клиентов.

### Scope PR

- расширить локальную модель задачи полями priority и percent complete либо добавить нормализованное расширение модели согласно принятой архитектуре;
- добавить Room migration и migration test;
- парсить и сериализовать `PRIORITY` со значениями 0–9;
- парсить и сериализовать `PERCENT-COMPLETE` со значениями 0–100;
- добавить UI выбора приоритета;
- добавить UI процента выполнения;
- определить отображение полей в просмотре задачи;
- включить новые поля в fingerprint/change tracking/conflict comparison;
- сохранить неизвестные/некорректные remote values в raw payload без падения всего sync;
- добавить codec, repository, UI и E2E tests.

### Зафиксированная семантика

- `PRIORITY:0` означает undefined;
- меньшие ненулевые значения iCalendar означают более высокий приоритет;
- UI не должен ошибочно сортировать 1 как «низкий», а 9 как «высокий»;
- completion задачи устанавливает 100%, если пользователь явно не задал совместимую политику иначе;
- снятие completion не должно молча возвращать старый процент без документированного правила;
- точное правило связи completion и percent фиксируется тестами и коротким ADR в этом PR.

### Не входит

- новые task statuses;
- subtasks;
- отдельные DTSTART/DUE;
- категории;
- organizer/attendee.

### Критерии приёмки

- priority и percentage проходят Fossify → Baïkal → Thunderbird/совместимый клиент → Fossify;
- все допустимые граничные значения обрабатываются;
- некорректные значения не приводят к потере всей задачи;
- conflict UI/модель различает изменения новых полей;
- Room migration сохраняет существующие задачи.

### Ветка/PR

- ветка: `feature/CALDAV-020-priority-progress`
- PR: `CALDAV-020: sync task priority and percent complete`

---

## CALDAV-021 — Расширенные статусы VTODO

### Цель

Поддержать полный базовый набор статусов VTODO: `NEEDS-ACTION`, `IN-PROCESS`, `COMPLETED` и `CANCELLED`.

### Scope PR

- ввести явную domain-модель task status вместо вывода всего состояния только из completion flag;
- добавить Room migration и converters;
- обновить VTODO mapping;
- добавить UI смены статуса;
- согласовать статус с `COMPLETED` и `PERCENT-COMPLETE`;
- сохранить обратную совместимость с существующим `FLAG_TASK_COMPLETED`;
- обновить уведомления, списки, dimming и mark-complete action;
- добавить conflict comparison и E2E tests.

### Инварианты

- `COMPLETED` требует completion timestamp;
- любой другой статус не должен содержать `COMPLETED` после локального сохранения;
- `NEEDS-ACTION` обычно соответствует 0%;
- `IN-PROCESS` допускает 0–99%;
- `COMPLETED` соответствует 100%;
- `CANCELLED` не считается выполненной задачей, если это отдельно не задано отображением;
- remote payload с нестандартным сочетанием сохраняется до явного редактирования пользователем.

### Не входит

- subtasks;
- assignment workflow;
- CalDAV Scheduling.

### Критерии приёмки

- каждый стандартный статус корректно проходит round-trip;
- существующая кнопка mark complete остаётся рабочей;
- фильтрация completed/cancelled определена и покрыта тестами;
- другие клиенты видят согласованные STATUS/COMPLETED/PERCENT-COMPLETE.

### Ветка/PR

- ветка: `feature/CALDAV-021-task-statuses`
- PR: `CALDAV-021: support standard VTODO statuses`

---

## CALDAV-022 — Подзадачи и RELATED-TO

### Цель

Добавить иерархию задач через стандартное свойство `RELATED-TO;RELTYPE=PARENT`.

### Scope PR

- добавить связь parent/child в локальную модель;
- Room migration, foreign-key/index strategy и cycle protection;
- импортировать parent UID независимо от порядка получения ресурсов;
- разрешать deferred linking, когда parent ещё не загружен;
- сериализовать `RELATED-TO;RELTYPE=PARENT:<parent UID>`;
- добавить UI создания подзадачи и выбора/удаления родителя;
- отображать подзадачи в Task UI;
- определить поведение при удалении/перемещении parent;
- включить отношения в conflict comparison;
- tests для orphan, cycle, remote deletion и cross-collection references.

### Правила

- связь строится по UID, а не по remote href;
- циклы запрещены локально;
- отсутствующий parent не приводит к удалению child;
- удаление parent не удаляет remote children автоматически;
- cross-collection parent сохраняется, даже если UI не может полностью его отобразить;
- completion parent не должен автоматически завершать children без отдельной будущей функции.

### Не входит

- произвольные типы `RELTYPE` кроме PARENT;
- project management dependencies;
- assignment.

### Критерии приёмки

- иерархия совместима с Tasks.org/Thunderbird в пределах их поддержки;
- sync order не создаёт permanent orphan;
- cycle невозможно создать через UI/repository;
- неизвестные RELATED-TO relationships сохраняются при round-trip.

### Ветка/PR

- ветка: `feature/CALDAV-022-subtasks`
- PR: `CALDAV-022: add VTODO subtasks with RELATED-TO`

---

## CALDAV-023 — Задачи без даты и раздельные DTSTART/DUE

### Цель

Перейти от упрощённой модели «одна дата задачи = DUE» к полной практической модели сроков VTODO.

### Scope PR

- добавить nullable start и due в domain/storage model;
- Room migration из существующего `startTS` по зафиксированной безопасной политике;
- поддержать четыре режима:
  - без даты;
  - только DTSTART;
  - только DUE;
  - DTSTART + DUE;
- поддержать DATE и DATE-TIME независимо для start/due;
- добавить UI очистки даты, отдельного начала и срока;
- обновить сортировку, календарное отображение и уведомления;
- валидировать, что DUE не раньше DTSTART при совместимых типах;
- сохранить timezone и floating/date-only semantics;
- обновить codec, sync, conflicts и E2E tests.

### Миграционное правило

До реализации исполнитель обязан зафиксировать ADR: считать ли существующую Fossify task date сроком DUE. Roadmap рекомендует именно это. Миграция не должна создавать DTSTART без необходимости.

### Критерии приёмки

- задача без даты синхронизируется и не исчезает из task list;
- all-day due не сдвигается между timezone;
- DTSTART и DUE сохраняются раздельно;
- reminders имеют однозначную опорную дату;
- calendar views не создают фиктивную дату для undated task;
- все четыре режима проходят Baïkal round-trip.

### Ветка/PR

- ветка: `feature/CALDAV-023-start-due-undated`
- PR: `CALDAV-023: support undated tasks and separate start/due`

---

## CALDAV-024 — Категории и дополнительные metadata

### Цель

Добавить полноценное редактирование категорий и безопасную поддержку полезных стандартных metadata VTODO.

### Scope PR

- multiple `CATEGORIES` values с корректным escaping;
- `CREATED`;
- `LAST-MODIFIED`;
- `SEQUENCE`;
- `URL`;
- сохранение и отображение read-only metadata UID/created/modified в details/debug UI по необходимости;
- локальная модель категорий без смешивания с CalDAV collection identity;
- UI выбора нескольких категорий;
- обновление fingerprint/conflicts;
- round-trip tests с Unicode, comma, semicolon и backslash;
- E2E interoperability tests.

### Не входит

- `ORGANIZER`/`ATTENDEE` workflow;
- attachments;
- GEO;
- произвольный редактор X-properties.

### Критерии приёмки

- несколько категорий сохраняются между Fossify и другим клиентом;
- название task collection не подменяется категорией;
- CREATED не меняется при update;
- SEQUENCE обновляется по документированной политике;
- URL валидируется и открывается безопасно;
- unsupported metadata не уничтожается.

### Ветка/PR

- ветка: `feature/CALDAV-024-categories-metadata`
- PR: `CALDAV-024: sync VTODO categories and metadata`

---

## CALDAV-025 — Финальная interoperability-проверка полного task-функционала

### Цель

Свести все post-MVP возможности в стабильный персональный релиз и доказать совместимость на реальном Baïkal и независимых клиентах.

### Scope PR

- исправления, обнаруженные полной E2E-матрицей, без добавления новых крупных функций;
- обновление diagnostic export и user-facing known limitations;
- проверка migrations со всех schema versions, появившихся в CALDAV-017–024;
- performance hardening для recurrence/subtasks/multiple collections;
- финальные release notes;
- документированная backup/rollback процедура;
- версия приложения для полного персонального релиза.

### Обязательная матрица функций

- обычная, all-day, timed и undated task;
- DTSTART-only, DUE-only, DTSTART+DUE;
- completion/uncompletion с timestamp;
- NEEDS-ACTION, IN-PROCESS, COMPLETED, CANCELLED;
- priority 0–9;
- percent 0–100;
- recurring task и exception;
- completion отдельного occurrence;
- VALARM single/multiple;
- parent/subtask и orphan parent;
- несколько accounts/collections и move;
- categories и URL;
- offline create/update/delete;
- ETag conflict;
- preserved unknown X-properties;
- Unicode/timezone/DST;
- app update с MVP и предыдущей полной версий.

### Клиенты проверки

- Baïkal как сервер;
- текущий Fossify fork на двух устройствах/эмуляторах;
- Thunderbird как минимум;
- Tasks.org или jtx Board только как внешний interoperability oracle, их установка не требуется конечному пользователю;
- при наличии — другой desktop CalDAV VTODO client.

### Что означает «полный» после CALDAV-025

После этой задачи приложение считается функционально полным для персонального управления задачами: CRUD, сроки, задачи без даты, completion, статусы, процент, приоритет, повторения, исключения, напоминания, подзадачи, категории, несколько аккаунтов/списков, offline sync и конфликты.

Это не означает реализацию абсолютно всех расширений iCalendar/CalDAV. За пределами заявленного полного персонального функционала остаются, пока не появится отдельный roadmap:

- CalDAV Scheduling;
- назначение задач другим пользователям через ORGANIZER/ATTENDEE;
- inbox/outbox приглашений и replies;
- ATTACH;
- GEO;
- сложные ACL, delegation и shared collection management;
- vendor-specific UI для всех X-properties.

### Критерии приёмки

- обязательная матрица пройдена и приложена к PR;
- нет известных silent-data-loss defects;
- все conditional write/conflict гарантии сохранены;
- migrations проверены;
- известные ограничения явно документированы;
- release пригоден для ежедневного персонального использования без дополнительного task-приложения.

### Ветка/PR

- ветка: `feature/CALDAV-025-full-interoperability`
- PR: `CALDAV-025: validate full personal VTODO interoperability`

---

## 8. Стратегия тестирования

### 8.1. Пирамида тестов

#### JVM unit tests

- URL normalization;
- XML parsing;
- VTODO codec;
- sync planner/state machine;
- fingerprints;
- conflict matrix;
- redaction;
- retry classification.

#### Android instrumentation tests

- Room migrations;
- DAO transactions;
- credential storage;
- WorkManager;
- settings/task UI;
- process recreation.

#### Mock HTTP integration tests

- все CalDAV methods;
- Multi-Status XML;
- ETag/conditional requests;
- redirects;
- auth failures;
- partial multiget;
- 412/429/5xx;
- cancellation.

#### Реальный Baïkal E2E

- отдельный тестовый пользователь;
- отдельные collections events/tasks;
- disposable data;
- проверка вторым клиентом, предпочтительно Thunderbird;
- никаких production credentials в репозитории.

### 8.2. Golden fixtures

Fixtures должны быть обезличены и храниться в test resources:

- discovery responses;
- collection listing;
- multiget;
- VTODO разных клиентов;
- error multistatus;
- invalid/malformed payloads.

Golden tests не должны сравнивать нестабильный DTSTAMP/UID без injection clock/UID generator.

### 8.3. Инъекции для тестируемости

С первого PR предусмотреть:

- `Clock`;
- `UidGenerator`;
- HTTP transport interface;
- credential interface;
- transactional repositories;
- dispatcher injection;
- worker engine interface.

Нельзя получать `System.currentTimeMillis()`, случайный UUID, глобальный singleton client или реальный Context глубоко внутри sync algorithm без test seam.

---

## 9. Git и PR workflow

Перед каждой задачей:

```powershell
git switch main
git fetch origin
git pull --ff-only origin main
git status --short
git switch -c feature/CALDAV-NNN-short-name
```

Правила PR:

- base: `MakarovSoftware/Calendar:main`;
- один логический commit допустим, несколько осмысленных commits тоже допустимы;
- не делать merge upstream внутри feature PR;
- не коммитить `local.properties`, keystore, credentials, private URLs;
- не менять package/applicationId без отдельной задачи;
- PR description содержит Summary, Scope, Out of scope, Tests, Risks, Screenshots для UI;
- после merge следующий чат начинает только от обновлённого `main`.

Поскольку локальный clone изначально shallow, перед сложной исторической операцией можно отдельно выполнить:

```powershell
git fetch --unshallow origin
```

Это инфраструктурное действие, не часть feature PR.

---

## 10. Шаблон запроса для нового чата

Использовать следующий шаблон, подставив номер задачи:

```text
Работаем с репозиторием D:\Artem\Git\Calendar.

Реализуй только JIRA-задачу CALDAV-NNN из документа
CALDAV_VTODO_IMPLEMENTATION_ROADMAP.md. Одна задача должна соответствовать
одному PR в main. Начни с проверки актуального main и состояния worktree.

Не реализуй следующие задачи roadmap и не расширяй scope молча. Если контракт
задачи конфликтует с текущим кодом или уже слитыми изменениями, сначала дай
конкретный анализ и предложи минимальную корректировку.

Выполни реализацию, добавь перечисленные тесты, запусти применимые проверки,
покажи итоговый diff/status и подготовь название и описание PR. Не пушь и не
создавай PR, если я отдельно этого не попросил.
```

Рекомендуется приложить к запросу только раздел конкретной задачи и разделы:

- «Зафиксированная целевая архитектура»;
- «Definition of Done»;
- «Стратегия тестирования»;
- при необходимости непосредственно предшествующий контракт.

---

## 11. Решения, которые нельзя принимать заново в каждой задаче

Если roadmap не был явно обновлён, считаются утверждёнными следующие решения:

1. Синхронизируем напрямую только VTODO.
2. VEVENT остаётся за DAVx⁵/Android Calendar Provider.
3. В MVP один CalDAV-аккаунт и одна выбранная task collection.
4. Секреты хранятся через Android Keystore-backed механизм.
5. TLS verification не отключается.
6. Все destructive remote changes используют ETag preconditions.
7. Конфликты не разрешаются last-write-wins автоматически.
8. Неизвестные VTODO properties сохраняются при round-trip.
9. Дата текущей Fossify Task соответствует DUE.
10. Серверные metadata хранятся отдельно от основной Event model.
11. Задачи не записываются как VEVENT.
12. DAVx⁵ не форкается и не модифицируется.

---

## 12. Открытые вопросы, решаемые до соответствующего PR

Эти вопросы не блокируют CALDAV-001, но должны быть закрыты в указанных задачах:

| Вопрос | Где решить |
|---|---|
| Конкретные версии dav4jvm/ical4android/HTTP stack | CALDAV-002 |
| Механизм Keystore-backed secret storage на API 26 | CALDAV-003 |
| Как доверять частному CA Baïkal без insecure bypass | CALDAV-003/004, отдельный ADR при необходимости |
| Реальные discovery properties вашего Baïkal | CALDAV-005 |
| Точный формат task collection: VTODO-only или mixed | CALDAV-005 |
| Политика remote delete: hard delete local или локальный archive | CALDAV-008, зафиксировать до merge |
| Представление direct collections в существующем CalendarEntity UI | CALDAV-009 |
| Поведение при удалении аккаунта | CALDAV-007/014 |
| Интервал background sync | CALDAV-015 |
| Полная модель recurring VTODO | CALDAV-017 ADR |

---

## 13. MVP считается завершённым, когда

Пользователь может:

1. установить обычный DAVx⁵ и данный fork Calendar;
2. оставить синхронизацию событий и контактов в DAVx⁵;
3. отдельно добавить тот же Baïkal account в разделе `CalDAV tasks` Calendar;
4. выбрать VTODO collection;
5. увидеть существующие задачи;
6. создать новую задачу;
7. изменить, завершить, снова открыть и удалить её;
8. увидеть те же изменения в Thunderbird или другом VTODO-клиенте;
9. работать offline с последующей синхронизацией;
10. получить явный конфликт вместо молчаливой потери данных.

При этом:

- дополнительное task-приложение не требуется;
- собственный fork DAVx⁵ не требуется;
- события не дублируются;
- пароль не хранится открытым текстом;
- обновление официального DAVx⁵ не ломает task sync;
- приложение без настроенного task account работает как обычный Fossify Calendar.
