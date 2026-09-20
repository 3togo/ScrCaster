#!/usr/bin/env python3
"""Test install-time compilation without a connected Android device."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('optimize-apk.sh')

class OptimizeApkTest(unittest.TestCase):
    def invoke(self, output='Success', status=0, skip=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            adb = root / 'adb'
            calls = root / 'calls'
            adb.write_text('#!/bin/sh\nprintf "%s\\n" "$@" > "$CALLS"\nprintf "%s\\n" "$OUTPUT"\nexit "$STATUS"\n')
            adb.chmod(0o755)
            result = subprocess.run(['bash', str(SCRIPT), str(adb), 'tv:5555', 'io.github.togo3.scrcaster.tvdebug'],
                env={**os.environ, 'CALLS': str(calls), 'OUTPUT': output, 'STATUS': str(status),
                     'SCRCASTER_SKIP_DEXOPT': '1' if skip else '0'}, capture_output=True, text=True)
            return result, calls.read_text().splitlines() if calls.exists() else []

    def test_compiles_the_selected_package_without_forcing_recompilation(self):
        result, calls = self.invoke()
        self.assertEqual(0, result.returncode)
        self.assertEqual(['-s', 'tv:5555', 'shell', 'cmd', 'package', 'compile', '-m', 'speed',
                          'io.github.togo3.scrcaster.tvdebug'], calls)
        self.assertIn('completed', result.stdout)

    def test_unavailable_compiler_does_not_fail_install(self):
        result, _ = self.invoke('Unknown command', 1)
        self.assertEqual(0, result.returncode)
        self.assertIn('Warning:', result.stderr)
        self.assertNotIn('completed', result.stdout)

    def test_oem_failure_with_zero_exit_status_is_not_reported_as_success(self):
        result, _ = self.invoke('Failure', 0)
        self.assertIn('Warning:', result.stderr)
        self.assertNotIn('completed', result.stdout)

    def test_skip_does_not_contact_device(self):
        result, calls = self.invoke(skip=True)
        self.assertEqual(0, result.returncode)
        self.assertEqual([], calls)

if __name__ == '__main__':
    unittest.main()
