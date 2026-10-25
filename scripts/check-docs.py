"""Read-only inline Markdown/link and tracked-path hygiene checks (stdlib only)."""
from pathlib import Path
import re
import subprocess
import unicodedata
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
LINK = re.compile(r'!?\[[^\]]*\]\(([^\s)]+)(?:\s+"[^"]*")?\)')


def blocked(path):
    return (path == 'AGENTS.md'
            or path.startswith(('.opencode/', '.codegraph/', 'tasks/', '.cache/', '.artifacts/'))
            or path.lower().endswith(('.pem', '.key', '.pfx', '.p12'))
            or any((part == '.env' or part.startswith('.env.'))
                   and not part.endswith('.example') for part in Path(path).parts))


def anchors(text):
    result, counts = set(), {}
    fenced = False
    for line in text.splitlines():
        if line.lstrip().startswith('```'):
            fenced = not fenced
        match = re.match(r'^#{1,6}\s+(.+?)\s*#*$', line)
        if fenced or not match:
            continue
        title = re.sub(r'\[([^\]]+)\]\([^)]*\)', r'\1', match[1])
        title = title.replace('`', '').replace('*', '').lower()
        slug = ''.join(c for c in title if unicodedata.category(c)[0] in 'LNM'
                       or c in '_ -').replace(' ', '-')
        count = counts.get(slug, 0)
        counts[slug] = count + 1
        result.add(slug + (f'-{count}' if count else ''))
    return result


def check_file(path):
    text = path.read_text(encoding='utf-8-sig')
    errors = []
    if sum(line.lstrip().startswith('```') for line in text.splitlines()) % 2:
        errors.append('unbalanced triple-backtick fences')
    for number, line in enumerate(text.splitlines(), 1):
        if line != line.rstrip():
            errors.append(f'line {number}: trailing whitespace')
        if line.startswith(('<<<<<<<', '=======', '>>>>>>>')):
            errors.append(f'line {number}: conflict marker')
    for match in LINK.finditer(text):
        target = match[1]
        if target.startswith('//') or urlsplit(target).scheme:
            continue
        part, _, fragment = target.partition('#')
        destination = path.parent / unquote(part.split('?')[0]) if part else path
        if not destination.exists():
            errors.append(f'missing target: {target}')
        elif fragment and destination.suffix.lower() == '.md':
            if unquote(fragment) not in anchors(destination.read_text(encoding='utf-8-sig')):
                errors.append(f'missing heading anchor: {target}')
    return errors


def git_files(*args):
    output = subprocess.check_output(['git', '-C', str(ROOT), 'ls-files', '-z', *args])
    return [name for name in output.decode('utf-8').split('\0') if name]


def main():
    errors = [f'blocked tracked path: {name}' for name in git_files() if blocked(name)]
    files = sorted(set(git_files('--cached', '--others', '--exclude-standard', '--', '*.md')))
    checked = 0
    for name in files:
        path = ROOT / name
        if not path.is_file():
            continue  # A worktree deletion is checked by the author's staged diff review.
        checked += 1
        errors.extend(f'{name}: {error}' for error in check_file(path))
    for error in errors:
        print(error)
    print(f'Checked {checked} Markdown files; {len(errors)} findings. '
          'Inline links/approximate heading slugs only; no external URL or full secret scan.')
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
