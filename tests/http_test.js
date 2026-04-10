import http from "k6/http";
import { check, sleep } from "k6";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

export const options = {
  stages: [
    { duration: "10s", target: 10 },
    { duration: "30s", target: 10 },
    { duration: "10s", target: 0 },
  ],
};

const AUTH_URL = "http://localhost:8082";
const DOC_URL = "http://localhost:8080";

export default function () {
  const email = `user_${uuidv4()}@test.com`;
  const password = "Test@1234";
  const name = `user_${uuidv4()}`;

  // 1. Register
  const registerRes = http.post(
    `${AUTH_URL}/auth/register`,
    JSON.stringify({
      name: name,
      email: email,
      password: password,
    }),
    { headers: { "Content-Type": "application/json" } },
  );

  check(registerRes, {
    "register 201": (r) => r.status === 201,
  });

  // 2. Login
  const loginRes = http.post(
    `${AUTH_URL}/auth/login`,
    JSON.stringify({
      email: email,
      password: password,
    }),
    { headers: { "Content-Type": "application/json" } },
  );

  check(loginRes, {
    "login 200": (r) => r.status === 200,
    "has accessToken": (r) => JSON.parse(r.body).accessToken !== undefined,
  });

  const token = JSON.parse(loginRes.body).accessToken;

  // 3. Create document via connection-manager (same as frontend docApi)
  const title = `Doc_${uuidv4()}`;
  const createRes = http.post(
    `${DOC_URL}/documents?title=${encodeURIComponent(title)}`,
    null,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    },
  );

  check(createRes, {
    "create doc 200": (r) => r.status === 200,
  });

  sleep(1);
}
