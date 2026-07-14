# STOP / BILL / SETTLE Akışı

Request:

```http
POST /sessions/100/stop
Content-Type: application/json

{
  "energyKwh": 12.5
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

    C->>SS: POST /sessions/100/stop<br/>energyKwh=12.5

    SS->>DBs: Session'ı kilitleyerek oku
    DBs-->>SS: ACTIVE session

    SS->>SS: ACTIVE mi kontrol et
    SS->>SS: Cost hesapla<br/>12.5 * 8.50 + 2.00 = 108.25

    SS->>DBs: Wallet'ı kilitleyerek oku
    DBs-->>SS: balance=500.00 TRY

    SS->>DBs: Wallet debit<br/>500.00 - 108.25 = 391.75
    SS->>DBs: Session COMPLETED yap<br/>cost, endedAt, walletBalanceAfter kaydet

    SS->>ST: POST /connectors/10/release
    ST->>DBt: Connector status = AVAILABLE
    DBt-->>ST: OK
    ST-->>SS: AVAILABLE

    SS-->>C: 200 OK<br/>receipt
```

Beklenen sonuç:

```text
Session status = COMPLETED
Connector status = AVAILABLE
Wallet balance = 391.75
Cost = 108.25
```

