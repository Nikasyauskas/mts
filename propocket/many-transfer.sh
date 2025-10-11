curl -X POST http://localhost:8080/transfer/account-number \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccount": "8901201001",
    "toAccount": "8901201002",
    "amount": 100.00
  }'