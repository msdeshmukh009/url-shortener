import http from "k6/http";
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";

const shortenTrend = new Trend("shorten_duration", true);
const redirectTrend = new Trend("redirect_duration", true);

export const options = {
  summaryTrendStats: ["min", "avg", "p(50)", "p(90)", "p(95)", "p(99)", "max"],

  // hold each load level for 30s — 30s is enough for percentiles to stabilize
  // skip ramp stages — go straight to target since we want fixed load at each level
  systemTags: ["status", "method", "name"],
  stages: [
    { duration: "10s", target: 50 }, // ramp to 50
    { duration: "30s", target: 50 }, // ← hold at 50 (measure here)
    { duration: "10s", target: 100 }, // ramp to 100
    { duration: "30s", target: 100 }, // ← hold at 100 (measure here)
    { duration: "10s", target: 200 }, // ramp to 200
    { duration: "30s", target: 200 }, // ← hold at 200
    { duration: "10s", target: 500 }, // ramp to 500
    { duration: "30s", target: 500 }, // ← hold at 500
    { duration: "10s", target: 1000 }, // ramp to 1000
    { duration: "30s", target: 1000 }, // ← hold at 1000
    { duration: "10s", target: 1500 },
    { duration: "30s", target: 1500 },
    { duration: "10s", target: 2000 },
    { duration: "30s", target: 2000 },
    { duration: "10s", target: 2500 },
    { duration: "30s", target: 2500 },
    { duration: "10s", target: 3000 },
    { duration: "30s", target: 3000 },
    { duration: "10s", target: 3500 },
    { duration: "30s", target: 3500 },
    { duration: "10s", target: 4000 },
    { duration: "30s", target: 4000 },
    { duration: "10s", target: 0 }, // cool down
  ],
  thresholds: {
    shorten_duration: ["p(99)<5000"],
    redirect_duration: ["p(99)<5000"],
    http_req_failed: ["rate<0.10"],
  },
};

export default function () {
  const shortenRes = http.post(
    "http://localhost:8080/api/shorten",
    JSON.stringify({ originalUrl: "https://example.com" }),
    {
      headers: { "Content-Type": "application/json" },
      timeout: "10s",
    },
  );

  shortenTrend.add(shortenRes.timings.duration);
  check(shortenRes, { "[shorten] status 201": r => r.status === 201 });

  if (shortenRes.status !== 201) return;

  const shortCode = JSON.parse(shortenRes.body).shortCode;

  const redirectRes = http.get(`http://localhost:8080/api/redirect?shortCode=${shortCode}`, {
    redirects: 0,
    timeout: "10s",
  });

  redirectTrend.add(redirectRes.timings.duration);
  check(redirectRes, { "[redirect] status 302": r => r.status === 302 });
}
