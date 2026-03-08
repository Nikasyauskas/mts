curl -X POST http://localhost:8080/transfer/account-number \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "e4224dc9-ac32-4682-a43c-d7cfc791af5b",
    "fromAccount": "8901201001",
    "toAccount": "8901201002",
    "amount": 100.00
  }'