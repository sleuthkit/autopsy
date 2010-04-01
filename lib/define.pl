#
$VER = '2.24';

$HTTP_NL    = "\x0a";
$notes_file = "";

$PICTDIR       = "$INSTALLDIR/pict/";
$SANITIZE_TAG  = 'AutopsySanitized';
$SANITIZE_PICT = 'sanitized.jpg';

$PROGNAME = 'autopsy';

# Default directory names
$MKDIR_MASK = 0775;
$IMGDIR     = 'images';
$DATADIR    = 'output';
$LOGDIR     = 'logs';
$REPDIR     = 'reports';

# Colors
$BACK_COLOR       = "#CCCC99";
$BACK_COLOR_TABLE = "#CCCCCC";
$DEL_COLOR[0]     = "red";
$DEL_COLOR[1] = "#800000";  # used when meta data structure has been reallocated
$NORM_COLOR   = "";
$LINK_COLOR   = "blue";

$YEL_PIX = "pict/back_pix.jpg";

%m2d = (
    "Jan", 1, "Feb", 2, "Mar", 3, "Apr", 4,  "May", 5,  "Jun", 6,
    "Jul", 7, "Aug", 8, "Sep", 9, "Oct", 10, "Nov", 11, "Dec", 12
);
@d2m = (
    "",    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul",
    "Aug", "Sep", "Oct", "Nov", "Dec"
);

1;
