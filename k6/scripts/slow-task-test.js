import fetch from 'node-fetch';
const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';

const callSync = async (itr) => {
    const t0 = Date.now();
    console.log(`SYNC  ${itr} start ${new Date().toLocaleTimeString()}`);
    const res = await fetch(`${BASE_URL}/sync`);
    const data = await res.text();
    console.log(`SYNC  ${itr} done  "${data}" (${Date.now() - t0}ms)`);
};

const callAsync = async (itr) => {
    const t0 = Date.now();
    console.log(`ASYNC ${itr} start ${new Date().toLocaleTimeString()}`);
    const res = await fetch(`${BASE_URL}/async`);
    const data = await res.text();
    console.log(`ASYNC ${itr} done  "${data}" (${Date.now() - t0}ms)`);
};

const run = async () => {
    console.log(`--- SYNC sequential ---`);
    const s0 = Date.now();
    for (let i = 0; i < 10; i++) await callSync(i);   // await = one after another
    console.log(`10 sync total: ${Date.now() - s0}ms\n`);

    console.log(`--- ASYNC sequential ---`);
    const a0 = Date.now();
    for (let i = 0; i < 10; i++) await callAsync(i);
    console.log(`10 async total: ${Date.now() - a0}ms`);
};

run();