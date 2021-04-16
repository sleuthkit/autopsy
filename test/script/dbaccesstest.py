from typing import List, Dict

import psycopg2
import sqlite3


def get_sqlite_table_columns(conn) -> Dict[str, List[str]]:
    cur = conn.cursor()
    cur.execute("SELECT name FROM sqlite_master tables WHERE tables.type='table'")
    tables = list([table[0] for table in cur.fetchall()])
    cur.close()

    to_ret = {}
    for table in tables:
        cur = conn.cursor()
        cur.execute('SELECT name FROM pragma_table_info(?) ORDER BY cid', [table])
        to_ret[table] = list([col[0] for col in cur.fetchall()])

    return to_ret


def get_pg_table_columns(conn) -> Dict[str, List[str]]:
    cursor = conn.cursor()
    cursor.execute("""
    SELECT cols.table_name, cols.column_name
      FROM information_schema.columns cols
      WHERE cols.column_name IS NOT NULL
      AND cols.table_name IS NOT NULL
      AND cols.table_name IN (
        SELECT tables.tablename FROM pg_catalog.pg_tables tables
        WHERE LOWER(schemaname) = 'public'
      )
    ORDER by cols.table_name, cols.ordinal_position;
    """)
    mapping = {}
    for row in cursor:
        mapping.setdefault(row[0], []).append(row[1])

    cursor.close()
    conn.close()
    return mapping

#for key, val in get_pg_table_columns(psycopg2.connect(dbname="jythontest1_20200414_124128", user="postgres", password="password12345")).items():
#for key, val in get_sqlite_table_columns(sqlite3.connect(r"C:\Users\gregd\Documents\cases\7500-take4\autopsy.db")).items():
#    print(f"{key}: {val}")