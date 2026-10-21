"""Create a consistent SQLite backup without copying a live WAL database file."""
import argparse
import os
from pathlib import Path
import sqlite3


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', default=os.getenv('DAYFLOW_DB', 'dayflow.db'))
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    source, target = Path(args.database).resolve(), Path(args.output).resolve()
    if not source.is_file() or source == target:
        parser.error('The source must exist and output must be a different path.')
    target.parent.mkdir(parents=True, exist_ok=True)
    try:
        target.open('xb').close()
    except FileExistsError:
        parser.error('Output already exists; choose a new backup filename.')
    try:
        with sqlite3.connect(source.as_uri() + '?mode=ro', uri=True) as database:
            with sqlite3.connect(target) as backup:
                database.backup(backup, pages=256, sleep=0.05)
    except BaseException:
        target.unlink(missing_ok=True)
        raise
    print('Backup created:', target)


if __name__ == '__main__':
    main()
