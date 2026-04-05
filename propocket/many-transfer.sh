#!/usr/bin/env bash

# --- Регистрация (пароль ≥ 8 символов) ---
curl -sS -X POST "http://localhost:8080/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "new.user@example.com",
    "password": "secretpass",
    "userName": "New User",
    "phone": "+79001234567"
  }'

# --- Авторизация (логин = email) → JWT в JSON { "token", "tokenType" } ---
# Сид из db.sql: email 007@bank.local, пароль password
curl -sS -X POST "http://localhost:8080/auth/token" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "007@bank.local",
    "password": "password"
  }'

# --- Перевод (userId в теле должен совпадать с claim userId в JWT) ---
curl -sS -X POST "http://localhost:8080/transfer/account-number" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{
    "userId": "e4224dc9-ac32-4682-a43c-d7cfc791af5b",
    "fromAccount": "8901201001",
    "toAccount": "8901201002",
    "amount": 100.00
  }'

## TESTS -------------------------------------------------------------------------------------
