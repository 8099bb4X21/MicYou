import io
import re

path = r'E:/OpenCode/test/micyou-fork/composeApp/src/main/kotlin/com/lanrhyme/micyou/audio/AudioEngine.kt'
with io.open(path, 'r', encoding='utf-8', newline='') as f:
    src = f.read()

# 1. Restore direct assignments.
src, n = re.subn(r'updateStreamState\(StreamState\.(\w+)\)', r'_state.value = StreamState.\1', src)
print('assignments restored:', n)

# 2. Remove the funnel function.
start_marker = '    /**\n     * 状态唯一出口：同步维护远程按键总线'
start = src.find(start_marker)
assert start != -1, 'funnel not found'
end_marker = '    }\n'
end = src.find(end_marker, start)
assert end != -1, 'funnel end not found'
funnel = src[start:end + len(end_marker)]
assert 'RemoteKeyBus' in funnel, 'unexpected funnel body'
src = src[:start] + src[end + len(end_marker):]
print('funnel removed')

# 3. Remove the import.
old_imp = 'import com.lanrhyme.micyou.service.RemoteKeyBus\n'
assert old_imp in src, 'import not found'
src = src.replace(old_imp, '')
print('import removed')

with io.open(path, 'w', encoding='utf-8', newline='') as f:
    f.write(src)

assert 'updateStreamState' not in src, 'leftover updateStreamState'
assert 'RemoteKeyBus' not in src, 'leftover RemoteKeyBus'
print('OK: no leftovers')
