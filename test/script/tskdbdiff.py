import re
import sqlite3
import subprocess
import shutil
import os
import codecs
import datetime
import sys

class TskDbDiff(object):
    """Represents the differences between the gold and output databases.

    Contains methods to compare two databases.

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
    def __init__(self, output_db, gold_db, output_dir=None, gold_bb_dump=None, gold_dump=None, verbose=False):
        """Constructor for TskDbDiff.

        Args:
            output_db_path: a pathto_File, the output database
            gold_db_path: a pathto_File, the gold database
            output_dir: (optional) a pathto_Dir, the location where the generated files will be put.
            gold_bb_dump: (optional) a pathto_File, the location where the gold blackboard dump is located
            gold_dump: (optional) a pathto_File, the location where the gold non-blackboard dump is located
            verbose: (optional) a boolean, should the diff results be printed to stdout?
        """
        self.output_db_file = output_db
        self.gold_db_file = gold_db
        self.output_dir = output_dir
        self.gold_bb_dump = gold_bb_dump
        self.gold_dump = gold_dump
        self._generate_gold_dump = gold_dump is None
        self._generate_gold_bb_dump = gold_bb_dump is None
        self._bb_dump_diff = ""
        self._dump_diff = ""
        self._bb_dump = ""
        self._dump = ""
        self.verbose = verbose

    def run_diff(self):
        """Compare the databases.

        Raises:
            TskDbDiffException: if an error occurs while diffing or dumping the database
        """
        self._init_diff()
        # generate the gold database dumps if necessary
        if self._generate_gold_dump:
            TskDbDiff._dump_output_db_nonbb(self.gold_db_file, self.gold_dump)
        if self._generate_gold_bb_dump:
            TskDbDiff._dump_output_db_bb(self.gold_db_file, self.gold_bb_dump)

        # generate the output database dumps
        TskDbDiff.dump_output_db(self.output_db_file, self._dump, self._bb_dump)

        dump_diff_pass = self._diff(self._dump, self.gold_dump, self._dump_diff)
        bb_dump_diff_pass = self._diff(self._bb_dump, self.gold_bb_dump, self._bb_dump_diff)

        self._cleanup_diff()
        return dump_diff_pass, bb_dump_diff_pass

    def _init_diff(self):
        """Set up the necessary files based on the arguments given at construction"""
        if self.output_dir is None:
            # No stored files
            self._bb_dump = TskDbDiff._get_tmp_file("SortedData", ".txt")
            self._bb_dump_diff = TskDbDiff._get_tmp_file("SortedData-Diff", ".txt")
            self._dump = TskDbDiff._get_tmp_file("DBDump", ".txt")
            self._dump_diff = TskDbDiff._get_tmp_file("DBDump-Diff", ".txt")
        else:
            self._bb_dump = os.path.join(self.output_dir, "SortedData.txt")
            self._bb_dump_diff = os.path.join(self.output_dir, "SortedData-Diff.txt")
            self._dump = os.path.join(self.output_dir, "DBDump.txt")
            self._dump_diff = os.path.join(self.output_dir, "DBDump-Diff.txt")

        if self.gold_bb_dump is None:
            self.gold_bb_dump = TskDbDiff._get_tmp_file("GoldSortedData", ".txt")
            self.gold_dump = TskDbDiff._get_tmp_file("GoldDBDump", ".txt")

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
            output_file: a pathto_File, the output text file
            gold_file: a pathto_File, the input text file
        """
        if(not os.path.isfile(output_file)):
            return False
        output_data = codecs.open(output_file, "r", "utf_8").read()
        gold_data = codecs.open(gold_file, "r", "utf_8").read()

        if (not(gold_data == output_data)):
            diff_file = codecs.open(diff_path, "wb", "utf_8")
            dffcmdlst = ["diff", output_file, gold_file]
            subprocess.call(dffcmdlst, stdout = diff_file)
            return False
        else:
            return True

    def _dump_output_db_bb(db_file, bb_dump_file):
        """Dumps sorted text results to the given output location.

        Smart method that deals with a blackboard comparison to avoid issues
        with different IDs based on when artifacts were created.

        Args:
            db_file: a pathto_File, the output database.
            bb_dump_file: a pathto_File, the sorted dump file to write to
        """
        unsorted_dump = TskDbDiff._get_tmp_file("dump_data", ".txt")
        conn = sqlite3.connect(db_file)
        conn.text_factory = lambda x: x.decode("utf-8", "ignore")
        conn.row_factory = sqlite3.Row
        artifact_cursor = conn.cursor()
        # Get the list of all artifacts
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
                if(row["parent_path"] != None):
                    database_log.write(row["parent_path"] + row["name"] + ' <artifact type="' + row["display_name"] + '" > ')
                else:
                    database_log.write(row["name"] + ' <artifact type="' + row["display_name"] + '" > ')

                # Get attributes for this artifact
                attribute_cursor = conn.cursor()
                looptry = True
                artifact_count += 1
                try:
                    art_id = ""
                    art_id = str(row["artifact_id"])
                    attribute_cursor.execute("SELECT blackboard_attributes.source, blackboard_attribute_types.display_name, blackboard_attributes.value_type, blackboard_attributes.value_text, blackboard_attributes.value_int32, blackboard_attributes.value_int64, blackboard_attributes.value_double FROM blackboard_attributes INNER JOIN blackboard_attribute_types ON blackboard_attributes.attribute_type_id = blackboard_attribute_types.attribute_type_id WHERE artifact_id =? ORDER BY blackboard_attributes.source, blackboard_attribute_types.display_name, blackboard_attributes.value_type, blackboard_attributes.value_text, blackboard_attributes.value_int32, blackboard_attributes.value_int64, blackboard_attributes.value_double", [art_id])
                    attributes = attribute_cursor.fetchall()
                except sqlite3.Error as e:
                    msg = "Attributes in artifact id (in output DB)# " + str(row["artifact_id"]) + " encountered an error: " + str(e) +" .\n"
                    print("Attributes in artifact id (in output DB)# ", str(row["artifact_id"]), " encountered an error: ", str(e))
                    print() 
                    looptry = False
                    artifact_fail += 1
                    database_log.write('Error Extracting Attributes')
                    database_log.close()
                    raise TskDbDiffException(msg)

                # Print attributes
                if(looptry == True):
                    src = attributes[0][0]
                    for attr in attributes:
                        attr_value_index = 3 + attr["value_type"]
                        numvals = 0
                        for x in range(3, 6):
                            if(attr[x] != None):
                                numvals += 1
                        if(numvals > 1):
                            msg = "There were too many values for attribute type: " + attr["display_name"] + " for artifact with id #" + str(row["artifact_id"]) + ".\n"
                        if(not attr["source"] == src):
                            msg = "There were inconsistent sources for artifact with id #" + str(row["artifact_id"]) + ".\n"
                        try:
                            attr_value_as_string = str(attr[attr_value_index])
                            #if((type(attr_value_as_string) != 'unicode') or (type(attr_value_as_string) != 'str')):
                            #    attr_value_as_string = str(attr_value_as_string)
                            patrn = re.compile("[\n\0\a\b\r\f]")
                            attr_value_as_string = re.sub(patrn, ' ', attr_value_as_string)
                            database_log.write('<attribute source="' + attr["source"] + '" type="' + attr["display_name"] + '" value="' + attr_value_as_string + '" />')
                        except IOError as e:
                            print("IO error")
                            raise TskDbDiffException("Unexpected IO error while writing to database log." + str(e))
                
                database_log.write(' <artifact/>\n')
                row = artifact_cursor.fetchone()

            print(artifact_fail)
            if(artifact_fail > 0):
                msg ="There were " + str(artifact_count) + " artifacts and " + str(artifact_fail) + " threw an exception while loading.\n"
        except Exception as e:
            raise TskDbDiffException("Unexpected error while dumping blackboard database: " + str(e))
        finally:
            database_log.close()
        
        # Now sort the file
        srtcmdlst = ["sort", unsorted_dump, "-o", bb_dump_file]
        subprocess.call(srtcmdlst)

    def _dump_output_db_nonbb(db_file, dump_file):
        """Dumps a database to a text file.

        Does not dump the artifact and attributes.

        Args:
            db_file: a pathto_File, the database file to dump
            dump_file: a pathto_File, the location to dump the non-blackboard database items
        """
        backup_db_file = TskDbDiff._get_tmp_file("tsk_backup_db", ".db")
        shutil.copy(db_file, backup_db_file)
        conn = sqlite3.connect(backup_db_file)
        conn.text_factory = lambda x: x.decode("utf-8", "ignore")
        # Delete the blackboard tables
        conn.execute("DROP TABLE blackboard_artifacts")
        conn.execute("DROP TABLE blackboard_attributes")

        # Write to the database dump
        with codecs.open(dump_file, "wb", "utf_8") as db_log:
            for line in conn.iterdump():
                db_log.write('%s\n' % line)

        # cleanup the backup
        os.remove(backup_db_file)

    def dump_output_db(db_file, dump_file, bb_dump_file):
        """Dumps the given database to text files for later comparison.

        Args:
            db_file: a pathto_File, the database file to dump
            dump_file: a pathto_File, the location to dump the non-blackboard database items
            bb_dump_file: a pathto_File, the location to dump the blackboard database items
        """
        TskDbDiff._dump_output_db_nonbb(db_file, dump_file)
        TskDbDiff._dump_output_db_bb(db_file, bb_dump_file)

    def _get_tmp_file(base, ext):
        time = datetime.datetime.now().time().strftime("%H%M%f")
        return os.path.join(os.environ['TMP'], base + time + ext)


class TskDbDiffException(Exception):
    pass


def main():
    try:
        sys.argv.pop(0)
        output_db = sys.argv.pop(0)
        gold_db = sys.argv.pop(0)
    except:
        print("usage: tskdbdiff [OUPUT DB PATH] [GOLD DB PATH]")
        sys.exit()

    db_diff = TskDbDiff(output_db, gold_db, output_dir=".")
    dump_passed, bb_dump_passed = db_diff.run_diff()

    if dump_passed and bb_dump_passed:
        print("Database comparison passed.")
    if not dump_passed:
        print("Non blackboard database comparison failed.")
    if not bb_dump_passed:
        print("Blackboard database comparison failed.")

    return 0


if __name__ == "__main__":
    sys.exit(main())

