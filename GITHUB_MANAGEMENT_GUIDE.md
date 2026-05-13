# GitHub Issues & PR Management Guide

## 🚀 Как использовать этот план

Этот документ описывает процесс создания и управления GitHub Issues для проекта **Distributed Audit Ledger**.

---

## 📋 Процесс создания Issue

### 1. Создание Issue в GitHub

Перейти на: `https://github.com/<your-username>/distributed-audit-ledger/issues`

Нажать **"New Issue"** и заполнить:

```
Title:        [LABEL] Описание issue
Description:  <скопировать из GITHUB_ISSUES_PLAN.md>
Labels:       backend, infra, docs, blockchain, frontend
Project:      Distributed Audit Ledger (если есть проект)
```

### 2. Структура Issue

**Обязательные части:**

```markdown
## 🎯 Goal
Одно предложение с целью

## 📝 Description
Подробное описание требований

## ✅ Acceptance Criteria
- [ ] Точный критерий 1
- [ ] Точный критерий 2
- [ ] ...

## 📚 Resources
- Link to docs
- Related issues: #XX, #YY

## 🔗 Dependencies
- Depends on: #XX
- Blocks: #YY
```

### 3. Subtasks (Checklist)

Внутри Issue Description добавляем subtasks:

```markdown
## 🔨 Subtasks
- [ ] #X.1 - Subtask 1
- [ ] #X.2 - Subtask 2
- [ ] #X.3 - Subtask 3
```

**Значение:** Каждый subtask представляет вспомогательную задачу (может быть отдельным коммитом или частью одного PR).

---

## 🌿 Branching Strategy

### Создание Feature Branch

```bash
git checkout -b feature/#XX-brief-description

# Примеры:
git checkout -b feature/#5-command-service-skeleton
git checkout -b feature/#3-audit-ledger-contract
git checkout -b feature/#10-angular-ui-setup
```

### Naming Convention

```
feature/#XX-description   # Основная feature
fix/#XX-description        # Bugfix
docs/#XX-description       # Документация
test/#XX-description       # Тесты
```

---

## 💻 Commit Messages

### Format

```
[#XX] Brief description of change

Detailed explanation if needed.
```

### Примеры

```
[#5] Add UserLoggedIn command handler

- Create Command DTO
- Add REST endpoint /commands/user/login
- Configure Kafka producer
- Add unit tests

[#15] Fix authentication token validation
[#12] Update ARCHITECTURE.md with new flow diagram
```

---

## 🔗 Pull Request Workflow

### 1. Создание PR

```markdown
## Description
Краткое описание что сделано

## 🎯 Closes / Relates

Closes #XX              # Закрывает issue (автоматически при merge)
Relates to #YY, #ZZ     # Связанные issues

## 📋 Checklist

- [x] Code follows style guidelines
- [x] Self-review completed
- [x] Tests added/updated
- [x] Documentation updated
- [ ] (if applicable) Screenshots added

## 🧪 Testing

Steps to verify the changes:

```bash
cd backend
mvn clean test
```

Curl example:
```bash
curl -X POST http://localhost:8081/commands/user/login \
  -H "Content-Type: application/json" \
  -d '{"userId": "user1"}'
```

## 📸 Screenshots (if UI changes)

<!-- Add screenshots here -->
```

### 2. PR Title

```
[#XX] Brief description

# Примеры:
[#5] Add UserLoggedIn command endpoint
[#10] Implement Angular audit log table
[#13] Setup GitHub Actions CI/CD pipeline
```

### 3. Linking PR to Issue

В PR description всегда указываем:
- `Closes #XX` - если PR закрывает issue полностью
- `Relates to #XX` - если PR связан с issue, но не закрывает (например, subtask)

**GitHub автоматически закроет issue при merge PR с `Closes #XX`**

---

## 🏗️ Workflow для каждого Issue

### Step 1: Создать Issue в GitHub

```
Title: [BACKEND] Command Service - скелет
Labels: backend, Phase-1
Description: <скопировать из плана>
```

**GitHub автоматически присвоит номер (#5)**

### Step 2: Создать feature branch

```bash
git checkout -b feature/#5-command-service-skeleton
```

### Step 3: Разработка

```bash
# Сделать коммиты с префиксом [#5]
git commit -m "[#5] Add Spring Boot starter and Kafka producer"
git commit -m "[#5] Add UserLoggedIn command DTO"
git commit -m "[#5] Add REST endpoint for login command"
```

### Step 4: Push и Create PR

```bash
git push origin feature/#5-command-service-skeleton
```

На GitHub:
- PR будет с предложением
- В description напишите: `Closes #5`
- Attach PR к project (если есть)
- Request review

### Step 5: Merge после Review

```
Squash and merge / Rebase and merge
```

**GitHub автоматически закроет issue #5**

---

## 📊 Project Board Setup

Рекомендуется создать Project Board для визуализации:

### Columns

1. **Backlog** - Все issues из плана
2. **Ready** - Готовы к разработке (зависимости решены)
3. **In Progress** - Кто-то работает
4. **Review** - PR создан, ждет review
5. **Done** - Merged и закрыто

### Automation

GitHub позволяет автоматизировать:
- Issue добавляется в Backlog при создании
- Перемещается в Ready когда зависимости решены
- Перемещается в In Progress когда PR создан
- Перемещается в Review когда PR создан
- Перемещается в Done когда PR merged

---

## 🔄 Зависимости между Issues

### Как управлять зависимостями

В issue description добавляется:

```markdown
## Dependencies
- **Blocks:** #XX, #YY (этот issue блокирует другие)
- **Depends on:** #AA, #BB (этот issue зависит от других)
- **Related:** #CC, #DD (связанные issues)
```

### Пример: #6 зависит от #4 и #2

```markdown
## Dependencies
- Depends on: #4 (Maven setup), #2 (Docker Compose)
- Blocks: #8, #9 (Query Service и Integrity Check)
```

Перед работой над #6:
1. ✅ #4 должен быть merged
2. ✅ #2 должен быть merged
3. ❌ Не начинать работу если зависимости не готовы

---

## 🏷️ Labels System

| Label | Когда использовать |
|-------|-------------------|
| `backend` | Java/Spring работа |
| `blockchain` | Solidity/Web3 работа |
| `frontend` | Angular/TypeScript работа |
| `infra` | Docker, K8s, CI/CD |
| `docs` | Документация |
| `Phase-1-MVP` | MVP этап (issues #1-#13) |
| `Phase-2` | Расширения (issues #14-#20) |
| `bug` | Баг или problematic behavior |
| `enhancement` | Улучшение существующей функции |
| `question` | Вопрос для командного обсуждения |
| `good-first-issue` | Если планируешь open-source |

### Как назначить labels

CLI:
```bash
gh issue edit 5 -l backend,phase-1-mvp
```

Или через GitHub UI → Labels

---

## 🧮 Progress Tracking

### Посчитать прогресс MVP

Из CLI:
```bash
# Все issues Phase-1
gh issue list -l "Phase-1-MVP" --state all

# Только closed issues (done)
gh issue list -l "Phase-1-MVP" --state closed
```

### Metrics

Отслеживаем:
- **Total issues:** 13 (Phase 1)
- **Closed:** X/13
- **In Progress:** Y/13
- **Blockers:** (issues с открытыми зависимостями)

---

## 📝 PR Template

Создать `.github/pull_request_template.md`:

```markdown
## Description
<!-- Краткое описание изменений -->

## Related Issues
<!-- GitHub автоматически линкует если написать #XX -->
- Closes #XX
- Relates to #YY

## Type of Change
- [ ] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)
- [ ] Breaking change
- [ ] Documentation update

## Checklist
- [ ] My code follows the style guidelines
- [ ] I have self-reviewed my code
- [ ] I have commented complex logic
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] No new warnings generated

## Testing
<!-- Как проверить изменения -->

```bash
# Steps to test
```

## Screenshots (if applicable)
<!-- Add images here -->
```

---

## 🚨 Common Issues & Solutions

### Issue: PR не закрывается автоматически

**Причина:** В описании PR не написано `Closes #XX`

**Решение:** Добавить в PR description:
```
Closes #5
```

### Issue: Зависимость между issues забыли указать

**Причина:** #6 started до #4 merged, конфликт

**Решение:** Заранее писать зависимости в issue description:
```
## Dependencies
- Depends on: #4, #2
```

### Issue: Слишком много changes в одном PR

**Решение:** Разбить на несколько PR для каждого subtask

---

## 💡 Best Practices

1. **One Issue = One Feature**
   - Не смешивать разные фичи в одном issue

2. **Clear Acceptance Criteria**
   - Чтобы знать когда task готов

3. **Link PRs to Issues**
   - `Closes #XX` в description

4. **Commit messages with issue number**
   - `[#XX] Description` в каждом коммите

5. **Update issue status visually**
   - Используй Project Board для видимости

6. **Document in comments**
   - Если есть вопросы → comment на issue

7. **Regular check dependencies**
   - Перед началом work = проверить "Depends on"

---

## 📚 Resources

- [GitHub Issues Docs](https://docs.github.com/en/issues)
- [GitHub Projects Docs](https://docs.github.com/en/issues/organizing-your-work-with-project-boards)
- [PR Best Practices](https://github.com/features/code-review)
- [Conventional Commits](https://www.conventionalcommits.org/)


