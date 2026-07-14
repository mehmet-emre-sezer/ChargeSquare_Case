# START Akışı

Request:

```http
POST /sessions
Content-Type: application/json

{
  "userId": 7,
  "connectorId": 10
}
```

Sequence:

```mermaid
sequenceDiagram
    participant C as Client
    participant SS as Session Service
    participant DBs as Session DB
    participant ST as Station Service
    participant DBt as Station DB

    C->>SS: POST /sessions<br/>userId=7, connectorId=10

    SS->>DBs: Wallet var mı?
    DBs-->>SS: Var, balance=500.00 TRY

    SS->>ST: GET /connectors/10
    ST->>DBt: Connector + tariff oku
    DBt-->>ST: AVAILABLE + 8.50 TRY/kWh + 2.00 fee
    ST-->>SS: Connector snapshot

    SS->>SS: Connector AVAILABLE mı?

    SS->>ST: POST /connectors/10/occupy
    ST->>DBt: Connector status = OCCUPIED
    DBt-->>ST: OK
    ST-->>SS: OCCUPIED

    SS->>DBs: ACTIVE session oluştur<br/>tariff snapshot kaydet
    DBs-->>SS: sessionId=100

    SS-->>C: 201 CREATED<br/>ACTIVE session
```

Kritik fikir: Session başlamadan önce Station Service'e gerçekten sorulur. Connector müsait değilse session yaratılmaz.

