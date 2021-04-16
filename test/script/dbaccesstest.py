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
    return mapping


def get_sql_insert_value(val) -> str:
    if not val:
        return "NULL"

    if isinstance(val, str):
        escaped_val = val.replace('\n', '\\n').replace("'", "''")
        return f"'{escaped_val}'"

    return str(val)


def write_normalized(output_file, db_conn, table: str, column_names: List[str], normalizer=None):
    cursor = db_conn.cursor()

    joined_columns = ",".join([col for col in column_names])
    cursor.execute(f"SELECT {joined_columns} FROM {table}")
    for row in cursor:
        if len(row) != len(column_names):
            print(f"ERROR: in {table}, number of columns retrieved: {len(row)} but columns are {len(column_names)} with {str(column_names)}")
            continue

        row_dict = {}
        for col_idx in range(0, len(column_names)):
            row_dict[column_names[col_idx]] = row[col_idx]

        if normalizer:
            row_dict = normalizer(table, row_dict)

        values_statement = ",".join(get_sql_insert_value(row_dict[col]) for col in column_names)
        insert_statement = f'INSERT INTO "{table}" VALUES({values_statement})\n'
        output_file.write(insert_statement)




#with sqlite3.connect(r"C:\Users\gregd\Desktop\autopsy_412.db") as conn, \
with psycopg2.connect(dbname="jythontest1_20200414_124128", user="postgres", password="password12345") as conn, \
        open(r"C:\Users\gregd\Desktop\dbdump.sql", mode="w", encoding='utf-8') as output_file:

    for table, cols in get_pg_table_columns(conn).items():
    # for table, cols in get_sqlite_table_columns(conn).items():
        write_normalized(output_file, conn, table, cols)
