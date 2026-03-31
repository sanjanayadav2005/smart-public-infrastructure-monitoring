# Java backend

This is the Java version of the original Flask backend.

It keeps the same API routes used by the React frontend:

- `POST /api/report-issue`
- `POST /api/admin/login`
- `POST /api/admin/logout`
- `GET /api/admin/session`
- `GET /api/complaints`
- `POST /api/resolve/{id}`

It also serves:

- uploaded files from `static/uploads`
- the built frontend from `static/frontend`

## Run

From the project root:

```powershell
.\java-backend\run-java-backend.ps1
```

The server starts on `http://localhost:5000`.

## Notes

- Data is still stored in `database.db` through SQLite.
- Admin demo credentials stay the same: `admin / 1234`.
