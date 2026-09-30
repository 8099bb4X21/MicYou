import io
import glob

files = glob.glob(r'E:/OpenCode/test/micyou-fork/tauri-app/src-tauri/src/remote_key.rs') + \
    glob.glob(r'E:/OpenCode/test/micyou-fork/tauri-app/src-tauri/src/tray.rs') + \
    glob.glob(r'E:/OpenCode/test/micyou-fork/tauri-app/src-tauri/src/commands/system.rs') + \
    glob.glob(r'E:/OpenCode/test/micyou-fork/tauri-app/src-tauri/src/lib.rs')
for f in files:
    with io.open(f, 'r', encoding='utf-8') as fh:
        s = fh.read()
    print(f.split('src-tauri/src/')[1], 'braces:', s.count('{') - s.count('}'),
          'tap:', s.count('fn tap'), 'send_remote_key_once:', s.count('send_remote_key_once'))
