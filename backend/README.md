# JARVIS NEXUS — WebSocket Relay Server

## 1. Overview
The **JARVIS NEXUS Relay Server** is a lightweight, high-performance Node.js WebSocket relay enabling end-to-end encrypted remote control between Android devices over the Internet without requiring manual NAT configuration or public port forwarding.

### Security Guarantees
- **Blind Routing:** The relay routes encrypted ciphertext between authenticated `deviceId` pairs. Because payload encryption occurs on the Android devices via AES-256-GCM / ECDH session keys, the relay server never holds plaintext keys.
- **Heartbeat & Presence:** PING/PONG keep-alives monitor connection states and automatically broadcast peer drop notifications.
- **Mutual Authentication:** Devices authenticate with their public key signatures during `REGISTER_DEVICE`.

---

## 2. Quick Start (Local / Self-Hosted)

### Prerequisites
- Node.js 18+ LTS
- npm

### Installation & Execution
```bash
cd backend
npm install
node server.js
```

The server binds to port `8080` by default (or the value of `process.env.PORT`).

---

## 3. Docker Deployment

### `Dockerfile`
```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
EXPOSE 8080
CMD ["node", "server.js"]
```

### Build & Run
```bash
docker build -t jarvis-nexus-relay .
docker run -d -p 8080:8080 --name nexus-relay --restart always jarvis-nexus-relay
```

---

## 4. Production TLS / WSS Setup (Nginx Reverse Proxy)

For Internet Mode in the Android application, connections must use secure WebSockets (`wss://`):

```nginx
server {
    server_name nexus.yourdomain.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 86400s;
        proxy_send_timeout 86400s;
    }

    listen 443 ssl http2;
    ssl_certificate /etc/letsencrypt/live/nexus.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/nexus.yourdomain.com/privkey.pem;
}
```

In the Android app under **JARVIS NEXUS Settings > Internet Relay Server**, enter:
`wss://nexus.yourdomain.com`
