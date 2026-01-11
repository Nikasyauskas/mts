# mts
MTS - money transfer system

## Запуск PostgreSQL

### Быстрый старт

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