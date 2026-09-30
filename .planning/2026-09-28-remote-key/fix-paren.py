import io

path = r'E:/OpenCode/test/micyou-fork/composeApp/src/main/kotlin/com/lanrhyme/micyou/audio/AudioEngine.kt'
with io.open(path, 'r', encoding='utf-8', newline='') as f:
    lines = f.read().split('\n')

fixed = 0
for i, line in enumerate(lines):
    stripped = line.rstrip()
    if 'updateStreamState(StreamState.' in stripped and not stripped.endswith(')'):
        lines[i] = stripped + ')'
        fixed += 1

with io.open(path, 'w', encoding='utf-8', newline='') as f:
    f.write('\n'.join(lines))
print('fixed lines:', fixed)
