# Smart Public Infra Spring Boot Backend

This project is the Spring Boot version of the original Flask backend.

## Stack

- Spring Boot 3.5.13
- Spring Web
- Spring Data JPA
- H2 file database
- Maven Wrapper

## Structure

- `controller` for REST endpoints and SPA entry routing
- `service` for business logic
- `model` for JPA entities
- `repository` for database access
- `config` for CORS, static resources, and app properties
- `exception` for centralized API error handling

## Run

```powershell
cd spring-backend
.\mvnw.cmd spring-boot:run
```

The app starts on `http://localhost:5000`.

## Notes

- Complaint images are written to `../static/uploads`
- The built frontend is served from `../static/frontend`
- H2 data is stored in `spring-backend/data`
- Demo admin credentials are `admin / 1234`
