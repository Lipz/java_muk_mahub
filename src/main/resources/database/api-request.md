curl -X POST http://localhost:8080/api/v1/systems \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer <TOKEN>" \
    -d '{
    "name": "asycuda"
    }'


curl -X POST http://localhost:8080/api/v1/systems \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer <TOKEN>" \
    -d '{
    "name": "ecustoms"
    }'

curl -X POST http://localhost:8080/api/v1/systems \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer <TOKEN>" \
    -d '{
    "name": "gtas"
    }'

curl -X POST http://localhost:8080/api/v1/systems \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer <TOKEN>" \
    -d '{
    "name": "asyhub"
    }'


curl -X POST http://localhost:8080/api/v1/systems \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer <TOKEN>" \
    -d '{
    "name": "nsw"
    }'
