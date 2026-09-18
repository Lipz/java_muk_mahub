┌─────────────────┐
│     systems     │
│─────────────────│
│ uuid PK         │
│ name            │
│ created_at      │
└────────┬────────┘
         │
         │ 1:N
         ▼
┌─────────────────┐
│     servers     │
│─────────────────│
│ uuid PK         │
│ system_id FK    │
│ name            │
│ ip              │
│ server_type     │
│ created_at      │
└───────┬─────────┘
        │
        ├───────────────┐
        │               │
        │ 1:N           │ 1:N
        ▼               ▼
┌───────────────┐  ┌──────────────────┐
│ server_logs   │  │ server_resources │
│───────────────│  │──────────────────│
│ uuid PK       │  │ uuid PK          │
│ server_id FK  │  │ server_id FK     │
│ channel       │  │ channel          │
│ pub_path      │  │ record_timestamp │
│ save_path     │  │ payload JSON     │
└───────────────┘  └──────────────────┘
                         

        servers
           │
           │ 1:N
           ▼
┌──────────────────┐
│ server_storage   │
│──────────────────│
│ uuid PK          │
│ server_id FK     │
│ channel          │
│ record_timestamp │
│ payload JSON     │
└──────────────────┘