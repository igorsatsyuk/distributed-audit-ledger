# GitHub Issues - Ready Templates

Готовые шаблоны для создания first 5 issues. Копируй и паст в GitHub Issues.

---

## Issue #1: [SETUP] Инициализация репозитория

**Title:** `[SETUP] Initialize repository structure and base configuration`

**Labels:** `infra`, `docs`, `Phase-1-MVP`

**Description:**

```markdown
## 🎯 Goal
Создать базовую структуру репозитория, инициализировать Git и подготовить основные конфигурационные файлы.

## 📝 Description
Это foundation issue - все остальные работы будут depend на этом.

Нужно создать:
- Базовую структуру каталогов согласно архитектуре
- Основные конфигурационные файлы (.gitignore, README.md, LICENSE)
- Этот репозиторий готов к дальнейшей разработке

## ✅ Acceptance Criteria
- [x] Репозиторий инициализирован git init
- [x] Создана структура каталогов (backend/, blockchain/, frontend/, deploy/, docs/)
- [x] Создан файл .gitignore (Java, Node.js, Python, IDE)
- [x] Создан LICENSE (MIT)
- [x] Создан базовый README.md
- [x] Создано 3+ основных файла документации в docs/
- [x] Push первым коммитом в main/master

## 📋 Subtasks
- [ ] #1.1 - Создать folder structure согласно плану
- [ ] #1.2 - Инициализировать git и создать .gitignore
- [ ] #1.3 - Создать LICENSE, README.md и базовую документацию

## 📚 Files to create

```
distributed-audit-ledger/
├── backend/
│   ├── command-service/
│   ├── query-service/
│   ├── event-store-service/
│   ├── audit-writer-service/
│   ├── common/
│   │   ├── event-model/
│   │   └── shared-contracts/
│   └── pom.xml (parent)
├── blockchain/
│   ├── contracts/
│   ├── migrations/
│   └── package.json (or hardhat.config.js)
├── frontend/
│   └── audit-ui/
├── deploy/
│   ├── docker-compose.yml
│   └── env/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── CQRS_FLOW.md
│   └── DEPLOYMENT.md
├── .gitignore
├── LICENSE (MIT)
├── README.md
├── GITHUB_ISSUES_PLAN.md
├── GITHUB_MANAGEMENT_GUIDE.md
└── .github/
    ├── pull_request_template.md
    └── workflows/ (для CI/CD позже)
```

## 🔗 Dependencies
- None (это первый issue)

## 🔗 Blocks
- #2 (Docker Compose)
- #4 (Maven setup)
- All other issues
```

---

## Issue #2: [INFRA] Docker Compose для инфраструктуры

**Title:** `[INFRA] Setup Docker Compose with PostgreSQL, Kafka, Ganache`

**Labels:** `infra`, `Phase-1-MVP`

**Description:**

```markdown
## 🎯 Goal
Подготовить docker-compose файл с основной инфраструктурой (PostgreSQL, Kafka, Ganache).

## 📝 Description
Создать docker-compose.yml с:
- PostgreSQL 14+ для Event Store
- Kafka + Zookeeper для messaging
- Ganache (локальный блокчейн) для смарт-контрактов
- pgAdmin для управления БД (опционально)
- Redis для кэширования (опционально)

После `docker-compose up -d`:
- PostgreSQL доступен на :5432
- Kafka доступен на :9092
- Ganache доступен на :8545
- Zookeeper на :2181

## ✅ Acceptance Criteria
- [x] docker-compose.yml содержит сервисы: PostgreSQL, Kafka, Zookeeper, Ganache
- [x] Все сервисы запускаются без ошибок: `docker-compose up -d`
- [x] PostgreSQL инициализируется с пустой БД (или базовым schema)
- [x] Kafka ready для подписки на topics
- [x] Ganache запущен и RPC доступен на http://localhost:8545
- [x] Создано .env.example с всеми необходимыми переменными
- [x] Создан README в deploy/ с инструкциями

## 📋 Subtasks
- [ ] #2.1 - Создать docker-compose.yml с основными сервисами
- [ ] #2.2 - Настроить переменные окружения и .env файлы
- [ ] #2.3 - Создать скрипты инициализации PostgreSQL (init-db.sql)
- [ ] #2.4 - Написать README для запуска инфраструктуры

## 🧪 Testing
```bash
cd deploy

# Start all services
docker-compose up -d

# Check services
docker-compose ps

# Verify PostgreSQL
psql -h localhost -U postgres -d audit_ledger -c "SELECT 1"

# Verify Kafka
docker exec kafka kafka-broker-api-versions.sh --bootstrap-server kafka:9092

# Verify Ganache
curl http://localhost:8545 -X POST -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}'

# Cleanup
docker-compose down -v
```

## 🔗 Dependencies
- Depends on: #1

## 🔗 Blocks
- #3 (Blockchain)
- #5 (Command Service)
- #6 (Event Store)
- #7 (Audit Writer)
```

---

## Issue #3: [BLOCKCHAIN] Смарт-контракт AuditLedger

**Title:** `[BLOCKCHAIN] Implement AuditLedger smart contract`

**Labels:** `blockchain`, `Phase-1-MVP`

**Description:**

```markdown
## 🎯 Goal
Разработать Solidity смарт-контракт для хранения хэшей аудит-событий в блокчейне.

## 📝 Description
Создать контракт AuditLedger с функциями:
1. `appendAuditRecord()` - добавить новую запись (хэш события)
2. `getRecord()` - получить запись по индексу
3. `getRecordsCount()` - количество записей
4. `isHashExists()` - проверить наличие хэша

Контракт должен быть:
- Развернут на локальном Ganache
- Защищен от несанкционированного доступа (простой Ownable или whitelist)
- Иметь события (Events) для анализа логов

## ✅ Acceptance Criteria
- [x] Создан файл contracts/AuditLedger.sol
- [x] Контракт компилируется без ошибок (`hardhat compile`)
- [x] Написаны unit тесты (Hardhat/Chai) - минимум 5 тестов
- [x] Все тесты проходят (`hardhat test`)
- [x] Создан deployment скрипт (scripts/deploy.js)
- [x] Контракт успешно развернут на Ganache
- [x] Написана документация контракта (contracts/AuditLedger.md)

## 📋 Subtasks
- [ ] #3.1 - Настроить Hardhat проект (npm init, npm install)
- [ ] #3.2 - Написать AuditLedger.sol контракт
- [ ] #3.3 - Написать unit тесты (test/AuditLedger.test.js)
- [ ] #3.4 - Написать deployment скрипт
- [ ] #3.5 - Написать документацию

## 📝 Contract Specification

```solidity
pragma solidity ^0.8.0;

contract AuditLedger {
    struct AuditRecord {
        bytes32 eventHash;
        uint256 timestamp;
        string eventType;
        address source;
    }

    AuditRecord[] public records;
    address public owner;

    event RecordAppended(bytes32 indexed eventHash, string eventType, address indexed source);

    /// @dev Добавить новую аудит-запись
    function appendAuditRecord(
        bytes32 _hash,
        uint256 _timestamp,
        string memory _eventType,
        address _source
    ) public onlyOwner {
        records.push(AuditRecord({
            eventHash: _hash,
            timestamp: _timestamp,
            eventType: _eventType,
            source: _source
        }));
        emit RecordAppended(_hash, _eventType, _source);
    }

    /// @dev Получить запись по индексу
    function getRecord(uint256 _index) public view returns (AuditRecord memory) {
        require(_index < records.length, "Index out of bounds");
        return records[_index];
    }

    /// @dev Количество записей
    function getRecordsCount() public view returns (uint256) {
        return records.length;
    }

    /// @dev Проверить наличие хэша
    function isHashExists(bytes32 _hash) public view returns (bool) {
        for (uint256 i = 0; i < records.length; i++) {
            if (records[i].eventHash == _hash) return true;
        }
        return false;
    }
}
```

## 🧪 Testing Examples

```javascript
describe("AuditLedger", function () {
    it("Should append audit record", async function () {
        const hash = ethers.utils.keccak256(ethers.utils.toUtf8Bytes("test"));
        await auditLedger.appendAuditRecord(hash, Date.now(), "UserLoggedIn", owner.address);
        expect(await auditLedger.getRecordsCount()).to.equal(1);
    });

    it("Should check if hash exists", async function () {
        const hash = ethers.utils.keccak256(ethers.utils.toUtf8Bytes("test"));
        await auditLedger.appendAuditRecord(hash, Date.now(), "UserLoggedIn", owner.address);
        expect(await auditLedger.isHashExists(hash)).to.equal(true);
    });
    // ...
});
```

## 🔗 Dependencies
- Depends on: #1, #2

## 🔗 Blocks
- #7 (Audit Writer Service)
```

---

## Issue #4: [BACKEND] Initialize Maven project structure

**Title:** `[BACKEND] Create Maven multi-module project structure`

**Labels:** `backend`, `infra`, `Phase-1-MVP`

**Description:**

```markdown
## 🎯 Goal
Создать Multi-Module Maven проект для всех backend сервисов.

## 📝 Description
Создать структуру Maven проекта с:
1. Parent pom.xml (dependency management)
2. Модули: common/, command-service/, event-store-service/, query-service/, audit-writer-service/

## ✅ Acceptance Criteria
- [x] Parent pom.xml создан с управлением версиями и зависимостями
- [x] Все модули создали как Maven modules (pom.xml for each)
- [x] Spring Boot, Spring WebFlux, Kafka dependencies добавлены в parent
- [x] Все модули компилируются без ошибок (`mvn clean compile`)
- [x] Структура каталогов соответствует Maven best practices
- [x] Создан .mvn/wrapper/ для гарантированной версии Maven

## 📋 Subtasks
- [ ] #4.1 - Создать parent pom.xml с dependency management
- [ ] #4.2 - Создать pom.xml для каждого модуля
- [ ] #4.3 - Добавить основные Spring Boot зависимости
- [ ] #4.4 - Верифицировать что все компилируется

## 📝 Dependency Versions (в parent pom.xml)

- Java: 25+
- Spring Boot: 4.x
- Spring Cloud: 2023.0.0+
- Maven: 3.8.0+

## 🧪 Testing

```bash
cd backend

# Build all modules
mvn clean package

# Verify each module
mvn clean install
```

## 🔗 Dependencies
- Depends on: #1, #2

## 🔗 Blocks
- #5 (Command Service)
- #6 (Event Store)
- #7 (Audit Writer)
- #8 (Query Service)
```

---

## Issue #5: [BACKEND] Command Service - skeleton

**Title:** `[BACKEND] Implement Command Service skeleton with Kafka producer`

**Labels:** `backend`, `Phase-1-MVP`

**Description:**

```markdown
## 🎯 Goal
Создать базовый Command Service с REST API для приема команд и их публикации в Kafka.

## 📝 Description
Command Service - это микросервис, который:
1. Принимает HTTP команды
2. Валидирует их
3. Публикует события в Kafka

Начинаем с простого сценария: `UserLoggedIn` событие.

## ✅ Acceptance Criteria
- [x] Spring Boot приложение запускается на :8081
- [x] REST endpoint: `POST /commands/user/login`
- [x] Kafka producer отправляет события в topic `user.login.events`
- [x] JSON request/response правильно обработаны
- [x] Логирование работает
- [x] Интеграционные тесты проходят
- [x] application.yml/properties настроены

## 📋 Subtasks
- [ ] #5.1 - Создать Spring Boot application class и application.yml
- [ ] #5.2 - Создать UserLoggedIn event DTO dalam common/event-model
- [ ] #5.3 - Создать REST controller с endpoint POST /commands/user/login
- [ ] #5.4 - Создать Kafka producer configuration and service
- [ ] #5.5 - Написать integration tests

## 📝 API Specification

### Request
```bash
POST /commands/user/login HTTP/1.1
Content-Type: application/json

{
  "userId": "user123",
  "loginTime": "2024-05-13T10:30:00Z",
  "ipAddress": "192.168.1.1"
}
```

### Response (201 Created)
```json
{
  "commandId": "cmd-abc123",
  "status": "ACCEPTED",
  "eventPublishedAt": "2024-05-13T10:30:00.123Z"
}
```

## 🧪 Testing

```bash
cd backend/command-service

# Start Docker services first
docker-compose -f ../../deploy/docker-compose.yml up -d

# Build and test
mvn clean test

# Run application
mvn spring-boot:run

# Test endpoint
curl -X POST http://localhost:8081/commands/user/login \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "loginTime": "2024-05-13T10:30:00Z",
    "ipAddress": "192.168.1.1"
  }'

# Verify message in Kafka
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic user.login.events \
  --from-beginning
```

## 🔗 Dependencies
- Depends on: #1, #2, #4

## 🔗 Blocks
- #6 (Event Store Service)
- #7 (Audit Writer Service)
```

---

## Как использовать эти templates:

1. Скопируй содержимое каждого issue
2. Создай новый issue на GitHub
3. Паст description в текстовое поле
4. Выбери labels
5. Нажми "Create issue"

**Result:** 5 issues с номерами #1-#5 автоматически создадут dependency chain для разработки.


