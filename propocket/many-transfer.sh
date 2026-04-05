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
curl -sS -X POST "http://localhost:8080/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "new.user@example.com",
    "password": "asdasdasda",
    "userName": "niki",
    "phone": "+79001234567"
  }'

curl -sS -X POST "http://localhost:8080/auth/token" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "new.user@example.com",
    "password": "asdasdasda"
  }'

curl -sS -X POST "http://localhost:8080/transfer/account-number" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3NzUzOTc0MTYsImlhdCI6MTc3NTM5MzgxNiwidXNlcklkIjoiNjc1NmYzODUtODY1Ni00NjI2LWE3ZTYtYmU0MDNlZjU4MDAyIiwiZXhwIjoxNzc1Mzk3NDE2LCJpYXQiOjE3NzUzOTM4MTZ9.gmJPwghGFXKT72qgYIceoUY9qYa5st7tCb3E7TJGKSg" \
  -d '{
    "userId": "6756f385-8656-4626-a7e6-be403ef58002",
    "fromAccount": "896050114513",
    "toAccount": "8901201002",
    "amount": 100.00
  }'

