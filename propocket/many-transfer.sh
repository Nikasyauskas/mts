curl -X POST http://localhost:8080/transfer/ \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": "bf7e2e36-350b-4ea7-ae7d-ff4ce38d3476",
    "toAccountId": "3c4987b6-c696-4768-97e2-f79622a49e6b",
    "amount": 50.00
  }'

curl -X POST http://localhost:8080/transfer/account-number \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccount": "8901201001",
    "toAccount": "8901201002",
    "amount": 50.00
  }'