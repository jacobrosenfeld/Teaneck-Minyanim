# Deployment Setup

This document describes the CI/CD pipeline for Teaneck Minyanim. Pushes to `dev` auto-deploy to the dev environment; merges to `main` auto-deploy to production.

## Architecture

- **Prod**: `teaneckminyanim.com` → Caddy → `localhost:8080` → systemd `teaneck-prod`
- **Dev**: `dev.teaneckminyanim.com` → Caddy → `localhost:8081` → systemd `teaneck-dev`
- **CI/CD**: GitHub Actions builds the JAR and deploys via SSH on every push
- **Databases**: `teaneck-minyanim` (prod), `teaneck-minyanim-dev` (dev)

---

## Server Setup

### 1. Deploy user

A dedicated `deploy` user runs the app with minimal privileges.

```bash
sudo useradd --system -m -s /bin/bash deploy
sudo mkdir -p /home/deploy/.ssh
# paste the GitHub Actions public key into authorized_keys
sudo nano /home/deploy/.ssh/authorized_keys
sudo chown -R deploy: /home/deploy/.ssh
sudo chmod 700 /home/deploy/.ssh
sudo chmod 600 /home/deploy/.ssh/authorized_keys

sudo mkdir -p /opt/teaneck/prod /opt/teaneck/dev
sudo chown -R deploy: /opt/teaneck
```

### 2. Sudoers rule

Allows the deploy user to restart services without a password:

```bash
sudo visudo -f /etc/sudoers.d/deploy-teaneck
```

```
deploy ALL=(ALL) NOPASSWD: /bin/systemctl restart teaneck-prod, /bin/systemctl restart teaneck-dev
```

### 3. Environment files

Credentials and port config are kept in env files on the server — never in the repo.

```bash
sudo nano /opt/teaneck/prod/env
```

```bash
SPRING_DATASOURCE_URL=jdbc:mariadb://localhost:3306/teaneck-minyanim
SPRING_DATASOURCE_USERNAME=user
SPRING_DATASOURCE_PASSWORD=yourpassword
SERVER_PORT=8080
```

```bash
sudo nano /opt/teaneck/dev/env
```

```bash
SPRING_DATASOURCE_URL=jdbc:mariadb://localhost:3306/teaneck-minyanim-dev
SPRING_DATASOURCE_USERNAME=user
SPRING_DATASOURCE_PASSWORD=yourpassword
SERVER_PORT=8081
```

```bash
sudo chmod 600 /opt/teaneck/prod/env /opt/teaneck/dev/env
```

### 4. Create dev database

```bash
mysql -u user -p -e "CREATE DATABASE \`teaneck-minyanim-dev\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### 5. systemd service files

**`/etc/systemd/system/teaneck-prod.service`**

```ini
[Unit]
Description=Teaneck Minyanim (prod)
After=network.target mariadb.service

[Service]
User=opc
WorkingDirectory=/opt/teaneck/prod
ExecStart=/usr/bin/java -jar /opt/teaneck/prod/app.jar
EnvironmentFile=/opt/teaneck/prod/env
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

**`/etc/systemd/system/teaneck-dev.service`**

```ini
[Unit]
Description=Teaneck Minyanim (dev)
After=network.target mariadb.service

[Service]
User=opc
WorkingDirectory=/opt/teaneck/dev
ExecStart=/usr/bin/java -jar /opt/teaneck/dev/app.jar
EnvironmentFile=/opt/teaneck/dev/env
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable teaneck-prod teaneck-dev
```

Note: the JAR files don't exist until the first GitHub Actions deploy runs, so don't start the services manually until after that.

### 6. Caddy config

```caddy
teaneckminyanim.com {
    reverse_proxy localhost:8080
}

dev.teaneckminyanim.com {
    reverse_proxy localhost:8081

    # Optional: protect dev from public access
    basicauth {
        yourname $2a$14$...  # generate with: caddy hash-password
    }
}
```

Add a DNS A record for `dev.teaneckminyanim.com` pointing at the server IP. Caddy handles TLS automatically.

---

## GitHub Setup

### SSH deploy key

A dedicated SSH key pair is used by GitHub Actions to deploy. It was generated once and should not be regenerated unless rotating credentials.

1. Generate on your local machine (not the server):
   ```bash
   ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/teaneck_deploy
   ```

2. Add the **public key** to `/home/deploy/.ssh/authorized_keys` on the server.

3. Add the **private key** as a GitHub Actions secret:
   - Repo → Settings → Secrets and variables → Actions
   - Secret name: `DEPLOY_KEY` — value: contents of `~/.ssh/teaneck_deploy`
   - Secret name: `SERVER_HOST` — value: server IP address

### GitHub Actions workflows

Two workflow files live in `.github/workflows/`:

| File | Trigger | Deploys to |
|---|---|---|
| `deploy-prod.yml` | push to `main` | `/opt/teaneck/prod`, port 8080 |
| `deploy-dev.yml` | push to `dev` | `/opt/teaneck/dev`, port 8081 |

Each workflow: checks out the repo → builds the JAR with Maven → SCPs it to the server → restarts the systemd service → health-checks `/actuator/health`.

---

## Branching workflow

```bash
# Create and push the dev branch (one-time setup)
git checkout -b dev
git push -u origin dev
```

Day-to-day:
- Do all work on `dev`, push to trigger a dev deploy
- When ready to ship, open a PR from `dev` → `main` and merge it
- Prod deploys automatically on merge

---

## Useful commands

```bash
# View live logs
journalctl -u teaneck-prod -f
journalctl -u teaneck-dev -f

# Check service status
sudo systemctl status teaneck-prod
sudo systemctl status teaneck-dev

# Manually restart
sudo systemctl restart teaneck-prod
sudo systemctl restart teaneck-dev

# Find a running java process
jps -v

# Kill a process by PID
kill <pid>
```

---

## Rollback

To redeploy a previous version, go to the repo's **Actions** tab on GitHub, find the workflow run for the commit you want, and click **Re-run jobs**. This rebuilds and redeploys that exact commit.
