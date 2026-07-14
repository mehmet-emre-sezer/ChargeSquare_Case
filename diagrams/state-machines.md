# State Machine'ler

## Connector State Machine

```mermaid
stateDiagram-v2
    [*] --> AVAILABLE

    AVAILABLE --> OCCUPIED: POST /occupy
    OCCUPIED --> AVAILABLE: POST /release

    OCCUPIED --> OCCUPIED: occupy tekrar gelirse 409
    AVAILABLE --> AVAILABLE: release tekrar gelirse no-op
```

## Session State Machine

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: POST /sessions
    ACTIVE --> COMPLETED: POST /sessions/{id}/stop

    COMPLETED --> COMPLETED: stop tekrar gelirse 409
```

Ana doğruluk kuralları:

```text
Dolu connector'a start olmaz.
Bitmiş session tekrar stop edilemez.
Stop olunca connector boşa çıkar.
```

