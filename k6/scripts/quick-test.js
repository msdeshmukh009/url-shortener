import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const shortenTrend  = new Trend('shorten_duration',  true);
const redirectTrend = new Trend('redirect_duration', true);

export const options = {
  vus: 10,         
  iterations: 10,  
  summaryTrendStats: ['min', 'avg', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const shortenRes = http.post(
    'http://localhost:8080/api/shorten',
    JSON.stringify({ originalUrl: `https://example.com/page/${__VU}-${__ITER}` }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  shortenTrend.add(shortenRes.timings.duration);
  check(shortenRes, { '[shorten] status 201': (r) => r.status === 201 });

  if (shortenRes.status !== 201) return;

  const shortCode = JSON.parse(shortenRes.body).shortCode;
  const redirectRes = http.get(
    `http://localhost:8080/api/redirect?shortCode=${shortCode}`,
    { redirects: 0 }
  );

  redirectTrend.add(redirectRes.timings.duration);
  check(redirectRes, { '[redirect] status 302': (r) => r.status === 302 });
}