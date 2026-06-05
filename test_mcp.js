const { spawn } = require('child_process');
const child = spawn('agentmemory.cmd', ['mcp'], { env: process.env, shell: true });

child.stdout.on('data', (data) => {
    const lines = data.toString().split('\n');
    lines.forEach(line => {
        if (!line.trim()) return;
        try {
            const msg = JSON.parse(line);
            if (msg.id === 1) {
                console.log('Tools:', msg.result.tools.map(t => t.name).join(', '));
                process.exit(0);
            }
        } catch(e) {}
    });
});

child.stderr.on('data', (data) => {
    console.error(data.toString());
});

const req = JSON.stringify({
    jsonrpc: '2.0',
    id: 1,
    method: 'tools/list'
}) + '\n';

child.stdin.write(req);
