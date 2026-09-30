import io

path = r'E:/OpenCode/test/micyou-fork/composeApp/src/main/kotlin/com/lanrhyme/micyou/ui/MobileHome.kt'
with io.open(path, 'r', encoding='utf-8', newline='') as f:
    src = f.read()

start_marker = '// ==================== Main Control ===================='
end_marker = '// ==================== Bottom Bar ===================='
start = src.find(start_marker)
end = src.find(end_marker)
assert start != -1 and end != -1 and start < end, 'markers not found'
removed = src[start:end]
assert 'private fun MainControlCard(' in removed, 'MainControlCard not in range'
assert 'MobileStatusText' in removed, 'unexpected range'
new_src = src[:start] + src[end:]
with io.open(path, 'w', encoding='utf-8', newline='') as f:
    f.write(new_src)
print('removed lines:', removed.count(chr(10)))
print('MobileStatusText still present:', 'private fun MobileStatusText(' in new_src)
print('MainControlCard still present:', 'MainControlCard(' in new_src)
