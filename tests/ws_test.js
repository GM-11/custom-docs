import http from "k6/http";
import ws from "k6/ws";
import { check, sleep } from "k6";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";
import { Counter } from "k6/metrics";
import { b64decode } from "k6/encoding";

export const options = {
  stages: [
    { duration: "10s", target: 5 },
    { duration: "30s", target: 5 },
    { duration: "10s", target: 0 },
  ],
};

const AUTH_URL = "http://localhost:8082";
const CM_URL = "http://localhost:8080"; // connection-manager
const DOCMGR_URL = "http://localhost:8081"; // docmanager
const WS_URL = "ws://localhost:8080";

const opsReceived = new Counter("ops_received");
const opsSent = new Counter("ops_sent");

function jwtSub(token) {
  const payload = token.split(".")[1];
  const decoded = b64decode(payload, "rawurl", "s");
  return JSON.parse(decoded).sub;
}

// One-time setup: one user creates the shared doc, returns hubId only
export function setup() {
  const email = `owner_${uuidv4()}@test.com`;
  const password = "Test@1234";
  const name = `owner_${uuidv4()}`;

  const registerRes = http.post(
    `${AUTH_URL}/auth/register`,
    JSON.stringify({ name, email, password }),
    { headers: { "Content-Type": "application/json" } },
  );
  check(registerRes, { "setup register 201": (r) => r.status === 201 });

  const loginRes = http.post(
    `${AUTH_URL}/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { "Content-Type": "application/json" } },
  );
  check(loginRes, { "setup login 200": (r) => r.status === 200 });

  const loginBody = JSON.parse(loginRes.body);
  const token = loginBody.accessToken;
  const ownerId = jwtSub(token);

  const createRes = http.post(
    `${CM_URL}/documents?title=ConcurrentTest_${uuidv4()}`,
    null,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  check(createRes, { "setup create doc 200": (r) => r.status === 200 });

  // Guard against empty/non-string bodies to avoid crashing setup().
  // Some handlers may return an empty body on error while still being parsed here.
  const rawBody = createRes.body;
  check(
    { rawBody },
    {
      "create doc response has body": (d) =>
        typeof d.rawBody === "string" && d.rawBody.length > 0,
    },
  );

  const hubId =
    typeof rawBody === "string" ? rawBody.replace(/"/g, "").trim() : "";

  check(
    { hubId },
    {
      "hubId parsed": (d) => typeof d.hubId === "string" && d.hubId.length > 0,
    },
  );

  return { hubId, ownerId };
}

export default function (data) {
  const { hubId, ownerId } = data;

  // Each VU registers + logs in as its own unique user
  const email = `vu_${uuidv4()}@test.com`;
  const password = "Test@1234";
  const name = `vu_${uuidv4()}`;

  const registerRes = http.post(
    `${AUTH_URL}/auth/register`,
    JSON.stringify({ name, email, password }),
    { headers: { "Content-Type": "application/json" } },
  );
  check(registerRes, { "vu register 201": (r) => r.status === 201 });

  const loginRes = http.post(
    `${AUTH_URL}/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { "Content-Type": "application/json" } },
  );
  check(loginRes, { "vu login 200": (r) => r.status === 200 });

  const body = JSON.parse(loginRes.body);
  const token = body.accessToken;
  const userId = jwtSub(token);

  // Grant this VU access to the shared doc
  const grantRes = http.put(
    `${DOCMGR_URL}/documents/access`,
    JSON.stringify({
      documentId: hubId,
      userEmail: email,
      ownerId: ownerId,
    }),
    { headers: { "Content-Type": "application/json" } },
  );
  check(grantRes, { "grant access 200": (r) => r.status === 200 });

  // Connect to the shared hub
  const url = `${WS_URL}/ws?token=${token}&hubId=${encodeURIComponent(hubId)}`;

  let version = 0;
  let position = 0;
  let received = 0;

  const res = ws.connect(url, {}, function (socket) {
    socket.on("open", () => {
      // Send 10 insert ops, one every 200ms
      let opsToSend = 10;
      let sent = 0;

      const interval = socket.setInterval(() => {
        if (sent >= opsToSend) {
          // k6 ws sockets don't implement clearInterval(); setInterval returns an id
          // and you cancel it via the global clearInterval().
          clearInterval(interval);
          // Wait a bit for in-flight broadcasts then close
          socket.setTimeout(() => socket.close(), 1000);
          return;
        }

        const op = {
          type: 0, // OT_INSERT
          position: position,
          version: version,
          clientId: userId,
          data: "a",
        };

        socket.send(JSON.stringify(op));
        opsSent.add(1);
        position += 1;
        version += 1;
        sent += 1;
      }, 200);
    });

    socket.on("message", (_msg) => {
      received += 1;
      opsReceived.add(1);
    });

    socket.on("error", (e) => {
      console.error(`[VU error] ${e}`);
    });

    socket.on("close", () => {
      // session done
    });
  });

  check(res, { "ws connected 101": (r) => r && r.status === 101 });

  // ops_received should be > 0 for any VU that isn't the only one connected
  check(
    { received },
    {
      "received broadcasts from other clients": (d) => d.received > 0,
    },
  );

  sleep(1);
}
