# Need to change
The service cause its a bad design methods on a lont term.

┌───────────────────────────┐
│        Controller         │  →  Handles HTTP (REST APIs)
└────────────┬──────────────┘
             │ calls
┌────────────▼──────────────┐
│          Service          │  →  Business logic (UserService)
└────────────┬──────────────┘
             │ delegates
┌────────────▼──────────────┐
│        Repository         │  →  Data access (UserRepository)
└───────────────────────────┘

