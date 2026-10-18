"""Check emitted code, not just patch source fixtures. No Android runtime claim."""
import json
import os
from common import ROOT, BUILD, android_paths, bootstrap, java_tool, run
from validate_apk import ValidationError


def inspect_visual_bytecode(apk):
    android, _ = android_paths()
    cli, _ = bootstrap()
    classes = BUILD / 'visual-dex-guard'
    classes.mkdir(exist_ok=True, parents=True)
    run([java_tool('javac'), '-cp', cli, '-d', classes, ROOT / 'scripts/NativeVisualDexGuard.java'], capture=True)
    result = json.loads(run([java_tool('java'), '-Xmx3g', '-cp', os.pathsep.join(map(str, (classes, cli, android))),
                             'NativeVisualDexGuard', apk], capture=True))
    if result.get('status') != 'passed' or result.get('guarded_native_classes') != 6 or result.get('analyzed_methods', 0) < 200:
        raise ValidationError('Final visual bytecode guard did not complete.')
    return result
