# UW Track

UW Track is a backend notification system designed to monitor course seat availability at the University of Wisconsin-Madison (UW-Madison).

Built with Spring Boot, the system relies on scheduled Java tasks to periodically fetch course data from `public.enroll.wisc.edu`. It tracks status changes of specific course sections and alerts users via email. MySQL is used to persist application states and log historical course data.

* **Frontend Platform:** [mad-enroll.vercel.app](https://mad-enroll.vercel.app)
* **Frontend Repository:** [Jingmozhiyu/mad-enroll](https://github.com/Jingmozhiyu/mad-enroll)

---

## Deployment

### Deploy on VM

Deploying via **Docker** is highly recommended.

**1. Create a `.env` file** in the project root with the following variables:
```env
MAIL_ADDRESS=your_email@example.com
MAIL_AUTH_CODE=your_mail_auth_code
JWT_SECRET=your_jwt_secret
DB_PASSWORD=your_database_password
# (Optional)
DB_USERNAME=root
```

**2. Create the `docker-compose.yml`** on your remote server:
```yaml
services:
  # 1. Database
  db:
    image: mysql:8.0
    restart: always
    environment:
      MYSQL_DATABASE: course_monitor_db
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
    ports:
      - "3306:3306"
    command: --default-authentication-plugin=mysql_native_password

  # 2. Spring Boot
  app:
    image: eclipse-temurin:21-jre-jammy
    restart: always
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - JWT_SECRET=${JWT_SECRET}
      - DB_USERNAME=root
      - DB_PASSWORD=${DB_PASSWORD}
      - MAIL_ADDRESS=${MAIL_ADDRESS}
      - MAIL_AUTH_CODE=${MAIL_AUTH_CODE}
    volumes:
      - ./app.jar:/app.jar
    command: ["java", "-Xmx512m", "-jar", "/app.jar"]
    ports:
      - "8080:8080"
    depends_on:
      - db

  # 3. Caddy
  caddy:
    image: caddy:2
    restart: always
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
      - caddy_config:/config
    depends_on:
      - app

volumes:
  caddy_data:
  caddy_config:
```

**3. Start the services:**
```bash
docker compose up -d
```

### Local Dev

For local development, the application defaults to using `application-dev.properties`.

Create a `.env` file in your local environment with the identical structure as above (you can hardcode `DB_USERNAME` if preferred).

Run the Spring Boot Monitor Application.

Access the local client at `http://localhost:8080`.

## Developers
Developed by Yinwen Gong.

## Disclaimer
This project is an independent tool and is not affiliated with University of Wisconsin-Madison.