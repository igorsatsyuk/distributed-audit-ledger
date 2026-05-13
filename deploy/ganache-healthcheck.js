const http = require('http');

const payload = JSON.stringify({
  jsonrpc: '2.0',
  method: 'eth_blockNumber',
  params: [],
  id: 1,
});

const req = http.request(
  {
    host: 'localhost',
    port: 8545,
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(payload),
    },
    timeout: 5000,
  },
  (res) => {
    let body = '';
    res.on('data', (chunk) => {
      body += chunk;
    });
    res.on('end', () => {
      if (res.statusCode !== 200) {
        process.exit(1);
      }

      try {
        const parsed = JSON.parse(body);
        if (typeof parsed.result === 'string') {
          process.exit(0);
        }
      } catch (e) {
        // Ignore and fall through to non-zero exit.
      }

      process.exit(1);
    });
  }
);

req.on('timeout', () => {
  req.destroy();
  process.exit(1);
});

req.on('error', () => {
  process.exit(1);
});

req.write(payload);
req.end();

