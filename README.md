# mts
MTS - money transfer system

## Requirements
* docker
* java 11 и выше
* scala 2.13
* sbt 1.10.6
* python 3

## 1. Запуск PostgreSQL

```bash
docker compose up -d
```

Этот команда:
- Поднимет PostgreSQL контейнер на порту 5434
- Автоматически выполнит SQL скрипт из `sql/db.sql` при первом запуске
- Создаст все необходимые таблицы и тестовые данные

### Параметры подключения

- **Host**: localhost
- **Port**: 5434
- **Database**: postgres
- **Username**: postgres
- **Password**: postgres

### Полезные команды

```bash
# Запуск контейнера
docker compose up -d

# Остановка контейнера
docker compose down

# Просмотр логов
docker compose logs -f postgres

# Подключение к БД через psql
docker compose exec postgres psql -U postgres

# Пересоздание БД (удалит все данные!)
docker compose down -v
docker compose up -d
```

### Проверка работы

После запуска можно проверить подключение:

```bash
docker compose exec postgres psql -U postgres -c "SELECT * FROM bank.users;"
```

## 2. Компиляция и запуск приложения
Используем sbt
```bash
sbt run
```
приложени должно запуститься с логими, символизирующими, что сервер запущен.
```bash
2026-01-17T23:11:34.82126+03:00  INFO msg:="test configuration localhost:8080"
2026-01-17T23:11:36.317491+03:00 INFO msg:="Starting the server..."
2026-01-17T23:11:36.460907+03:00 INFO msg:="Server started"
```

## 3. Запуск frontend'а (mts-frontend.py)

### 3.1 Создание виртуального окружения

```bash
# Создать виртуальное окружение
python3 -m venv venv

# Активировать виртуальное окружение
# На Linux/macOS:
source venv/bin/activate
# На Windows:
# venv\Scripts\activate
```

### 3.2 Установка зависимостей

```bash
# Убедитесь, что виртуальное окружение активировано
pip install -r requirements.txt
```

### 3.3 Запуск приложения

```bash
# Убедитесь, что виртуальное окружение активировано и PostgreSQL запущен
python3 mts-frontend.py
```

### Управление приложением

- **Q** - Выход из приложения
- **← →** - Переключение между вкладками (Users, Transactions, Balance History)
- **↑ ↓** - Прокрутка данных
- **R** - Обновление данных

### Деактивация виртуального окружения

```bash
deactivate
```

## 4. Пересылание денег с аккаунта на аккаунт
исполнием REST запрос (пример в propocket/many-transfer.sh)
```bash
curl -X POST http://localhost:8080/transfer/account-number \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccount": "8901201001",
    "toAccount": "8901201002",
    "amount": 100.00
  }'
```