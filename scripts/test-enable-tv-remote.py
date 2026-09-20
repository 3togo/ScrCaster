#!/usr/bin/env python3
"""Exercise the installer against a fake ADB without changing device settings."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('enable-tv-remote.sh')
COMPONENT = 'io.github.togo3.scrcaster.tvdebug/io.github.togo3.scrcaster.TvRemoteAccessibilityService'
FAKE = '''#!/usr/bin/env python3
import json, os, shlex, sys
p = os.environ['FAKE_ADB_STATE']
s = json.load(open(p))
a = sys.argv[1:]
assert a[:3] == ['-s', 'test-tv', 'shell'], a
a = a[3:]
if a == ['pm', 'list', 'features']: print('feature:android.software.leanback' if s.get('tv', True) else '')
elif a[:1] == ['getprop']: print('')
elif a == ['am', 'get-current-user']: print('10')
elif a[:3] == ['settings', '--user', '10']:
    action, namespace, key = a[3:6]
    assert namespace == 'secure'
    if action == 'get': print(s.get(key, 'null'))
    elif action == 'put':
        if s.get('reject'): sys.exit(1)
        s[key] = shlex.split(a[6])[0]
        json.dump(s, open(p, 'w'))
else: raise AssertionError(a)
'''

class EnableRemoteTest(unittest.TestCase):
    def run_setup(self, state, repeat=1, package="io.github.togo3.scrcaster.tvdebug"):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            adb = path / 'adb'
            adb.write_text(FAKE)
            adb.chmod(0o755)
            data = path / 'state.json'
            data.write_text(json.dumps(state))
            for _ in range(repeat):
                result = subprocess.run(['bash', str(SCRIPT), str(adb), 'test-tv', package],
                    env={**os.environ, 'FAKE_ADB_STATE': str(data)}, capture_output=True, text=True)
            return result, json.loads(data.read_text())

    def test_fresh_tv_and_repeat_install(self):
        result, state = self.run_setup({}, repeat=2)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(COMPONENT, state['enabled_accessibility_services'])
        self.assertEqual('1', state['accessibility_enabled'])

    def test_preserves_other_services_including_inner_class(self):
        original = 'example.reader/.Reader:example.other/.Outer$Service'
        result, state = self.run_setup({'enabled_accessibility_services': original})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(original + ':' + COMPONENT, state['enabled_accessibility_services'])

    def test_short_component_is_not_duplicated(self):
        short = 'io.github.togo3.scrcaster/.TvRemoteAccessibilityService'
        result, state = self.run_setup({'enabled_accessibility_services': short}, package='io.github.togo3.scrcaster')
        self.assertEqual(0, result.returncode)
        self.assertEqual(short, state['enabled_accessibility_services'])

    def test_phone_is_unchanged(self):
        original = {'tv': False, 'enabled_accessibility_services': 'example.reader/.Reader'}
        result, state = self.run_setup(original)
        self.assertEqual(0, result.returncode)
        self.assertEqual(original, state)

    def test_rejected_setup_fails(self):
        result, _ = self.run_setup({'reject': True})
        self.assertNotEqual(0, result.returncode)

if __name__ == '__main__':
    unittest.main()
