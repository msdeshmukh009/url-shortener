const { execSync } = require('child_process');
const fs       = require('fs');
const path     = require('path');
const readline = require('readline');

const ROOT        = 'C:\\Users\\deshm\\Documents\\vyson\\url-shortener\\k6';
const SCRIPT_PATH = path.join(ROOT, 'scripts', 'stress-test.js');
const JSON_DIR    = path.join(ROOT, 'results', 'json');
const HTML_DIR    = path.join(ROOT, 'results', 'html');

fs.mkdirSync(JSON_DIR, { recursive: true });
fs.mkdirSync(HTML_DIR, { recursive: true });

const now = new Date();
const ts = now.toISOString()
  .replace(/T/, '_')
  .replace(/:/g, '-')
  .replace(/\..+$/, '');

const jsonFile = path.join(JSON_DIR, `stress-${ts}.json`);
const htmlFile = path.join(HTML_DIR, `stress-${ts}.html`);

console.log(`\n▶  Running k6 stress test...`);
console.log(`   JSON output → ${jsonFile}\n`);

const k6Cmd = [`k6 run`, `"${SCRIPT_PATH}"`, `--out json="${jsonFile}"`].join(' ');

try {
  execSync(k6Cmd, { stdio: 'inherit' });
} catch (e) {
  console.log('\n⚠   k6 exited with non-zero status — continuing to generate report.\n');
}

console.log(`\n▶  Generating HTML report...`);

function percentile(arr, p) {
  if (arr.length === 0) return 0;
  const sorted = [...arr].sort((a, b) => a - b);
  const index = Math.ceil((p / 100) * sorted.length) - 1;
  return sorted[Math.max(0, index)];
}

async function parseJsonStream(filePath) {
  const buckets = { _start: null };
  const failures = {};

  const stream = fs.createReadStream(filePath, { encoding: 'utf-8' });
  const rl = readline.createInterface({ input: stream, crlfDelay: Infinity });

  for await (const line of rl) {
    if (!line) continue;
    let event;
    try { event = JSON.parse(line); } catch { continue; }
    if (event.type !== 'Point') continue;

    const metric = event.metric;
    const time = new Date(event.data.time).getTime();
    if (!buckets._start) buckets._start = time;
    const bucketSec = Math.floor((time - buckets._start) / 10000) * 10;

    if (metric === 'shorten_duration' || metric === 'redirect_duration') {
      if (!buckets[metric]) buckets[metric] = {};
      if (!buckets[metric][bucketSec]) buckets[metric][bucketSec] = [];
      buckets[metric][bucketSec].push(event.data.value);
    }

    if (metric === 'http_req_failed') {
      if (!failures[bucketSec]) failures[bucketSec] = { total: 0, failed: 0 };
      failures[bucketSec].total += 1;
      if (event.data.value === 1) failures[bucketSec].failed += 1;
    }
  }

  buckets._failures = failures;
  return buckets;
}

(async () => {
  const buckets = await parseJsonStream(jsonFile);

  function buildSeries(metric) {
    const windows = buckets[metric] || {};
    const labels = Object.keys(windows).map(Number).sort((a, b) => a - b);
    return {
      labels: labels.map(s => `${s}s`),
      p50:    labels.map(s => +percentile(windows[s], 50).toFixed(0)),
      p90:    labels.map(s => +percentile(windows[s], 90).toFixed(0)),
      p95:    labels.map(s => +percentile(windows[s], 95).toFixed(0)),
      p99:    labels.map(s => +percentile(windows[s], 99).toFixed(0)),
    };
  }

  function buildFailureSeries(failures) {
    const labels = Object.keys(failures).map(Number).sort((a, b) => a - b);
    return {
      labels: labels.map(s => `${s}s`),
      rate:   labels.map(s => {
        const f = failures[s];
        return f.total === 0 ? 0 : +((f.failed / f.total) * 100).toFixed(4);
      }),
      total:  labels.map(s => failures[s].total),
      failed: labels.map(s => failures[s].failed),
    };
  }

  const shorten       = buildSeries('shorten_duration');
  const redirect      = buildSeries('redirect_duration');
  const failureSeries = buildFailureSeries(buckets._failures);

  const stageLabels = ['50', '100', '200', '500', '1000', '1500', '2000', '2500', 'Cooldown'];

  const html = `<!DOCTYPE html>
<html>
<head>
  <title>k6 Load Test — ${ts}</title>
  <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
  <style>
    body { font-family: sans-serif; padding: 24px; background: #f5f5f5; color: #333; }
    h1, h2 { margin: 0 0 12px; }
    .chart-box { background: white; border-radius: 8px; padding: 24px; margin-bottom: 24px; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
    table { border-collapse: collapse; background: white; margin-top: 12px; width: 100%; }
    th, td { padding: 8px 14px; border: 1px solid #ddd; text-align: right; font-size: 13px; }
    th { background: #eee; }
    .meta { color: #666; font-size: 13px; margin-bottom: 24px; }
    .stages { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
    .stage { background: #1976d2; color: white; padding: 4px 10px; border-radius: 4px; font-size: 12px; }
  </style>
</head>
<body>

<h1>Load Test Report</h1>
<div class="meta">Generated: ${now.toLocaleString()} &nbsp;·&nbsp; File: stress-${ts}</div>

<div class="stages">
  ${stageLabels.map(s => `<span class="stage">${s} VUs</span>`).join('')}
</div>

<div class="chart-box">
  <h2>POST /api/shorten — Latency Over Time</h2>
  <canvas id="shortenChart" height="100"></canvas>
  <table>
    <tr><th>Time</th>${shorten.labels.map(l => `<th>${l}</th>`).join('')}</tr>
    <tr><th>P50</th>${shorten.p50.map(v => `<td>${v}</td>`).join('')}</tr>
    <tr><th>P90</th>${shorten.p90.map(v => `<td>${v}</td>`).join('')}</tr>
    <tr><th>P95</th>${shorten.p95.map(v => `<td>${v}</td>`).join('')}</tr>
    <tr><th>P99</th>${shorten.p99.map(v => `<td>${v}</td>`).join('')}</tr>
  </table>
</div>

<div class="chart-box">
  <h2>GET /api/redirect — Latency Over Time</h2>
  <canvas id="redirectChart" height="100"></canvas>
  <table>
    <tr><th>Time</th>${redirect.labels.map(l => `<th>${l}</th>`).join('')}</tr>
    <tr><th>P50</th>${redirect.p50.map(v => `<td>${v}</td>`).join('')}</tr>
    <tr><th>P90</th>${redirect.p90.map(v => `<td>${v}</td>`).join('')}</tr>
    <tr><th>P95</th>${redirect.p95.map(v => `<td>${v}</td>`).join('')}</tr>
    <tr><th>P99</th>${redirect.p99.map(v => `<td>${v}</td>`).join('')}</tr>
  </table>
</div>

<div class="chart-box">
  <h2>Failed Requests Over Time</h2>
  <canvas id="failureChart" height="100"></canvas>
  <table>
    <tr><th>Time</th>${failureSeries.labels.map(l => `<th>${l}</th>`).join('')}</tr>
    <tr><th>Total</th>${failureSeries.total.map(v => `<td>${v}</td>`).join('')}</tr>
    <tr><th>Failed</th>${failureSeries.failed.map(v => `<td>${v}</td>`).join('')}</tr>
    <tr><th>Rate (%)</th>${failureSeries.rate.map(v => `<td>${v}%</td>`).join('')}</tr>
  </table>
</div>

<script>
const commonOptions = {
  responsive: true,
  plugins: { legend: { position: 'top' } },
  scales: {
    x: { title: { display: true, text: 'Time (seconds)' } },
    y: { title: { display: true, text: 'Latency (ms)' }, beginAtZero: true }
  }
};

new Chart(document.getElementById('shortenChart'), {
  type: 'line',
  data: {
    labels: ${JSON.stringify(shorten.labels)},
    datasets: [
      { label: 'P50', data: ${JSON.stringify(shorten.p50)}, borderColor: '#4CAF50', tension: 0.3, fill: false },
      { label: 'P90', data: ${JSON.stringify(shorten.p90)}, borderColor: '#2196F3', tension: 0.3, fill: false },
      { label: 'P95', data: ${JSON.stringify(shorten.p95)}, borderColor: '#FF9800', tension: 0.3, fill: false },
      { label: 'P99', data: ${JSON.stringify(shorten.p99)}, borderColor: '#F44336', tension: 0.3, fill: false },
    ]
  },
  options: commonOptions
});

new Chart(document.getElementById('redirectChart'), {
  type: 'line',
  data: {
    labels: ${JSON.stringify(redirect.labels)},
    datasets: [
      { label: 'P50', data: ${JSON.stringify(redirect.p50)}, borderColor: '#4CAF50', tension: 0.3, fill: false },
      { label: 'P90', data: ${JSON.stringify(redirect.p90)}, borderColor: '#2196F3', tension: 0.3, fill: false },
      { label: 'P95', data: ${JSON.stringify(redirect.p95)}, borderColor: '#FF9800', tension: 0.3, fill: false },
      { label: 'P99', data: ${JSON.stringify(redirect.p99)}, borderColor: '#F44336', tension: 0.3, fill: false },
    ]
  },
  options: commonOptions
});

new Chart(document.getElementById('failureChart'), {
  type: 'line',
  data: {
    labels: ${JSON.stringify(failureSeries.labels)},
    datasets: [
      {
        label: 'Failure Rate (%)',
        data: ${JSON.stringify(failureSeries.rate)},
        borderColor: '#F44336',
        backgroundColor: 'rgba(244, 67, 54, 0.15)',
        tension: 0.3,
        fill: true,
        yAxisID: 'y',
      },
      {
        label: 'Failed Request Count',
        data: ${JSON.stringify(failureSeries.failed)},
        borderColor: '#9C27B0',
        backgroundColor: 'rgba(156, 39, 176, 0.1)',
        tension: 0.3,
        fill: false,
        yAxisID: 'y1',
      },
    ]
  },
  options: {
    responsive: true,
    plugins: { legend: { position: 'top' } },
    scales: {
      x: { title: { display: true, text: 'Time (seconds)' } },
      y: {
        type: 'linear',
        position: 'left',
        title: { display: true, text: 'Failure Rate (%)' },
        beginAtZero: true,
        ticks: { callback: (v) => v.toFixed(3) + '%' }
      },
      y1: {
        type: 'linear',
        position: 'right',
        title: { display: true, text: 'Failed Count' },
        beginAtZero: true,
        grid: { drawOnChartArea: false },
      }
    }
  }
});
</script>

</body>
</html>`;

  fs.writeFileSync(htmlFile, html);
  console.log(`   HTML report  → ${htmlFile}\n`);

  console.log(`▶  Opening report in browser...\n`);
  execSync(`start "" "${htmlFile}"`, { shell: true });
})();