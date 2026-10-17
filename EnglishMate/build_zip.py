from __future__ import annotations

import argparse
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

EXCLUDED_DIRS = {'.git', '.idea', 'node_modules', 'target', 'dist', '__pycache__'}
EXCLUDED_FILES = {'.env', 'EnglishMate_Fixed.zip'}


def should_include(path: Path, root: Path) -> bool:
    relative = path.relative_to(root)
    if any(part in EXCLUDED_DIRS for part in relative.parts):
        return False
    if path.name in EXCLUDED_FILES:
        return False
    return path.is_file()


def build_zip(project_dir: Path, output: Path) -> None:
    project_dir = project_dir.resolve()
    output = output.resolve()
    if not project_dir.is_dir():
        raise SystemExit(f'项目目录不存在: {project_dir}')

    if output.exists():
        output.unlink()

    with ZipFile(output, 'w', compression=ZIP_DEFLATED, compresslevel=9) as zf:
        for path in sorted(project_dir.rglob('*')):
            if should_include(path, project_dir):
                arcname = Path('EnglishMate_Fixed') / path.relative_to(project_dir)
                zf.write(path, arcname.as_posix())

    with ZipFile(output, 'r') as zf:
        bad = zf.testzip()
        if bad is not None:
            raise SystemExit(f'ZIP 完整性校验失败: {bad}')

    print(f'已生成: {output}')


def main() -> None:
    parser = argparse.ArgumentParser(description='打包 EnglishMate 修复工程')
    parser.add_argument('--project', default='.', help='EnglishMate_Fixed 项目根目录，默认当前目录')
    parser.add_argument('--output', default='EnglishMate_Fixed.zip', help='输出 ZIP 路径')
    args = parser.parse_args()
    build_zip(Path(args.project), Path(args.output))


if __name__ == '__main__':
    main()
