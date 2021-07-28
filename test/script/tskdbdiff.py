# Requires python3

import re
import sqlite3
import subprocess
import shutil
import os
import codecs
import datetime
import sys
from typing import Callable, Dict, Union, List

import psycopg2
import psycopg2.extras
import socket
import csv

class TskDbDiff(object):
    """Compares two TSK/Autospy SQLite databases.

    Attributes:
        gold_artifacts:
        autopsy_artifacts:
        gold_attributes:
        autopsy_attributes:
        gold_objects:
        autopsy_objects:
        artifact_comparison:
        attribute_comparision:
        report_errors: a listof_listof_String, the error messages that will be
        printed to screen in the run_diff method
        passed: a boolean, did the diff pass?
        autopsy_db_file:
        gold_db_file:
    """
    def __init__(self, output_db, gold_db, output_dir=None, gold_bb_dump=None, gold_dump=None, verbose=False, isMultiUser=False, pgSettings=None):
        """Constructor for TskDbDiff.

        Args:
            output_db_path: path to output database (non-gold standard)
            gold_db_path: path to gold database
            output_dir: (optional) Path to folder where generated files will be put.
            gold_bb_dump: (optional) path to file where the gold blackboard dump is located
            gold_dump: (optional) path to file where the gold non-blackboard dump is located
            verbose: (optional) a boolean, if true, diff results are sent to stdout. 
        """

        self.output_db_file = output_db
        self.gold_db_file = gold_db
        self.output_dir = output_dir
        self.gold_bb_dump = gold_bb_dump
        self.gold_dump = gold_dump
        self._generate_gold_dump = False        
        self._generate_gold_bb_dump = False
        self._bb_dump_diff = ""
        self._dump_diff = ""
        self._bb_dump = ""
        self._dump = ""
        self.verbose = verbose
        self.isMultiUser = isMultiUser
        self.pgSettings = pgSettings

        if self.isMultiUser and not self.pgSettings:
            print("Missing PostgreSQL database connection settings data.")
            sys.exit(1)

        if self.gold_bb_dump is None:
            self._generate_gold_bb_dump = True
        if self.gold_dump is None:
            self._generate_gold_dump = True

    def run_diff(self):
        """Compare the databases.

        Raises:
            TskDbDiffException: if an error occurs while diffing or dumping the database
        """

        self._init_diff()
        id_obj_path_table = -1
        # generate the gold database dumps if necessary     
        if self._generate_gold_dump:       
            id_obj_path_table = TskDbDiff._dump_output_db_nonbb(self.gold_db_file, self.gold_dump, self.isMultiUser, self.pgSettings)     
        if self._generate_gold_bb_dump:        
            TskDbDiff._dump_output_db_bb(self.gold_db_file, self.gold_bb_dump, self.isMultiUser, self.pgSettings, id_obj_path_table)

        # generate the output database dumps (both DB and BB)
        id_obj_path_table = TskDbDiff._dump_output_db_nonbb(self.output_db_file, self._dump, self.isMultiUser, self.pgSettings)
        TskDbDiff._dump_output_db_bb(self.output_db_file, self._bb_dump, self.isMultiUser, self.pgSettings, id_obj_path_table)

        # Compare non-BB
        dump_diff_pass = self._diff(self._dump, self.gold_dump, self._dump_diff)

        # Compare BB
        bb_dump_diff_pass = self._diff(self._bb_dump, self.gold_bb_dump, self._bb_dump_diff)

        self._cleanup_diff()
        return dump_diff_pass, bb_dump_diff_pass


    def _init_diff(self):
        """Set up the necessary files based on the arguments given at construction"""
        if self.output_dir is None:
            # No stored files
            self._bb_dump = TskDbDiff._get_tmp_file("BlackboardDump", ".txt")
            self._bb_dump_diff = TskDbDiff._get_tmp_file("BlackboardDump-Diff", ".txt")
            self._dump = TskDbDiff._get_tmp_file("DBDump", ".txt")
            self._dump_diff = TskDbDiff._get_tmp_file("DBDump-Diff", ".txt")
        else:
            self._bb_dump = os.path.join(self.output_dir, "BlackboardDump.txt")
            self._bb_dump_diff = os.path.join(self.output_dir, "BlackboardDump-Diff.txt")
            self._dump = os.path.join(self.output_dir, "DBDump.txt")
            self._dump_diff = os.path.join(self.output_dir, "DBDump-Diff.txt")

        # Sorting gold before comparing (sort behaves differently in different environments)
        new_bb = TskDbDiff._get_tmp_file("GoldBlackboardDump", ".txt")
        new_db = TskDbDiff._get_tmp_file("GoldDBDump", ".txt")
        if self.gold_bb_dump is not None:
            srtcmdlst = ["sort", self.gold_bb_dump, "-o", new_bb]
            subprocess.call(srtcmdlst)
            srtcmdlst = ["sort", self.gold_dump, "-o", new_db]
            subprocess.call(srtcmdlst)
        self.gold_bb_dump = new_bb
        self.gold_dump = new_db


    def _cleanup_diff(self):
        if self.output_dir is None:
            #cleanup temp files
            os.remove(self._dump)
            os.remove(self._bb_dump)
            if os.path.isfile(self._dump_diff):
                os.remove(self._dump_diff)
            if os.path.isfile(self._bb_dump_diff):
                os.remove(self._bb_dump_diff)

        if self.gold_bb_dump is None:
            os.remove(self.gold_bb_dump)
            os.remove(self.gold_dump)


    def _diff(self, output_file, gold_file, diff_path):
        """Compare two text files.

        Args:
            output_file: a pathto_File, the latest text file
            gold_file: a pathto_File, the gold text file
            diff_path: The file to write the differences to
        Returns False if different
        """

        if (not os.path.isfile(output_file)):
            return False

        if (not os.path.isfile(gold_file)):
            return False

        # It is faster to read the contents in and directly compare
        output_data = codecs.open(output_file, "r", "utf_8").read()
        gold_data = codecs.open(gold_file, "r", "utf_8").read()
        if (gold_data == output_data):
            return True

        # If they are different, invoke 'diff'
        diff_file = codecs.open(diff_path, "wb", "utf_8")
        # Gold needs to be passed in as 1st arg and output as 2nd
        dffcmdlst = ["diff", gold_file, output_file]
        subprocess.call(dffcmdlst, stdout = diff_file)

        # create file path for gold files inside output folder. In case of diff, both gold and current run files
        # are available in the report output folder. Prefix Gold- is added to the filename.
        gold_file_in_output_dir = os.path.join(os.path.dirname(output_file), "Gold-" + os.path.basename(output_file))
        shutil.copy(gold_file, gold_file_in_output_dir)

        return False


    @staticmethod
    def _get_associated_artifact_type(cur, artifact_id, isMultiUser):
        if isMultiUser:
            cur.execute(
                "SELECT tsk_files.parent_path, blackboard_artifact_types.display_name FROM blackboard_artifact_types INNER JOIN blackboard_artifacts ON blackboard_artifact_types.artifact_type_id = blackboard_artifacts.artifact_type_id INNER JOIN tsk_files ON tsk_files.obj_id = blackboard_artifacts.obj_id WHERE artifact_id=%s",
                [artifact_id])
        else:
            cur.execute(
                "SELECT tsk_files.parent_path, blackboard_artifact_types.display_name FROM blackboard_artifact_types INNER JOIN blackboard_artifacts ON blackboard_artifact_types.artifact_type_id = blackboard_artifacts.artifact_type_id INNER JOIN tsk_files ON tsk_files.obj_id = blackboard_artifacts.obj_id WHERE artifact_id=?",
                [artifact_id])

        info = cur.fetchone()

        return "File path: " + info[0] + " Artifact Type: " + info[1]


    @staticmethod
    def _dump_output_db_bb(db_file, bb_dump_file, isMultiUser, pgSettings, id_obj_path_table):
        """Dumps sorted text results to the given output location.

        Smart method that deals with a blackboard comparison to avoid issues
        with different IDs based on when artifacts were created.

        Args:
            db_file: a pathto_File, the output database.
            bb_dump_file: a pathto_File, the sorted dump file to write to
        """

        unsorted_dump = TskDbDiff._get_tmp_file("dump_data", ".txt")
        if isMultiUser:
            conn, unused_db = db_connect(db_file, isMultiUser, pgSettings)
            artifact_cursor = conn.cursor(cursor_factory=psycopg2.extras.DictCursor)
        else: # Use Sqlite
            conn = sqlite3.connect(db_file)
            conn.text_factory = lambda x: x.decode("utf-8", "ignore")
            conn.row_factory = sqlite3.Row
            artifact_cursor = conn.cursor()
        # Get the list of all artifacts (along with type and associated file)
        # @@@ Could add a SORT by parent_path in here since that is how we are going to later sort it.
        artifact_cursor.execute("SELECT tsk_files.parent_path, tsk_files.name, blackboard_artifact_types.display_name, blackboard_artifacts.artifact_id FROM blackboard_artifact_types INNER JOIN blackboard_artifacts ON blackboard_artifact_types.artifact_type_id = blackboard_artifacts.artifact_type_id INNER JOIN tsk_files ON tsk_files.obj_id = blackboard_artifacts.obj_id")
        database_log = codecs.open(unsorted_dump, "wb", "utf_8")
        row = artifact_cursor.fetchone()
        appnd = False
        counter = 0
        artifact_count = 0
        artifact_fail = 0

        # Cycle through artifacts
        try:
            while (row != None):

                # File Name and artifact type
                # Remove parent object ID from Unalloc file name
                normalizedName = re.sub('^Unalloc_[0-9]+_', 'Unalloc_', row["name"])
                if(row["parent_path"] != None):
                    database_log.write(row["parent_path"] + normalizedName + ' <artifact type="' + row["display_name"] + '" > ')
                else:
                    database_log.write(normalizedName + ' <artifact type="' + row["display_name"] + '" > ')

                if isMultiUser:
                    attribute_cursor = conn.cursor(cursor_factory=psycopg2.extras.DictCursor)
                else:
                    attribute_cursor = conn.cursor()
                looptry = True
                artifact_count += 1
                try:
                    art_id = ""
                    art_id = str(row["artifact_id"])
                  
                    # Get attributes for this artifact
                    if isMultiUser:
                        attribute_cursor.execute("SELECT blackboard_attributes.source, blackboard_attributes.attribute_type_id, blackboard_attribute_types.display_name, blackboard_attributes.value_type, blackboard_attributes.value_text, blackboard_attributes.value_int32, blackboard_attributes.value_int64, blackboard_attributes.value_double FROM blackboard_attributes INNER JOIN blackboard_attribute_types ON blackboard_attributes.attribute_type_id = blackboard_attribute_types.attribute_type_id WHERE artifact_id = %s ORDER BY blackboard_attributes.source, blackboard_attribute_types.display_name, blackboard_attributes.value_type, blackboard_attributes.value_text, blackboard_attributes.value_int32, blackboard_attributes.value_int64, blackboard_attributes.value_double", [art_id])
                    else:
                        attribute_cursor.execute("SELECT blackboard_attributes.source, blackboard_attributes.attribute_type_id, blackboard_attribute_types.display_name, blackboard_attributes.value_type, blackboard_attributes.value_text, blackboard_attributes.value_int32, blackboard_attributes.value_int64, blackboard_attributes.value_double FROM blackboard_attributes INNER JOIN blackboard_attribute_types ON blackboard_attributes.attribute_type_id = blackboard_attribute_types.attribute_type_id WHERE artifact_id =? ORDER BY blackboard_attributes.source, blackboard_attribute_types.display_name, blackboard_attributes.value_type, blackboard_attributes.value_text, blackboard_attributes.value_int32, blackboard_attributes.value_int64, blackboard_attributes.value_double", [art_id])
                    
                    attributes = attribute_cursor.fetchall()
                
                    # Print attributes
                    if (len(attributes) == 0):
                       # @@@@ This should be </artifact> 
                       database_log.write(' <artifact/>\n')
                       row = artifact_cursor.fetchone()
                       continue

                    src = attributes[0][0]
                    for attr in attributes:
                        numvals = 0
                        for x in range(3, 6):
                            if(attr[x] != None):
                                numvals += 1
                        if(numvals > 1):
                            msg = "There were too many values for attribute type: " + attr["display_name"] + " for artifact with id #" + str(row["artifact_id"]) + ".\n"

                        if(not attr["source"] == src):
                            msg = "There were inconsistent sources for artifact with id #" + str(row["artifact_id"]) + ".\n"

                        try:
                            if attr["value_type"] == 0:
                                attr_value_as_string = str(attr["value_text"])                        
                            elif attr["value_type"] == 1:
                                attr_value_as_string = str(attr["value_int32"])                        
                            elif attr["value_type"] == 2:
                                attr_value_as_string = str(attr["value_int64"])
                                if attr["attribute_type_id"]  == 36 and id_obj_path_table != -1 and int(attr_value_as_string) > 0: #normalize positive TSK_PATH_IDs from being object id to a path if the obj_id_path_table was generated
                                    attr_value_as_string = id_obj_path_table[int(attr_value_as_string)]
                            elif attr["value_type"] == 3:
                                attr_value_as_string = "%20.10f" % float((attr["value_double"])) #use exact format from db schema to avoid python auto format double value to (0E-10) scientific style                       
                            elif attr["value_type"] == 4:
                                attr_value_as_string = "bytes"                        
                            elif attr["value_type"] == 5:
                                attr_value_as_string = str(attr["value_int64"])                        
                            if attr["display_name"] == "Associated Artifact":
                                attr_value_as_string = TskDbDiff._get_associated_artifact_type(attribute_cursor, attr_value_as_string, isMultiUser)
                            patrn = re.compile("[\n\0\a\b\r\f]")
                            attr_value_as_string = re.sub(patrn, ' ', attr_value_as_string)
                            if attr["source"] == "Keyword Search" and attr["display_name"] == "Keyword Preview":
                                attr_value_as_string = "<Keyword Preview placeholder>"
                            database_log.write('<attribute source="' + attr["source"] + '" type="' + attr["display_name"] + '" value="' + attr_value_as_string + '" />')
                        except IOError as e:
                            print("IO error")
                            raise TskDbDiffException("Unexpected IO error while writing to database log." + str(e))

                except sqlite3.Error as e:
                    msg = "Attributes in artifact id (in output DB)# " + str(row["artifact_id"]) + " encountered an error: " + str(e) +" .\n"
                    print("Attributes in artifact id (in output DB)# ", str(row["artifact_id"]), " encountered an error: ", str(e))
                    print() 
                    looptry = False
                    artifact_fail += 1
                    database_log.write('Error Extracting Attributes')
                    database_log.close()
                    raise TskDbDiffException(msg)
                finally:
                    attribute_cursor.close()

               
                # @@@@ This should be </artifact> 
                database_log.write(' <artifact/>\n')
                row = artifact_cursor.fetchone()

            if(artifact_fail > 0):
                msg ="There were " + str(artifact_count) + " artifacts and " + str(artifact_fail) + " threw an exception while loading.\n"
        except Exception as e:
            raise TskDbDiffException("Unexpected error while dumping blackboard database: " + str(e))
        finally:
            database_log.close()
            artifact_cursor.close()
            conn.close()
        
        # Now sort the file
        srtcmdlst = ["sort", unsorted_dump, "-o", bb_dump_file]
        subprocess.call(srtcmdlst)

    @staticmethod
    def _dump_output_db_nonbb(db_file, dump_file, isMultiUser, pgSettings):
        """Dumps a database to a text file.

        Does not dump the artifact and attributes.

        Args:
            db_file: a pathto_File, the database file to dump
            dump_file: a pathto_File, the location to dump the non-blackboard database items
        """

        conn, backup_db_file = db_connect(db_file, isMultiUser, pgSettings)
        guid_utils = TskGuidUtils.create(conn)

        if isMultiUser:
            table_cols = get_pg_table_columns(conn)
            schema = get_pg_schema(db_file, pgSettings.username, pgSettings.password,
                                   pgSettings.pgHost, pgSettings.pgPort)
        else:
            table_cols = get_sqlite_table_columns(conn)
            schema = get_sqlite_schema(conn)

        with codecs.open(dump_file, "wb", "utf_8") as output_file:
            output_file.write(schema + "\n")
            for table, cols in sorted(table_cols.items(), key=lambda pr: pr[0]):
                normalizer = TABLE_NORMALIZATIONS[table] if table in TABLE_NORMALIZATIONS else None
                write_normalized(guid_utils, output_file, conn, table, cols, normalizer)

        # Now sort the file
        srtcmdlst = ["sort", dump_file, "-o", dump_file]
        subprocess.call(srtcmdlst)

        conn.close()
        # cleanup the backup
        # if backup_db_file:
        #    os.remove(backup_db_file)
        return guid_utils.obj_id_guids

    @staticmethod
    def dump_output_db(db_file, dump_file, bb_dump_file, isMultiUser, pgSettings):
        """Dumps the given database to text files for later comparison.

        Args:
            db_file: a pathto_File, the database file to dump
            dump_file: a pathto_File, the location to dump the non-blackboard database items
            bb_dump_file: a pathto_File, the location to dump the blackboard database items
        """
        id_obj_path_table = TskDbDiff._dump_output_db_nonbb(db_file, dump_file, isMultiUser, pgSettings)
        TskDbDiff._dump_output_db_bb(db_file, bb_dump_file, isMultiUser, pgSettings, id_obj_path_table)

    @staticmethod
    def _get_tmp_file(base, ext):
        time = datetime.datetime.now().time().strftime("%H%M%f")
        return os.path.join(os.environ['TMP'], base + time + ext)


class TskDbDiffException(Exception):
    pass

class PGSettings(object):
    def __init__(self, pgHost=None, pgPort=5432, user=None, password=None):
        self.pgHost = pgHost
        self.pgPort = pgPort
        self.username = user
        self.password = password

    def get_pgHost(self):
        return self.pgHost

    def get_pgPort(self):
        return self.pgPort

    def get_username(self):
        return self.username

    def get_password(self):
        return self.password


class TskGuidUtils:
    """
    This class provides guids for potentially volatile data.
    """

    @staticmethod
    def _get_guid_dict(db_conn, select_statement, delim="", normalizer: Union[Callable[[str], str], None] = None):
        """
        Retrieves a dictionary mapping the first item selected to a concatenation of the remaining values.
        Args:
            db_conn: The database connection.
            select_statement: The select statement.
            delim: The delimiter for how row data from index 1 to end shall be concatenated.
            normalizer: Means of normalizing the generated string or None.

        Returns: A dictionary mapping the key (the first item in the select statement) to a concatenation of the remaining values.

        """
        cursor = db_conn.cursor()
        cursor.execute(select_statement)
        ret_dict = {}
        for row in cursor:
            # concatenate value rows with delimiter filtering out any null values.
            value_str = delim.join([str(col) for col in filter(lambda col: col is not None, row[1:])])
            if normalizer:
                value_str = normalizer(value_str)
            ret_dict[row[0]] = value_str

        return ret_dict

    @staticmethod
    def create(db_conn):
        """
        Creates an instance of this class by querying for relevant guid data.
        Args:
            db_conn: The database connection.

        Returns: The instance of this class.

        """
        guid_files = TskGuidUtils._get_guid_dict(db_conn, "SELECT obj_id, parent_path, name FROM tsk_files",
                                                 normalizer=normalize_file_path)
        guid_vs_parts = TskGuidUtils._get_guid_dict(db_conn, "SELECT obj_id, addr, start FROM tsk_vs_parts", "_")
        guid_vs_info = TskGuidUtils._get_guid_dict(db_conn, "SELECT obj_id, vs_type, img_offset FROM tsk_vs_info", "_")
        guid_fs_info = TskGuidUtils._get_guid_dict(db_conn, "SELECT obj_id, img_offset, fs_type FROM tsk_fs_info", "_")
        guid_image_names = TskGuidUtils._get_guid_dict(db_conn, "SELECT obj_id, name FROM tsk_image_names "
                                                                "WHERE sequence=0",
                                                       normalizer=get_filename)
        guid_os_accounts = TskGuidUtils._get_guid_dict(db_conn, "SELECT os_account_obj_id, addr FROM tsk_os_accounts")
        guid_reports = TskGuidUtils._get_guid_dict(db_conn, "SELECT obj_id, path FROM reports",
                                                   normalizer=normalize_file_path)

        objid_artifacts = TskGuidUtils._get_guid_dict(db_conn,
                                                      "SELECT blackboard_artifacts.artifact_obj_id, "
                                                      "blackboard_artifact_types.type_name "
                                                      "FROM blackboard_artifacts "
                                                      "INNER JOIN blackboard_artifact_types "
                                                      "ON blackboard_artifact_types.artifact_type_id = "
                                                      "blackboard_artifacts.artifact_type_id")

        artifact_objid_artifacts = TskGuidUtils._get_guid_dict(db_conn,
                                                               "SELECT blackboard_artifacts.artifact_id, "
                                                               "blackboard_artifact_types.type_name "
                                                               "FROM blackboard_artifacts "
                                                               "INNER JOIN blackboard_artifact_types "
                                                               "ON blackboard_artifact_types.artifact_type_id = "
                                                               "blackboard_artifacts.artifact_type_id")

        cursor = db_conn.cursor()
        cursor.execute("SELECT obj_id, par_obj_id FROM tsk_objects")
        par_obj_objects = dict([(row[0], row[1]) for row in cursor])

        guid_artifacts = {}
        for k, v in objid_artifacts.items():
            if k in par_obj_objects:
                par_obj_id = par_obj_objects[k]

                # check for artifact parent in files, images, reports
                path = ''
                for artifact_parent_dict in [guid_files, guid_image_names, guid_reports]:
                    if par_obj_id in artifact_parent_dict:
                        path = artifact_parent_dict[par_obj_id]
                        break

                guid_artifacts[k] = "/".join([path, v])

        return TskGuidUtils(
            # aggregate all the object id dictionaries together
            obj_id_guids={**guid_files, **guid_reports, **guid_os_accounts, **guid_vs_parts, **guid_vs_info,
                          **guid_fs_info, **guid_fs_info, **guid_image_names, **guid_artifacts},
            artifact_types=artifact_objid_artifacts)

    artifact_types: Dict[int, str]
    obj_id_guids: Dict[int, any]

    def __init__(self, obj_id_guids: Dict[int, any], artifact_types: Dict[int, str]):
        """
        Main constructor.
        Args:
            obj_id_guids: A dictionary mapping object ids to their guids.
            artifact_types: A dictionary mapping artifact ids to their types.
        """
        self.artifact_types = artifact_types
        self.obj_id_guids = obj_id_guids

    def get_guid_for_objid(self, obj_id, omitted_value: Union[str, None] = 'Object ID Omitted'):
        """
        Returns the guid for the specified object id or returns omitted value if the object id is not found.
        Args:
            obj_id: The object id.
            omitted_value: The value if no object id mapping is found.

        Returns: The relevant guid or the omitted_value.

        """
        return self.obj_id_guids[obj_id] if obj_id in self.obj_id_guids else omitted_value

    def get_guid_for_file_objid(self, obj_id, omitted_value: Union[str, None] = 'Object ID Omitted'):
        # this method is just an alias for get_guid_for_objid
        return self.get_guid_for_objid(obj_id, omitted_value)

    def get_guid_for_accountid(self, account_id, omitted_value: Union[str, None] = 'Account ID Omitted'):
        # this method is just an alias for get_guid_for_objid
        return self.get_guid_for_objid(account_id, omitted_value)

    def get_guid_for_artifactid(self, artifact_id, omitted_value: Union[str, None] = 'Artifact ID Omitted'):
        """
        Returns the guid for the specified artifact id or returns omitted value if the artifact id is not found.
        Args:
            artifact_id: The artifact id.
            omitted_value: The value if no object id mapping is found.

        Returns: The relevant guid or the omitted_value.
        """
        return self.artifact_types[artifact_id] if artifact_id in self.artifact_types else omitted_value


class NormalizeRow:
    """
    Given a dictionary representing a row (i.e. column name mapped to value), returns a normalized representation of
    that row such that the values should be less volatile from run to run.
    """
    row_masker: Callable[[TskGuidUtils, Dict[str, any]], Dict[str, any]]

    def __init__(self, row_masker: Callable[[TskGuidUtils, Dict[str, any]], Union[Dict[str, any], None]]):
        """
        Main constructor.
        Args:
            row_masker: The function to be called to mask the specified row.
        """
        self.row_masker = row_masker

    def normalize(self, guid_util: TskGuidUtils, row: Dict[str, any]) -> Union[Dict[str, any], None]:
        """
        Normalizes a row such that the values should be less volatile from run to run.
        Args:
            guid_util: The TskGuidUtils instance providing guids for volatile ids.
            row: The row values mapping column name to value.

        Returns: The normalized row or None if the row should be ignored.

        """
        return self.row_masker(guid_util, row)


class NormalizeColumns(NormalizeRow):
    """
    Utility for normalizing specific column values of a row so they are not volatile values that will change from run
    to run.
    """

    @classmethod
    def _normalize_col_vals(cls,
                            col_mask: Dict[str, Union[any, Callable[[TskGuidUtils, any], any]]],
                            guid_util: TskGuidUtils,
                            row: Dict[str, any]):
        """
        Normalizes column values for each column rule provided.
        Args:
            col_mask: A dictionary mapping columns to either the replacement value or a function to retrieve the
            replacement value given the TskGuidUtils instance and original value as arguments.
            guid_util: The TskGuidUtil used to provide guids for volatile values.
            row: The dictionary representing the row mapping column names to values.

        Returns: The new row representation.

        """
        row_copy = row.copy()
        for key, val in col_mask.items():
            # only replace values if present in row
            if key in row_copy:
                # if a column replacing function, call with original value
                if isinstance(val, Callable):
                    row_copy[key] = val(guid_util, row[key])
                # otherwise, just replace with mask value
                else:
                    row_copy[key] = val

        return row_copy

    def __init__(self, col_mask: Dict[str, Union[any, Callable[[any], any]]]):
        super().__init__(lambda guid_util, row: NormalizeColumns._normalize_col_vals(col_mask, guid_util, row))


def get_path_segs(path: Union[str, None]) -> Union[List[str], None]:
    """
    Breaks a path string into its folders and filenames.
    Args:
        path: The path string or None.

    Returns: The path segments or None.

    """
    if path:
        # split on backslash or forward slash
        return list(filter(lambda x: len(x.strip()) > 0, [s for s in re.split(r"[\\/]", path)]))
    else:
        return None


def get_filename(path: Union[str, None]) -> Union[str, None]:
    """
    Returns the last segment of a file path.
    Args:
        path: The path.

    Returns: The last segment of the path

    """
    path_segs = get_path_segs(path)
    if path_segs is not None and len(path_segs) > 0:
        return path_segs[-1]
    else:
        return None


def index_of(lst, search_item) -> int:
    """
    Returns the index of the item in the list or -1.
    Args:
        lst: The list.
        search_item: The item to search for.

    Returns: The index in the list of the item or -1.

    """
    for idx, item in enumerate(lst):
        if item == search_item:
            return idx

    return -1


def get_sql_insert_value(val) -> str:
    """
    Returns the value that would appear in a sql insert statement (i.e. string becomes 'string', None becomes NULL)
    Args:
        val: The original value.

    Returns: The sql insert equivalent value.

    """
    if val is None:
        return "NULL"

    if isinstance(val, str):
        escaped_val = val.replace('\n', '\\n').replace("'", "''")
        return f"'{escaped_val}'"

    return str(val)


def get_sqlite_table_columns(conn) -> Dict[str, List[str]]:
    """
    Retrieves a dictionary mapping table names to a list of all the columns for that table
    where the columns are in ordinal value.
    Args:
        conn: The database connection.

    Returns: A dictionary of the form { table_name: [col_name1, col_name2...col_nameN] }

    """
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
    """
    Returns a dictionary mapping table names to the list of their columns in ordinal order.
    Args:
        conn: The pg database connection.

    Returns: The dictionary of tables mapped to a list of their ordinal-orderd column names.
    """
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


def sanitize_schema(original: str) -> str:
    """
    Sanitizes sql script representing table/index creations.
    Args:
        original: The original sql schema creation script.

    Returns: The sanitized schema.
    """
    sanitized_lines = []
    dump_line = ''
    for line in original.splitlines():
        line = line.strip('\r\n ')
        lower_line = line.lower()
        # It's comment or alter statement or catalog entry or set idle entry or empty line
        if (not line or
                line.startswith('--') or
                lower_line.startswith('set') or
                " set default nextval" in lower_line or
                " owner to " in lower_line or
                " owned by " in lower_line or
                "pg_catalog" in lower_line or
                "idle_in_transaction_session_timeout" in lower_line):
            continue

        # if there is no white space or parenthesis delimiter, add a space
        if re.match(r'^.+?[^\s()]$', dump_line) and re.match(r'^[^\s()]', line):
            dump_line += ' '

        # append the line to the outputted line
        dump_line += line

        # if line ends with ';' then this will be one statement in diff
        if line.endswith(';'):
            sanitized_lines.append(dump_line)
            dump_line = ''

    if len(dump_line.strip()) > 0:
        sanitized_lines.append(dump_line)

    return "\n".join(sanitized_lines)


def get_pg_schema(dbname: str, pg_username: str, pg_pword: str, pg_host: str, pg_port: Union[str, int]):
    """
    Gets the schema to be added to the dump text from the postgres database.
    Args:
        dbname: The name of the database.
        pg_username: The postgres user name.
        pg_pword: The postgres password.
        pg_host: The postgres host.
        pg_port: The postgres port.

    Returns: The normalized schema.

    """
    os.environ['PGPASSWORD'] = pg_pword
    pg_dump = ["pg_dump", "-U", pg_username, "-h", pg_host, "-p", str(pg_port),
               "--schema-only", "-d", dbname, "-t", "public.*"]
    output = subprocess.check_output(pg_dump)
    output_str = output.decode('UTF-8')
    return sanitize_schema(output_str)


def get_sqlite_schema(db_conn):
    """
    Gets the schema to be added to the dump text from the sqlite database.
    Args:
        db_conn: The database connection.

    Returns: The normalized schema.

    """
    cursor = db_conn.cursor()
    query = "SELECT sql FROM sqlite_master " \
            "WHERE type IN ('table', 'index') AND sql IS NOT NULL " \
            "ORDER BY type DESC, tbl_name ASC"

    cursor.execute(query)
    schema = '\n'.join([str(row[0]) + ';' for row in cursor])
    return sanitize_schema(schema)


def _mask_event_desc(desc: str) -> str:
    """
    Masks dynamic event descriptions of the form "<artifact_type_name>:<artifact id>" so the artifact id is no longer
    present.
    Args:
        desc: The original description.

    Returns: The normalized description.

    """

    # Takes a string like "Shell Bags: 30840" and replaces with "ShellBags:<artifact_id>"
    match = re.search(r"^\s*(.+?)\s*:\s*\d+\s*$", desc.strip())
    if match:
        return f"{match.group(1)}:<artifact_id>"

    return desc


def normalize_tsk_event_descriptions(guid_util: TskGuidUtils, row: Dict[str, any]) -> Dict[str, any]:
    """
    Normalizes event description rows masking possibly changing column values.
    Args:
        guid_util: Provides guids for ids that may change from run to run.
        row: A dictionary mapping column names to values.

    Returns: The normalized event description row.
    """
    row_copy = row.copy()
    # replace object ids with information that is deterministic
    row_copy['event_description_id'] = MASKED_ID
    row_copy['content_obj_id'] = guid_util.get_guid_for_file_objid(row['content_obj_id'])
    row_copy['artifact_id'] = guid_util.get_guid_for_artifactid(row['artifact_id']) \
        if row['artifact_id'] is not None else None
    row_copy['data_source_obj_id'] = guid_util.get_guid_for_file_objid(row['data_source_obj_id'])

    if row['full_description'] == row['med_description'] == row['short_description']:
        row_copy['full_description'] = _mask_event_desc(row['full_description'])
        row_copy['med_description'] = _mask_event_desc(row['med_description'])
        row_copy['short_description'] = _mask_event_desc(row['short_description'])

    return row_copy


def normalize_ingest_jobs(guid_util: TskGuidUtils, row: Dict[str, any]) -> Dict[str, any]:
    """
    Normalizes ingest jobs table rows.
    Args:
        guid_util: Provides guids for ids that may change from run to run.
        row: A dictionary mapping column names to values.

    Returns: The normalized ingest job row.

    """
    row_copy = row.copy()
    row_copy['host_name'] = "{host_name}"

    start_time = row['start_date_time']
    end_time = row['end_date_time']
    if start_time <= end_time:
        row_copy['start_date_time'] = MASKED_TIME
        row_copy['end_date_time'] = MASKED_TIME

    return row_copy


def normalize_unalloc_files(path_str: Union[str, None]) -> Union[str, None]:
    """
    Normalizes a path string removing timestamps from unalloc files.
    Args:
        path_str: The original path string.

    Returns: The path string where timestamps are removed from unalloc strings.

    """
    # takes a file name like "Unalloc_30580_7466496_2980941312" and removes the object id to become
    # "Unalloc_7466496_2980941312"
    return None if path_str is None else re.sub('Unalloc_[0-9]+_', 'Unalloc_', path_str)


def normalize_regripper_files(path_str: Union[str, None]) -> Union[str, None]:
    """
    Normalizes a path string removing timestamps from regripper files.
    Args:
        path_str: The original path string.

    Returns: The path string where timestamps are removed from regripper paths.

    """
    # takes a file name like "regripper-12345-full" and removes the id to become "regripper-full"
    return None if path_str is None else re.sub(r'regripper-[0-9]+-full', 'regripper-full', path_str)


def normalize_file_path(path_str: Union[str, None]) -> Union[str, None]:
    """
    Normalizes file paths removing or replacing pieces that will change from run to run (i.e. object id)
    Args:
        path_str: The original path string.

    Returns: The normalized path string
    """
    return normalize_unalloc_files(normalize_regripper_files(path_str))


def normalize_tsk_files(guid_util: TskGuidUtils, row: Dict[str, any]) -> Dict[str, any]:
    """
    Normalizes files table rows.
    Args:
        guid_util: Provides guids for ids that may change from run to run.
        row: A dictionary mapping column names to values.

    Returns: The normalized files table row.

    """
    # Ignore TIFF size and hash if extracted from PDFs.
    # See JIRA-6951 for more details.
    row_copy = row.copy()
    if row['extension'] is not None and row['extension'].strip().lower() == 'tif' and \
            row['parent_path'] is not None and row['parent_path'].strip().lower().endswith('.pdf/'):
        row_copy['size'] = "SIZE_IGNORED"
        row_copy['md5'] = "MD5_IGNORED"
        row_copy['sha256'] = "SHA256_IGNORED"

    row_copy['data_source_obj_id'] = guid_util.get_guid_for_file_objid(row['data_source_obj_id'])
    row_copy['obj_id'] = MASKED_OBJ_ID
    row_copy['os_account_obj_id'] = 'MASKED_OS_ACCOUNT_OBJ_ID'
    row_copy['parent_path'] = normalize_file_path(row['parent_path'])
    row_copy['name'] = normalize_file_path(row['name'])
    return row_copy


def normalize_tsk_files_path(guid_util: TskGuidUtils, row: Dict[str, any]) -> Dict[str, any]:
    """
    Normalizes file path table rows.
    Args:
        guid_util: Provides guids for ids that may change from run to run.
        row: A dictionary mapping column names to values.

    Returns: The normalized file path table row.
    """
    row_copy = row.copy()
    path = row['path']
    if path is not None:
        path_parts = get_path_segs(path)
        module_output_idx = index_of(path_parts, 'ModuleOutput')
        if module_output_idx >= 0:
            # remove everything up to and including ModuleOutput if ModuleOutput present
            path_parts = path_parts[module_output_idx:]
            if len(path_parts) > 2 and path_parts[1] == 'EFE':
                # for embedded file extractor, the next folder is the object id and should be omitted
                del path_parts[2]

        row_copy['path'] = os.path.join(*path_parts) if len(path_parts) > 0 else '/'

    row_copy['obj_id'] = guid_util.get_guid_for_file_objid(row['obj_id'])
    return row_copy


def normalize_tsk_objects_path(guid_util: TskGuidUtils, objid: int,
                               no_path_placeholder: Union[str, None]) -> Union[str, None]:
    """
    Returns a normalized path to be used in a tsk_objects table row.
    Args:
        guid_util: The utility for fetching guids.
        objid: The object id of the item.
        no_path_placeholder: text to return if no path value found.

    Returns: The 'no_path_placeholder' text if no path.  Otherwise, the normalized path.

    """
    path = guid_util.get_guid_for_objid(objid, omitted_value=None)

    if path is None:
        return no_path_placeholder
    else:
        # remove host name (for multi-user) and dates/times from path for reports
        path_parts = get_path_segs(path)
        module_output_idx = index_of(path_parts, 'ModuleOutput')
        if module_output_idx >= 0:
            # remove everything up to and including ModuleOutput if ModuleOutput present
            path_parts = path_parts[module_output_idx:]

            if "BulkExtractor" in path_parts or "Smirk" in path_parts:
                # chop off the last folder (which contains a date/time)
                path_parts = path_parts[:-1]

        if path_parts and len(path_parts) >= 2:
            for idx in range(0, len(path_parts) - 1):
                if path_parts[idx].lower() == "reports" and \
                        path_parts[idx + 1].lower().startswith("autopsytestcase html report"):
                    path_parts = ["Reports", "AutopsyTestCase HTML Report"]
                    break

        path = os.path.join(*path_parts) if len(path_parts) > 0 else '/'

        return path


def normalize_tsk_objects(guid_util: TskGuidUtils, row: Dict[str, any]) -> Dict[str, any]:
    """
    Normalizes object table rows.
    Args:
        guid_util: Provides guids for ids that may change from run to run.
        row: A dictionary mapping column names to values.

    Returns: The normalized object table row.
    """
    row_copy = row.copy()
    row_copy['obj_id'] = None if row['obj_id'] is None else \
        normalize_tsk_objects_path(guid_util, row['obj_id'], MASKED_OBJ_ID)

    row_copy['par_obj_id'] = None if row['par_obj_id'] is None else \
        normalize_tsk_objects_path(guid_util, row['par_obj_id'], 'MASKED_PARENT_OBJ_ID')

    return row_copy


MASKED_TIME = "MASKED_TIME"
MASKED_OBJ_ID = "MASKED_OBJ_ID"
MASKED_ID = "MASKED_ID"

IGNORE_TABLE = "IGNORE_TABLE"

TableNormalization = Union[IGNORE_TABLE, NormalizeRow]

"""
This dictionary maps tables where data should be specially handled to how they should be handled.
"""
TABLE_NORMALIZATIONS: Dict[str, TableNormalization] = {
    "blackboard_artifacts": IGNORE_TABLE,
    "blackboard_attributes": IGNORE_TABLE,
    "data_source_info": NormalizeColumns({
        "device_id": "{device id}",
        "added_date_time": "{dateTime}"
    }),
    "image_gallery_groups": NormalizeColumns({
        "group_id": MASKED_ID,
        "data_source_obj_id": lambda guid_util, col: guid_util.get_guid_for_objid(col, omitted_value=None),
    }),
    "image_gallery_groups_seen": IGNORE_TABLE,
    "ingest_jobs": NormalizeRow(normalize_ingest_jobs),
    "reports": NormalizeColumns({
        "obj_id": MASKED_OBJ_ID,
        "path": "AutopsyTestCase",
        "crtime": MASKED_TIME
    }),
    "tsk_aggregate_score": NormalizeColumns({
       "obj_id": lambda guid_util, col: guid_util.get_guid_for_objid(col, omitted_value="Object ID Omitted"),
       "data_source_obj_id": lambda guid_util, col: guid_util.get_guid_for_objid(col, omitted_value="Data Source Object ID Omitted"),
    }),
    "tsk_analysis_results": NormalizeColumns({
        "artifact_obj_id":
            lambda guid_util, col: guid_util.get_guid_for_objid(col, omitted_value="Artifact Object ID Omitted"),
    }),
    "tsk_data_artifacts": NormalizeColumns({
        "artifact_obj_id":
            lambda guid_util, col: guid_util.get_guid_for_file_objid(col, omitted_value="Artifact Object ID Omitted"),
        "os_account_obj_id":
            lambda guid_util, col: guid_util.get_guid_for_file_objid(col, omitted_value="Account Object ID Omitted"),
    }),
    "tsk_event_descriptions": NormalizeRow(normalize_tsk_event_descriptions),
    "tsk_events": NormalizeColumns({
        "event_id": "MASKED_EVENT_ID",
        "event_description_id": 'ID OMITTED'
    }),
    "tsk_examiners": NormalizeColumns({
        "login_name": "{examiner_name}"
    }),
    "tsk_files": NormalizeRow(normalize_tsk_files),
    "tsk_file_layout": NormalizeColumns({
        "obj_id": lambda guid_util, col: guid_util.get_guid_for_file_objid(col)
    }),
    "tsk_files_path": NormalizeRow(normalize_tsk_files_path),
    "tsk_image_names": NormalizeColumns({
       "name": lambda guid_util, col: get_filename(col)
    }),
    "tsk_objects": NormalizeRow(normalize_tsk_objects),
    "tsk_os_account_attributes": NormalizeColumns({
        "id": MASKED_ID,
        "os_account_obj_id": lambda guid_util, col: guid_util.get_guid_for_accountid(col),
        "source_obj_id": lambda guid_util, col: guid_util.get_guid_for_objid(col)
    }),
    "tsk_os_account_instances": NormalizeColumns({
        "id": MASKED_ID,
        "os_account_obj_id": lambda guid_util, col: guid_util.get_guid_for_accountid(col)
    }),
    "tsk_os_accounts": NormalizeColumns({
        "os_account_obj_id": MASKED_OBJ_ID
    }),
    "tsk_vs_parts": NormalizeColumns({
        "obj_id": MASKED_OBJ_ID
    })
}


def write_normalized(guid_utils: TskGuidUtils, output_file, db_conn, table: str, column_names: List[str],
                     normalizer: Union[TableNormalization, None] = None):
    """
    Outputs rows of a file as their normalized values (where values should not change from run to run).
    Args:
        guid_utils: Provides guids to replace values that would potentially change from run to run.
        output_file: The file where the normalized dump will be written.
        db_conn: The database connection.
        table: The name of the table.
        column_names: The name of the columns in the table in ordinal order.
        normalizer: The normalizer (if any) to use so that data is properly normalized.
    """
    if normalizer == IGNORE_TABLE:
        return

    cursor = db_conn.cursor()

    joined_columns = ",".join([col for col in column_names])
    cursor.execute(f"SELECT {joined_columns} FROM {table}")
    for row in cursor:
        if len(row) != len(column_names):
            print(
                f"ERROR: in {table}, number of columns retrieved: {len(row)} but columns are"
                f" {len(column_names)} with {str(column_names)}")
            continue

        row_dict = {}
        for col_idx in range(0, len(column_names)):
            row_dict[column_names[col_idx]] = row[col_idx]

        if normalizer and isinstance(normalizer, NormalizeRow):
            row_masker: NormalizeRow = normalizer
            row_dict = row_masker.normalize(guid_utils, row_dict)

        if row_dict is not None:
            # show row as json-like value
            entries = []
            for column in column_names:
                dict_value = row_dict[column] if column in row_dict and row_dict[column] is not None else None
                value = get_sql_insert_value(dict_value)
                if value is not None:
                    entries.append((column, value))
            insert_values = ", ".join([f"{pr[0]}: {pr[1]}" for pr in entries])
            insert_statement = f"{table}: {{{insert_values}}}\n"
            output_file.write(insert_statement)


def db_connect(db_file, is_multi_user, pg_settings=None):
    if is_multi_user:  # use PostgreSQL
        try:
            return psycopg2.connect("dbname=" + db_file + " user=" + pg_settings.username + " host=" +
                                    pg_settings.pgHost + " password=" + pg_settings.password), None
        except:
            print("Failed to connect to the database: " + db_file)
    else:  # Sqlite
        # Make a copy that we can modify
        backup_db_file = TskDbDiff._get_tmp_file("tsk_backup_db", ".db")
        shutil.copy(db_file, backup_db_file)
        # We sometimes get situations with messed up permissions
        os.chmod(backup_db_file, 0o777)
        return sqlite3.connect(backup_db_file), backup_db_file


def main():
    try:
        sys.argv.pop(0)
        output_db = sys.argv.pop(0)
        gold_db = sys.argv.pop(0)
    except:
        print("usage: tskdbdiff [OUTPUT DB PATH] [GOLD DB PATH]")
        sys.exit(1)

    db_diff = TskDbDiff(output_db, gold_db, output_dir=".")
    dump_passed, bb_dump_passed = db_diff.run_diff()

    if dump_passed and bb_dump_passed:
        print("Database comparison passed.")
    if not dump_passed:
        print("Non blackboard database comparison failed.")
    if not bb_dump_passed:
        print("Blackboard database comparison failed.")

    sys.exit(0)


if __name__ == "__main__":
    if sys.hexversion < 0x03000000:
        print("Python 3 required")
        sys.exit(1)

    main()
