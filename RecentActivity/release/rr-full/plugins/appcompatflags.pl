#-----------------------------------------------------------
# appcompatflags.pl
#   Extracts AppCompatFlags for Windows.
#   This is a list of applications configured to run in
#   compatibility mode. Some applications may be configured 
#   to run with elevated privilages (Tested in Vista only) :
#   "ELEVATECREATEPROCESS" "RUNASADMIN" "WINXPSP2 RUNASADMIN"
#
# Change history
#   20110830 [fpi] + banner, no change to the version number
#
# References
#   http://msdn.microsoft.com/en-us/library/bb756937.aspx
#
# Copyright (c) 2011-02-04 Brendan Coles <bcoles@gmail.com>
#-----------------------------------------------------------
# Require #
package appcompatflags;
use strict;

# Declarations #
my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 1,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20110204);
my $VERSION = getVersion();

# Functions #
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {
	return "Extracts AppCompatFlags for Windows. This is a list".
	       " of applications configured to run in compatibility".
	       " mode. Some applications may be configured to run".
	       " with elevated privilages (Tested in Vista only) :".
	       '"ELEVATECREATEPROCESS" "RUNASADMIN" "WINXPSP2 RUNASADMIN"';
}
sub getShortDescr {
	return "Extracts AppCompatFlags for Windows.";
}
sub getRefs {
	my %refs = ("Application Compatibility: Program Compatibility Assistant" =>
	            "http://msdn.microsoft.com/en-us/library/bb756937.aspx");
	return %refs;	
}

############################################################
# pluginmain #
############################################################
sub pluginmain {

	# Declarations #
	my $class = shift;
	my $hive = shift;

	# Initialize #
	::logMsg("Launching appcompatflags v.".$VERSION);
    ::rptMsg("appcompatflags v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # 20110830 [fpi] + banner     
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers";

	# If # AppCompatFlags path exists #
	if ($key = $root_key->get_subkey($key_path)) {

		# Return # plugin name, registry key and last modified date #
		::rptMsg("AppCompatFlags");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		# Extract # all keys from AppCompatFlags registry path #
		my @vals = $key->get_list_of_values();

		# If # registry keys exist in path #
		if (scalar(@vals) > 0) {

			# Extract # all key names+values for AppCompatFlags registry path #
			foreach my $v (@vals) {
				::rptMsg($v->get_name()." -> ".$v->get_data());
			}

		# Error # key value is null #
		} else {
			::rptMsg($key_path." has no values.");
		}

	# Error # AppCompatFlags isn't here, try another castle #
	} else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}

	# Return # obligatory new-line #
	::rptMsg("");
}

# Error # oh snap! #
1;
