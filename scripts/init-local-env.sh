#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
import base64,os,secrets
from pathlib import Path
p=Path('.env')
text=p.read_text() if p.exists() else ''
if not any(line.startswith('CURSOR_SECRET_BASE64=') and line.split('=',1)[1].strip() for line in text.splitlines()):
    text='\n'.join(line for line in text.splitlines() if not line.startswith('CURSOR_SECRET_BASE64='))
    with p.open('w') as f:
        os.chmod(p,0o600)
        f.write(text.rstrip()+'\nCURSOR_SECRET_BASE64='+base64.b64encode(secrets.token_bytes(32)).decode()+'\n')
    print('Created a local shared cursor key in ignored .env')
else:
    print('Existing local cursor key retained')
PY
