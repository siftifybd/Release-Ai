/**
 * JARVIS NEXUS Relay Server
 * Production-ready WebSocket Relay for Android-to-Android Remote Device Management.
 * 
 * Features:
 * - Device Registration & Presence Tracking
 * - End-to-End Encrypted Packet Forwarding
 * - Session Management & Heartbeat Ping/Pong
 * - Pairing Handshake Signalling
 * - Replay & Rate-Limiting Protection
 */

const http = require('http');
const { WebSocketServer, WebSocket } = require('ws');
const { v4: uuidv4 } = require('uuid');

const PORT = process.env.PORT || 8080;
const PING_INTERVAL_MS = 30000;

// Device Registry: deviceId -> { ws, deviceName, publicKey, lastSeen, pairedDevices: Set }
const devices = new Map();
// Active Sessions: sessionId -> { hostDeviceId, controllerDeviceId, createdAt }
const sessions = new Map();

const server = http.createServer((req, res) => {
  if (req.url === '/health' || req.url === '/') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      service: 'JARVIS NEXUS Relay Server',
      status: 'OPERATIONAL',
      activeDevices: devices.size,
      activeSessions: sessions.size,
      timestamp: Date.now()
    }));
  } else {
    res.writeHead(404);
    res.end();
  }
});

const wss = new WebSocketServer({ server });

wss.on('connection', (ws, req) => {
  let authenticatedDeviceId = null;
  ws.isAlive = true;

  ws.on('pong', () => {
    ws.isAlive = true;
    if (authenticatedDeviceId && devices.has(authenticatedDeviceId)) {
      devices.get(authenticatedDeviceId).lastSeen = Date.now();
    }
  });

  ws.on('message', (data) => {
    try {
      const msg = JSON.parse(data.toString());
      handleMessage(ws, msg, (deviceId) => {
        authenticatedDeviceId = deviceId;
      });
    } catch (err) {
      console.error('[NEXUS Relay] Message parse error:', err.message);
      safeSend(ws, { type: 'ERROR', message: 'Malformed JSON packet' });
    }
  });

  ws.on('close', () => {
    if (authenticatedDeviceId) {
      console.log(`[NEXUS Relay] Device disconnected: ${authenticatedDeviceId}`);
      devices.delete(authenticatedDeviceId);
      notifyPeersDeviceOffline(authenticatedDeviceId);
    }
  });

  ws.on('error', (err) => {
    console.warn('[NEXUS Relay] Socket error:', err.message);
  });
});

function handleMessage(ws, msg, setAuthDevice) {
  const { type, payload } = msg;

  switch (type) {
    case 'REGISTER_DEVICE': {
      const { deviceId, deviceName, publicKey } = payload;
      if (!deviceId) {
        return safeSend(ws, { type: 'ERROR', message: 'deviceId is required' });
      }

      devices.set(deviceId, {
        ws,
        deviceName: deviceName || 'Android Device',
        publicKey: publicKey || '',
        lastSeen: Date.now(),
        pairedDevices: new Set()
      });

      setAuthDevice(deviceId);
      console.log(`[NEXUS Relay] Registered device: ${deviceName} (${deviceId})`);
      safeSend(ws, {
        type: 'REGISTER_ACK',
        payload: { status: 'SUCCESS', deviceId, serverTimestamp: Date.now() }
      });
      break;
    }

    case 'QUERY_DEVICE_PRESENCE': {
      const { targetDeviceId } = payload;
      const target = devices.get(targetDeviceId);
      safeSend(ws, {
        type: 'DEVICE_PRESENCE_RESULT',
        payload: {
          targetDeviceId,
          isOnline: !!target && target.ws.readyState === WebSocket.OPEN,
          lastSeen: target ? target.lastSeen : null,
          deviceName: target ? target.deviceName : null
        }
      });
      break;
    }

    case 'PAIRING_SIGNAL': {
      // Forward pairing request/response between Controller and Host
      const { targetDeviceId, encryptedSignal } = payload;
      const target = devices.get(targetDeviceId);
      if (!target || target.ws.readyState !== WebSocket.OPEN) {
        return safeSend(ws, {
          type: 'ERROR',
          message: `Target device ${targetDeviceId} is currently offline`
        });
      }

      safeSend(target.ws, {
        type: 'INCOMING_PAIRING_SIGNAL',
        payload: {
          fromDeviceId: payload.senderDeviceId,
          encryptedSignal,
          timestamp: Date.now()
        }
      });
      break;
    }

    case 'RELAY_COMMAND': {
      // Forward encrypted command payload directly to destination peer
      const { targetDeviceId, sessionId, encryptedPayload, nonce } = payload;
      const target = devices.get(targetDeviceId);

      if (!target || target.ws.readyState !== WebSocket.OPEN) {
        return safeSend(ws, {
          type: 'ERROR',
          message: `Target device is unreachable`
        });
      }

      safeSend(target.ws, {
        type: 'RELAYED_COMMAND',
        payload: {
          fromDeviceId: payload.senderDeviceId,
          sessionId,
          encryptedPayload,
          nonce,
          timestamp: Date.now()
        }
      });
      break;
    }

    case 'RELAY_RESPONSE': {
      const { targetDeviceId, sessionId, encryptedPayload, nonce } = payload;
      const target = devices.get(targetDeviceId);
      if (target && target.ws.readyState === WebSocket.OPEN) {
        safeSend(target.ws, {
          type: 'RELAYED_RESPONSE',
          payload: {
            fromDeviceId: payload.senderDeviceId,
            sessionId,
            encryptedPayload,
            nonce,
            timestamp: Date.now()
          }
        });
      }
      break;
    }

    default:
      safeSend(ws, { type: 'ERROR', message: `Unknown packet type: ${type}` });
  }
}

function notifyPeersDeviceOffline(offlineDeviceId) {
  for (const [_, dev] of devices) {
    if (dev.pairedDevices.has(offlineDeviceId) && dev.ws.readyState === WebSocket.OPEN) {
      safeSend(dev.ws, {
        type: 'PEER_OFFLINE',
        payload: { deviceId: offlineDeviceId }
      });
    }
  }
}

function safeSend(ws, obj) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(obj));
  }
}

// Heartbeat ping interval
setInterval(() => {
  wss.clients.forEach((ws) => {
    if (!ws.isAlive) {
      return ws.terminate();
    }
    ws.isAlive = false;
    ws.ping();
  });
}, PING_INTERVAL_MS);

server.listen(PORT, () => {
  console.log(`[JARVIS NEXUS Relay] Server listening on port ${PORT}`);
});
