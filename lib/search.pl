#
# This file contains pre-defined search strings.  A button for each will
# be displayed in the Search Mode.
#
# The $auto_srch{} hash is filled in with the search string
# The index into the hash is the name of the search.
#
# For example, $auto_srch{'foo'} = "bar"; would search for the string
# bar
#
# If the search is case sensitive, then set $auto_srch_csense to 1 (this
# is the default value if not specified.  Set to 0 for insensitive
#
# If the search is a regular expression, set $auto_srch_reg to 1, else
# 0 (the default)
#
#
# If you develop patterns that you think will be useful to others, email
# them to me and I will include them in the next version (carrier@sleuthkit.org)
#

# Date / syslog search of month and date
$auto_srch{'Date'} =
"((jan)|(feb)|(mar)|(apr)|(may)|(june?)|(july?)|(aug)|(sept?)|(oct)|(nov)|(dec))([[:space:]]+[[:digit:]])?";
$auto_srch_reg{'Date'}    = 1;
$auto_srch_csense{'Date'} = 0;

# IP Address
$auto_srch{'IP'} =
'[0-2]?[[:digit:]]{1,2}\.[0-2]?[[:digit:]]{1,2}\.[0-2]?[[:digit:]]{1,2}\.[0-2]?[[:digit:]]{1,2}';
$auto_srch_reg{'IP'}    = 1;
$auto_srch_csense{'IP'} = 0;

# SSN in the pattern of 123-12-1234 - from Jerry Shenk
$auto_srch{'SSN1'}        = '[0-9][0-9][0-9]\-[0-9]]0-9]\-[0-9][0-9][0-9][0-9]';
$auto_srch_reg{'SSN1'}    = 1;
$auto_srch_csense{'SSN1'} = 0;

# SSN in the pattern of 123121234 - from Jerry Shenk
$auto_srch{'SSN2'}        = '[0-9][0-9][0-9][0-9]]0-9][0-9][0-9][0-9][0-9]';
$auto_srch_reg{'SSN2'}    = 1;
$auto_srch_csense{'SSN2'} = 0;

# CC # - from Jerry Shenk
$auto_srch{'CC'} =
  '[0-9][0-9][0-9][0-9]]0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]]0-9]';
$auto_srch_reg{'CC'}    = 1;
$auto_srch_csense{'CC'} = 0;

# This must be the last value
1;
