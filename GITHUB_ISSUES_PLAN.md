# GitHub Issues Plan для Distributed Audit Ledger

## Структура Issues

Все issues будут связаны через:
- Labels: `backend`, `blockchain`, `frontend`, `infra`, `docs`
- Project Board: для отслеживания статуса (TODO, In Progress, Done)
- Каждый issue будет связан с PR через `Closes #XX` или `Relates to #XX`

---

## MVP Phase (Phase 1) — ✅ COMPLETE (Issues #1–#12)

### 1. [SETUP] Инициализация репозитория
**ID:** #1  
**Labels:** `infra`, `docs`  
**Description:**
- Создать базовую структуру каталогов согласно архитектуре
- Инициализировать основные конфигурационные файлы
- Добавить LICENSE (MIT)
- Написать базовый README.md
- Добавить .gitignore для Java, Node.js, Solidity

**Subtasks:**
- [x] #1.1 - Создать folder structure
- [x] #1.2 - Инициализировать Git репозиторий
- [x] #1.3 - Создать изначальный README и LICENSE

**Expected PR:** PR-1 (Initial repository setup)

---

### 2. [INFRA] Docker Compose для инфраструктуры
**ID:** #2  
**Labels:** `infra`  
**Depends on:** #1  
**Description:**
Поднять docker-compose с основными сервисами:
- PostgreSQL (port 5432)
- Kafka + Zookeeper
- Ganache (локальный блокчейн, port 8545)
- pgAdmin (для управления БД)

**Acceptance Criteria:**
- `docker-compose up` запускает все сервисы
- БД готова к подключению
- Kafka доступен на port 9092
- Ganache готов к деплою контрактов

**Subtasks:**
- [x] #2.1 - docker-compose.yml с всеми сервисами
- [x] #2.2 - .env файлы с конфигурацией
- [x] #2.3 - Скрипты инициализации БД (схема)
- [x] #2.4 - README для запуска инфраструктуры

**Expected PR:** PR-2 (Docker Compose setup)

---

### 3. [BLOCKCHAIN] Смарт-контракт AuditLedger
**ID:** #3  
**Labels:** `blockchain`  
**Depends on:** #2  
**Description:**
Разработать Solidity смарт-контракт для хранения хэшей событий.

**Требования:**
```solidity
contract AuditLedger {
  struct AuditRecord {
    bytes32 eventHash;
    uint256 timestamp;
    string eventType;
    address source;
  }
  
  function appendAuditRecord(bytes32 _hash, uint256 _timestamp, string memory _eventType, address _source) public
  function getRecord(uint256 _index) public view returns (AuditRecord)
  function getRecordsCount() public view returns (uint256)
  function isHashExists(bytes32 _hash) public view returns (bool)
}
```

**Subtasks:**
- [x] #3.1 - Написать AuditLedger.sol контракт
- [x] #3.2 - Написать тесты (Hardhat)
- [x] #3.3 - Создать deployment скрипт
- [x] #3.4 - Документация по контракту

**Expected PR:** PR-3 (Smart contract implementation)

---

### 4. [BACKEND] Инициализация Maven проекта
**ID:** #4  
**Labels:** `backend`, `infra`  
**Depends on:** #1, #2  
**Description:**
Создать Multi-Module Maven проект для backend сервисов.

**Структура:**
```
backend/
├── pom.xml (parent)
├── common/
│   ├── event-model/
│   └── shared-contracts/
├── command-service/
├── event-store-service/
├── audit-writer-service/
└── query-service/
```

**Subtasks:**
- [x] #4.1 - Создать parent pom.xml с зависимостями
- [x] #4.2 - Создать common модули
- [x] #4.3 - Создать скелеты сервисов

**Expected PR:** PR-4 (Maven project structure)

---

### 5. [BACKEND] Command Service - скелет
**ID:** #5  
**Labels:** `backend`  
**Depends on:** #4, #2  
**Description:**
Создать основу Command Service: Spring Boot приложение с REST API для приема команд.

**Требования:**
- Spring Boot 4.x + WebFlux setup
- REST controller: `POST /commands/user/login`
- Kafka producer для публикации событий
- Simple in-memory event storage (позже заменим на БД)

**Acceptance Criteria:**
- Сервис запускается на port 8081
- Можно отправить POST команду `curl -X POST http://localhost:8081/commands/user/login -H "Content-Type: application/json" -d '{"userId": "user1"}'`
- Событие публикуется в Kafka topic `user.login.events`

**Subtasks:**
- [x] #5.1 - Spring Boot приложение с Kafka producer
- [x] #5.2 - REST endpoint для `UserLoggedIn` события
- [x] #5.3 - Event DTO класс
- [x] #5.4 - application.yml и конфигурация

**Expected PR:** PR-5 (Command Service skeleton)

---

### 6. [BACKEND] Event Store - сервис записи в БД
**ID:** #6  
**Labels:** `backend`  
**Depends on:** #4, #2  
**Description:**
Создать сервис для чтения события из Kafka и сохранения в PostgreSQL.

**Требования:**
- Spring Boot приложение
- Kafka consumer подписан на `user.login.events`
- Spring Data R2DBC для сохранения в БД
- Таблица `audit.events` (id, event_id, aggregate_id, event_type, user_id, payload, created_at, event_hash)

**Schema:**
```sql
CREATE TABLE audit.events (
  id BIGSERIAL PRIMARY KEY,
  event_id VARCHAR(36) NOT NULL UNIQUE,
  aggregate_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  user_id VARCHAR(255),
  payload JSONB NOT NULL,
  event_hash VARCHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**Subtasks:**
- [x] #6.1 - Spring Boot + Spring Data R2DBC setup
- [x] #6.2 - Entity класс и repository
- [x] #6.3 - Kafka consumer
- [x] #6.4 - Liquibase / Flyway миграции
- [x] #6.5 - Тесты

**Expected PR:** PR-6 (Event Store service)

---

### 7. [BACKEND] Audit Writer Service
**ID:** #7  
**Labels:** `backend`, `blockchain`  
**Depends on:** #3, #6, #2  
**Description:**
Создать сервис для записи хэшей событий в блокчейне.

**Требования:**
- Kafka consumer на том же топике `user.login.events`
- Вычисление SHA-256 хэша события
- Web3j клиент для взаимодействия с Ganache
- Вызов `appendAuditRecord` на смарт-контракте
- Обработка транзакций и ошибок

**Subtasks:**
- [x] #7.1 - Web3j setup и конфигурация для Ganache
- [x] #7.2 - Contract wrapper (ABI-aligned bindings)
- [x] #7.3 - Kafka consumer с обработкой событий
- [x] #7.4 - Hash calculation и blockchain write logic
- [x] #7.5 - Retry механизм при сбое
- [x] #7.6 - Тесты с test containers

**Expected PR:** PR-7 (Audit Writer Service)

---

### 8. [BACKEND] Query Service - MVP
**ID:** #8  
**Labels:** `backend`  
**Depends on:** #6, #2  
**Description:**
Создать Query Service с REST API для получения истории событий.

**Endpoints:**
- `GET /api/audit-logs` - список событий с фильтром
- `GET /api/audit-logs/{id}` - деталь события
- Query параметры: `?userId=...&eventType=...&from=...&to=...`

**Subtasks:**
- [x] #8.1 - Spring Boot WebFlux setup + reactive filtering/query layer
- [x] #8.2 - REST controllers
- [x] #8.3 - DTOs и MapStruct mappers
- [x] #8.4 - Reactive filtering/query logic (R2DBC repository/DatabaseClient)
- [x] #8.5 - Тесты

**Expected PR:** PR-8 (Query Service MVP)

---

### 9. [BACKEND] Проверка целостности событий
**ID:** #9  
**Labels:** `backend`, `blockchain`  
**Depends on:** #7, #8  
**Description:**
Добавить endpoint для проверки, что хэш события есть в блокчейне.

**Endpoint:**
- `GET /api/audit-logs/{id}/integrity-check` → проверить хэш в контракте

**Response:**
```json
{
  "auditLogId": 1,
  "eventId": "uuid-string",
  "eventHash": "abc123...",
  "blockchainRecord": {
    "exists": true,
    "transactionHash": "0x...",
    "blockNumber": 12345,
    "timestamp": 1234567890
  },
  "status": "ON_CHAIN"
}
```

> Возможные значения `status`: `ON_CHAIN` (хэш найден в блокчейне), `MISMATCH` (хэш есть в БД, но не в блокчейне), `PENDING` (хэш ещё не был записан в блокчейн — поле `event_hash` отсутствует в БД).

**Subtasks:**
- [x] #9.1 - Web3j клиент для чтения из контракта
- [x] #9.2 - Service для проверки хэша
- [x] #9.3 - REST endpoint
- [x] #9.4 - Интеграционные тесты (SpringBootTest + Testcontainers PostgreSQL: 11 сценариев — ON_CHAIN, MISMATCH, PENDING, 404, 503, 500, query filters)

**Expected PR:** PR-9 (Integrity check endpoint)

---

### 10. [FRONTEND] Базовый UI на Angular
**ID:** #10  
**Labels:** `frontend`  
**Depends on:** #8  
**Description:**
Создать Angular приложение с базовый таблицей аудита.

**Требования:**
- Angular 17+
- Material Design components
- Таблица событий с колонками: ID, Тип события, Пользователь, Время, Статус целостности
- Фильтры по типу события и пользователю
- Детальный просмотр события (drawer / modal)

**Subtasks:**
- [x] #10.1 - Angular проект setup (ng new)
- [x] #10.2 - Material modules setup
- [x] #10.3 - Components для таблицы и фильтров
- [x] #10.4 - HTTP client для Query Service
- [x] #10.5 - Роутинг и layout
- [x] #10.6 - Стили и responsive дизайн

**Expected PR:** PR-10 (Angular UI skeleton)

---

### 11. [FRONTEND] Интеграция с Backend API
**ID:** #11  
**Labels:** `frontend`  
**Depends on:** #10, #8, #9  
**Description:**
Подключить Frontend к Query Service и Integrity Check APIs.

**Requirements:**
- Angular HttpClient для запросов
- RxJS observables для обработки async данных
- Таблица событий с реальными данными из БД
- Индикатор целостности в таблице (OK / MISMATCH)
- Детальный просмотр с проверкой blockchain записи

**Subtasks:**
- [x] #11.1 - Services для HTTP запросов
- [x] #11.2 - Models / Interfaces для типизации
- [x] #11.3 - Таблица с real data
- [x] #11.4 - Pagination и lazy loading
- [x] #11.5 - Error handling и loading states
- [x] #11.6 - Unit тесты

**Expected PR:** PR-11 (Frontend API integration)

---

### 12. [DOCS] Документация архитектуры
**ID:** #12  
**Labels:** `docs`  
**Depends on:** #1  
**Description:**
Написать подробную документацию архитектуры с диаграммами.

**Файлы:**
- `docs/ARCHITECTURE.md` - описание всех компонентов
- `docs/CQRS_FLOW.md` - flow диаграмма
- `docs/DEPLOYMENT.md` - инструкции по развертыванию
- `docs/TESTING_SCENARIOS.md` - live demo сценарии

**Subtasks:**
- [x] #12.1 - ARCHITECTURE.md с диаграммами (ASCII art) ✅ 189 lines
- [x] #12.2 - CQRS_FLOW.md - пошаговый flow с примерами ✅ 364 lines
- [x] #12.3 - DEPLOYMENT.md - quickstart guide ✅ 348 lines
- [x] #12.4 - TESTING_SCENARIOS.md - curl команды и скриншоты ✅ screenshot pack added

**Expected PR:** PR-12 (Architecture documentation) ✅ Implemented via PR #124

---

### 13. [INFRA] CI/CD Pipeline
**ID:** #13  
**Labels:** `infra`  
**Depends on:** #4, #5, #6, #7, #8, #10  
**Description:**
Настроить GitHub Actions для автоматизированного тестирования и сборки.

**Pipeline:**
- Maven: `mvn clean test`
- Frontend: `npm test`
- Blockchain: `hardhat test`
- Docker build для каждого сервиса

**Subtasks:**
- [ ] #13.1 - Backend tests workflow (maven)
- [ ] #13.2 - Frontend tests workflow (npm)
- [ ] #13.3 - Blockchain tests workflow (hardhat)
- [ ] #13.4 - Docker build workflow
- [ ] #13.5 - Code coverage reports

**Expected PR:** PR-13 (CI/CD setup)

---

## Phase 2 - Расширения

### 14. [BACKEND] Поддержка дополнительных типов событий
**ID:** #14  
**Labels:** `backend`  
**Depends on:** #5, #6  
**Description:**
Добавить поддержку событий:
- `UserProfileChanged`
- `EntityCreated`
- `EntityUpdated`
- `DataDeleted`

**Subtasks:**
- [ ] #14.1 - Event DTO классы для новых типов
- [ ] #14.2 - Command endpoints
- [ ] #14.3 - Database schema updates
- [ ] #14.4 - Tests

**Expected PR:** PR-14 (Event types expansion)

---

### 15. [BACKEND] Authentication & Authorization
**ID:** #15  
**Labels:** `backend`, `frontend`  
**Depends on:** #5, #8, #10  
**Description:**
Добавить JWT аутентификацию и роли.

**Requirements:**
- Spring Security + JWT tokens
- Роли: `AUDITOR`, `ADMIN`, `USER`
- Login endpoint: `POST /auth/login`
- Token-based requests

**Subtasks:**
- [ ] #15.1 - Spring Security setup
- [ ] #15.2 - JWT token generation и validation
- [ ] #15.3 - Login endpoint
- [ ] #15.4 - Role-based access control
- [ ] #15.5 - Frontend authentication service
- [ ] #15.6 - Protected routes в Angular

**Expected PR:** PR-15 (Authentication & Authorization)

---

### 16. [FRONTEND] Advanced filtering и search
**ID:** #16  
**Labels:** `frontend`  
**Depends on:** #11  
**Description:**
Улучшить UI с расширенным поиском и фильтрацией.

**Features:**
- Полнотекстовый поиск по содержимому события
- Дата-рейнж пикер
- Множественные фильтры одновременно
- Сохранение фильтров в URL
- Export в CSV

**Subtasks:**
- [ ] #16.1 - Query параметры в URL
- [ ] #16.2 - Advanced filter components
- [ ] #16.3 - Date range picker
- [ ] #16.4 - Export функционал
- [ ] #16.5 - State management (localStorage)

**Expected PR:** PR-16 (Advanced filtering)

---

### 17. [FRONTEND] Event timeline visualization
**ID:** #17  
**Labels:** `frontend`  
**Depends on:** #16  
**Description:**
Добавить timeline view событий.

**Features:**
- Временная шкала событий
- Группировка по дням/часам
- Интерактивные точки на timeline
- Быстрый переход между событиями

**Subtasks:**
- [ ] #17.1 - Timeline component
- [ ] #17.2 - Event grouping logic
- [ ] #17.3 - Styling и animations
- [ ] #17.4 - Responsive на мобильных

**Expected PR:** PR-17 (Timeline visualization)

---

### 18. [BACKEND] Reconciliation Service
**ID:** #18  
**Labels:** `backend`  
**Depends on:** #9, #14  
**Description:**
Создать сервис для проверки целостности всех записей и поиска "подорванных" данных.

**Features:**
- Batch проверка всех событий
- Отчет о несоответствиях
- Автоматический запуск по расписанию (Quartz)
- Endpoint для ручной проверки

**Subtasks:**
- [ ] #18.1 - Batch integrity checker logic
- [ ] #18.2 - Scheduled task setup (Quartz)
- [ ] #18.3 - Reconciliation report service
- [ ] #18.4 - REST endpoint для запуска проверки
- [ ] #18.5 - Тесты

**Expected PR:** PR-18 (Reconciliation Service)

---

### 19. [INFRA] Kubernetes манифесты
**ID:** #19  
**Labels:** `infra`  
**Depends on:** #13  
**Description:**
Создать K8s Deployments для production развертывания.

**Resources:**
- Deployment для каждого сервиса
- Services, ConfigMaps, Secrets
- Helm chart
- Ingress конфигурация

**Subtasks:**
- [ ] #19.1 - K8s deployments для backend сервисов
- [ ] #19.2 - K8s deployments для frontend
- [ ] #19.3 - ConfigMaps и StatefulSets для Kafka/PostgreSQL
- [ ] #19.4 - Helm chart
- [ ] #19.5 - Ingress setup

**Expected PR:** PR-19 (Kubernetes deployment)

---

### 20. [DOCS] Live Demo сценарий
**ID:** #20  
**Labels:** `docs`  
**Depends on:** #12, #18  
**Description:**
Подготовить готовый сценарий для демонстрации на собеседовании.

**Content:**
- Step-by-step инструкции
- Shell/curl команды для тестирования
- Скриншоты UI
- Объяснение архитектурных решений
- Потенциальные вопросы и ответы

**Subtasks:**
- [ ] #20.1 - Написать guide
- [ ] #20.2 - Prepare curl команды
- [ ] #20.3 - Prepare скриншоты UI
- [ ] #20.4 - Q&A документ

**Expected PR:** PR-20 (Demo scenario documentation)

---

## Labels Reference

| Label | Описание |
|-------|----------|
| `backend` | Java/Spring Backend работы |
| `blockchain` | Solidity/Web3 работы |
| `frontend` | Angular/TypeScript работы |
| `infra` | Docker, K8s, CI/CD |
| `docs` | Документация |
| `bug` | Баг или issue |
| `enhancement` | Улучшение существующей функции |
| `question` | Вопрос или обсуждение |

---

## Dependency Graph

```
#1 (Setup)
├─ #2 (Docker Compose) ──┬─ #3 (Smart Contract) ─┬─ #7 (Audit Writer)
│                        │                        └─ #9 (Integrity Check)
│                        │
├─ #4 (Maven setup) ─────┬─ #5 (Command Service) ─ #6 (Event Store)
│                        │                          ├─ #8 (Query Service)
│                        │                          └─ #7 (Audit Writer)
│                        │
│                        └─ #6 (Event Store) ───────┬─ #8 (Query Service)
│                                                     └─ #9 (Integrity Check)
│
├─ #8 (Query Service) ─── #10 (Frontend UI) ─ #11 (API Integration)
│                                              ├─ #16 (Advanced Filtering)
│                                              └─ #17 (Timeline)
│
├─ #12 (Documentation)
│
└─ #13 (CI/CD) ──────── #14-20 (Phase 2)
```

---

## Migration Path (PR linking)

Каждый PR должен быть связан с Issue через:
```
Closes #XX
Relates to #YY
```

**Пример PR description:**

```markdown
## Description
Implements basic Command Service with Kafka producer

## Closes
- Closes #5

## Related
- Relates to #4
- Relates to #2 (Docker Compose)

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex logic
- [ ] Tests added/updated
- [ ] Documentation updated

## Testing
Steps to verify:
```bash
mvn clean test
curl -X POST http://localhost:8081/commands/user/login ...
```

## Screenshots (if applicable)
<!-- Add screenshots here -->
```

---

## Notes

1. **Последовательность фаз:** MVP phase должна быть завершена перед Phase 2
2. **Branching strategy:** Каждый issue → branch по шаблону `<type>/#XX-description`, где `type` = `feature|fix|docs|test`
3. **PR reviews:** Minimum 1 approval перед merge
4. **Commit messages:** `[#XX] Brief description` (с номером issue)
5. **Project board:** Используем GitHub Project для визуализации статуса
