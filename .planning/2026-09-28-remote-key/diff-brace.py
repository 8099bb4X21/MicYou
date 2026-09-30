import io
import subprocess

diff = subprocess.run(
    ['git', 'diff', 'master', '--', 'tauri-app/src-tauri/src/tray.rs'],
    capture_output=True, text=True, encoding='utf-8', errors='replace',
    cwd=r'E:/OpenCode/test/micyou-fork',
).stdout or ''
plus_o = plus_c = minus_o = minus_c = 0
for line in diff.split('\n'):
    if line.startswith('+++') or line.startswith('---'):
        continue
    if line.startswith('+'):
        plus_o += line.count('{')
        plus_c += line.count('}')
    elif line.startswith('-'):
        minus_o += line.count('{')
        minus_c += line.count('}')
print('plus {', plus_o, '} ', plus_c)
print('minus {', minus_o, '} ', minus_c)
print('net:', (plus_o - plus_c) - (minus_o - minus_c))
