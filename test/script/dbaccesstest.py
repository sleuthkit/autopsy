from typing import List, Dict, Callable, Union

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


IGNORE_TABLE = "IGNORE_TABLE"


class TskDbEnvironment:
    pass


class MaskRow:
    row_masker: Callable[[TskDbEnvironment, Dict[str, any]], Dict[str, any]]

    def __init__(self, row_masker: Callable[[TskDbEnvironment, Dict[str, any]], Union[Dict[str, any], None]]):
        self.row_masker = row_masker

    def mask(self, db_env: TskDbEnvironment, row: Dict[str, any]) -> Union[Dict[str, any], None]:
        return self.row_masker(db_env, row)


class MaskColumns(MaskRow):
    @classmethod
    def _mask_col_vals(cls,
                       col_mask: Dict[str, Union[any, Callable[[TskDbEnvironment, any], any]]],
                       db_env: TskDbEnvironment,
                       row: Dict[str, any]):

        row_copy = dict.copy()
        for key, val in col_mask:
            # only replace values if present in row
            if key in row_copy:
                # if a column replacing function, call with original value
                if isinstance(val, Callable):
                    row_copy[key] = val(db_env, row[key])
                # otherwise, just replace with mask value
                else:
                    row_copy[key] = val

        return row_copy

    def __init__(self, col_mask: Dict[str, Union[any, Callable[[any], any]]]):
        super().__init__(lambda db_env, row: MaskColumns._mask_col_vals(col_mask, db_env, row))


TableNormalization = Union[IGNORE_TABLE, MaskRow]


MASKED_OBJ_ID = "MASKED_OBJ_ID"
MASKED_ID = "MASKED_ID"

table_masking: Dict[str, TableNormalization] = {
    "tsk_files": MaskColumns({
        # TODO
    }),

    "tsk_vs_parts": MaskColumns({
        "obj_id": MASKED_OBJ_ID
    }),
    "image_gallery_groups": MaskColumns({
        "obj_id": MASKED_OBJ_ID
    }),
    "image_gallery_groups_seen": IGNORE_TABLE,
    # NOTE there was code in normalization for this, but the table is ignored?
    # "image_gallery_groups_seen": MaskColumns({
    #     "id": MASKED_ID,
    #     "group_id": MASKED_ID,
    # }),
    # TODO
    "tsk_files_path": None,
    # TODO
    "tsk_file_layout": None,
    "tsk_objects": None,
    "reports": MaskColumns({
        "obj_id": MASKED_OBJ_ID,
        "path": "AutopsyTestCase",
        "crtime": 0
    }),
    "data_source_info": MaskColumns({
        "device_id": "{device id}",
        "added_date_time": "{dateTime}"
    }),
    # TODO
    "ingest_jobs": None,
    "tsk_examiners": MaskColumns({
        "login_name": "{examiner_name}"
    }),
    "tsk_events": MaskColumns({
        "event_id": "MASKED_EVENT_ID",
        "time": 0,
    }),
    # TODO
    "event_description_index": None,
    "tsk_os_accounts": MaskColumns({
        "os_account_obj_id": MASKED_OBJ_ID
    }),
    # TODO
    "tsk_data_artifacts": None
}


#     files_index = line.find('INSERT INTO "tsk_files"') > -1 or line.find('INSERT INTO tsk_files ') > -1
# path_index = line.find('INSERT INTO "tsk_files_path"') > -1 or line.find('INSERT INTO tsk_files_path ') > -1
# object_index = line.find('INSERT INTO "tsk_objects"') > -1 or line.find('INSERT INTO tsk_objects ') > -1
# vs_parts_index = line.find('INSERT INTO "tsk_vs_parts"') > -1 or line.find('INSERT INTO tsk_vs_parts ') > -1
# report_index = line.find('INSERT INTO "reports"') > -1 or line.find('INSERT INTO reports ') > -1
# layout_index = line.find('INSERT INTO "tsk_file_layout"') > -1 or line.find('INSERT INTO tsk_file_layout ') > -1
# data_source_info_index = line.find('INSERT INTO "data_source_info"') > -1 or line.find(
#     'INSERT INTO data_source_info ') > -1
# event_description_index = line.find('INSERT INTO "tsk_event_descriptions"') > -1 or line.find(
#     'INSERT INTO tsk_event_descriptions ') > -1
# events_index = line.find('INSERT INTO "tsk_events"') > -1 or line.find('INSERT INTO tsk_events ') > -1
# ingest_job_index = line.find('INSERT INTO "ingest_jobs"') > -1 or line.find('INSERT INTO ingest_jobs ') > -1
# examiners_index = line.find('INSERT INTO "tsk_examiners"') > -1 or line.find('INSERT INTO tsk_examiners ') > -1
# ig_groups_index = line.find('INSERT INTO "image_gallery_groups"') > -1 or line.find(
#     'INSERT INTO image_gallery_groups ') > -1
# ig_groups_seen_index = line.find('INSERT INTO "image_gallery_groups_seen"') > -1 or line.find(
#     'INSERT INTO image_gallery_groups_seen ') > -1
# os_account_index = line.find('INSERT INTO "tsk_os_accounts"') > -1 or line.find('INSERT INTO tsk_os_accounts') > -1
# os_account_attr_index = line.find('INSERT INTO "tsk_os_account_attributes"') > -1 or line.find(
#     'INSERT INTO tsk_os_account_attributes') > -1
# os_account_instances_index = line.find('INSERT INTO "tsk_os_account_instances"') > -1 or line.find(
#     'INSERT INTO tsk_os_account_instances') > -1
# data_artifacts_index = line.find('INSERT INTO "tsk_data_artifacts"') > -1 or line.find(
#     'INSERT INTO tsk_data_artifacts') > -1

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
