// cache-benchmark.js
// Benchmark /api/redirect with and without cache enabled.

import fetch from 'node-fetch';

const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';
const API_KEY = "bulk-key-ac3faeb8-fdba-4945-bb11-67d79d2e0fd0";
const ORIGINAL_URL = 'https://example.com/cache-benchmark-test';
const ITERATIONS = 100;

// ─────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────

async function createShortUrl() {
    const response = await fetch(`${BASE_URL}/api/shorten`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-API-Key': API_KEY,
        },
        body: JSON.stringify({ originalUrl: ORIGINAL_URL }),
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(`Failed to create short URL: ${response.status} ${text}`);
    }

    const data = await response.json();
    return data.shortCode;
}

async function callRedirect(shortCode) {
    // Disable auto-following redirects so we just measure the API response
    const response = await fetch(
        `${BASE_URL}/api/redirect?shortCode=${shortCode}`,
        { redirect: 'manual' }
    );

    if (response.status !== 302) {
        throw new Error(`Unexpected status ${response.status} for shortCode=${shortCode}`);
    }

    return response;
}

async function runBenchmark(label, shortCode) {
    console.log(`\n━━━ ${label} ━━━`);
    console.log(`Running ${ITERATIONS} requests against /api/redirect...`);

    const durations = [];
    const startAll = Date.now();

    for (let i = 0; i < ITERATIONS; i++) {
        const start = performance.now();
        await callRedirect(shortCode);
        const end = performance.now();
        durations.push(end - start);
    }

    const totalMs = Date.now() - startAll;
    const min = Math.min(...durations);
    const max = Math.max(...durations);
    const avg = durations.reduce((a, b) => a + b, 0) / durations.length;
    const p50 = percentile(durations, 50);
    const p95 = percentile(durations, 95);
    const p99 = percentile(durations, 99);

    console.log(`Total time:      ${totalMs} ms`);
    console.log(`Per request avg: ${avg.toFixed(2)} ms`);
    console.log(`Per request p50: ${p50.toFixed(2)} ms`);
    console.log(`Per request p95: ${p95.toFixed(2)} ms`);
    console.log(`Per request p99: ${p99.toFixed(2)} ms`);
    console.log(`Per request min: ${min.toFixed(2)} ms`);
    console.log(`Per request max: ${max.toFixed(2)} ms`);

    return { totalMs, avg, p50, p95, p99, min, max, durations };
}

function percentile(arr, p) {
    const sorted = [...arr].sort((a, b) => a - b);
    const idx = Math.ceil((p / 100) * sorted.length) - 1;
    return sorted[Math.max(0, idx)];
}

// ─────────────────────────────────────────────────────────────────
// Main
// ─────────────────────────────────────────────────────────────────

async function main() {
    console.log('Cache benchmark for URL Shortener');
    console.log(`Target: ${BASE_URL}`);
    console.log(`Iterations per run: ${ITERATIONS}`);

    // Step 1: create a short URL to use for the benchmark
    console.log('\nCreating a test short URL...');
    const shortCode = await createShortUrl();
    console.log(`Short code: ${shortCode}`);

    // Step 2: warm-up call (avoids JIT compilation skewing the first request)
    console.log('\nWarming up...');
    await callRedirect(shortCode);

    // Step 3: run benchmark
    // (Run this BEFORE enabling cache in your app to measure baseline)
    const results = await runBenchmark('Benchmark Results', shortCode);

    // Step 4: estimated cache stats
    // Without cache: 100 DB calls
    // With cache:    1 DB call (first) + 99 cache hits
    console.log('\n━━━ Expected Cache Stats ━━━');
    console.log(`Without cache: 100 DB queries, 0 cache hits (hit ratio: 0%)`);
    console.log(`With cache:    1 DB query, 99 cache hits (hit ratio: 99%)`);

    return results;
}

main().catch(err => {
    console.error('Benchmark failed:', err.message);
    process.exit(1);
});