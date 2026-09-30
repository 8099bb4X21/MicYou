import io

path = r'E:/OpenCode/test/micyou-fork/composeApp/src/main/kotlin/com/lanrhyme/micyou/ui/MobileHome.kt'
with io.open(path, 'r', encoding='utf-8', newline='') as f:
    src = f.read()

start_marker = '// ==================== Main Button ===================='
start = src.find(start_marker)
assert start != -1, 'main button section not found'
# Section runs to end of file.
head = src[:start]

new_section = '''// ==================== Main Button ====================

@Composable
private fun MobileMainButton(
    isRunning: Boolean,
    isConnecting: Boolean,
    viewModel: MainViewModel
) {
    // Text button: static, compact, no icon, no animation.
    val label = when {
        isRunning -> stringResource(R.string.stop)
        isConnecting -> stringResource(R.string.statusConnecting)
        else -> stringResource(R.string.start)
    }
    val containerColor = when {
        isRunning -> MaterialTheme.colorScheme.error
        isConnecting -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Button(
        onClick = {
            if (isRunning || isConnecting) {
                viewModel.stopStream()
            } else {
                viewModel.startStream()
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        modifier = Modifier.testTag("home_main_button")
    ) {
        Text(label)
    }
}
'''

with io.open(path, 'w', encoding='utf-8', newline='') as f:
    f.write(head + new_section)
print('MobileMainButton replaced; new total lines:', (head + new_section).count(chr(10)) + 1)
