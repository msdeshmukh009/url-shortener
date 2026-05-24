# Running URL Shortener Locally

## Prerequisites

Make sure you have the following installed:

| Tool | Version | Download |
|---|---|---|
| Java JDK | 21+ | [adoptium.net](https://adoptium.net) |
| Maven | 3.8+ | [maven.apache.org](https://maven.apache.org/download.cgi) |
| PostgreSQL | Any | [postgresql.org](https://www.postgresql.org/download/windows/) |

Verify your installations by running these in Command Prompt:

```bash
java -version
mvn -version
psql --version
```

---

## 1. Clone or download the project

If using Git:

```bash
git clone https://github.com/your-username/url-shortener.git
cd url-shortener
```

Or just extract the downloaded zip and open the folder.

---

## 2. Set up the database

Open Command Prompt and connect to PostgreSQL:

```bash
psql -U postgres
```

The table already exists in the `postgres` database (default). Verify:

```sql
select count(*) from url_shortener;
```

Type `\q` to exit psql.

---

## 3. Configure application properties

Open `src/main/resources/application.properties` and set your PostgreSQL password:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/postgres
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD_HERE

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

Replace `YOUR_PASSWORD_HERE` with the password you set during PostgreSQL installation.

---

## 4. Build the project

In the project root folder, run:

```bash
mvn clean install -DskipTests
```

This downloads all dependencies and compiles the code. First run takes a few minutes.

---

## 5. Run the application

```bash
mvn spring-boot:run
```

You should see this in the output when it starts successfully:

```
Tomcat initialized with port 8080 (http)
Started UrlShortenerApplication in X.XXX seconds
```

The app is now running at `http://localhost:8080`

---

## 6. Test the endpoints

### Create a short URL

```bash
curl --location 'localhost:8080/api/shorten' \
--header 'Content-Type: application/json' \
--data '{
    "originalUrl":"https://maheshdeshmukh.netlify.app/index.html"
}'
```

### Redirect using a short code

```bash
curl --location 'localhost:8080/api/redirect?shortCode=be2edb862e'
```

---

## 7. Run tests

```bash
mvn test
```

---

## Common errors

| Error | Cause | Fix |
|---|---|---|
| `Failed to configure a DataSource` | `application.properties` missing or empty | Add DB config as shown in step 3 |
| `database "url_shortener" does not exist` | Wrong DB name in JDBC URL | Use `postgres` as the DB name in the URL |
| `duplicate key value violates unique constraint` | Hibernate sequence out of sync | Change `@GeneratedValue` to `GenerationType.IDENTITY` |
| `Port 8080 already in use` | Another app using port 8080 | Add `server.port=8081` to `application.properties` |
| `Connection refused` | PostgreSQL not running | Start PostgreSQL service from Windows Services |

---

## Stopping the app

Press `Ctrl + C` in the terminal where the app is running.