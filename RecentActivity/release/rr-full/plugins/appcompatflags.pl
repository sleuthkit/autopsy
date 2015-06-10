#-----------------------------------------------------------
# appcompatflags.pl
#   Extracts AppCompatFlags for Windows.
#   This is a list of applications configured to run in
#   compatibility mode. Some applications may be configured 
#   to run with elevated privilages (Tested in Vista only) :
#   "ELEVATECREATEPROCESS" "RUNASADMIN" "WINXPSP2 RUNASADMIN"
#
# Change history
#   20130930 - added support for Windows 8 Store key (thanks to
#              Eric Zimmerman for supplying test data)
#   20130905 - added support for both NTUSER.DAT and Software hives;
#              added support for Wow6432Node
#   20130706 - added Persisted key values (H. Carvey)
#   20110830 [fpi] + banner, no change to the version number
#
# References
#   http://msdn.microsoft.com/en-us/library/bb756937.aspx
#
# Copyright (c) 2011-02-04 Brendan Coles <bcoles@gmail.com>
# updated 20130706, H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
# Require #
package appcompatflags;
use strict;

# Declarations #
my %config = (hive          => "NTUSER\.DAT, Software",
              hasShortDescr => 1,
              hasDescr      => 1,
              hasRefs       => 1,
              osmask        => 22,
              category      => "program execution",
              version       => 20130930);
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
	
	my @paths = ("Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers",
	             "Wow6432Node\\Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers",
	             "Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers",
	             "Wow6432Node\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers");
	
	
	
	foreach my $key_path (@paths) {
	# If AppCompatFlags path exists #
		if ($key = $root_key->get_subkey($key_path)) {

		# Return # plugin name, registry key and last modified date #
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
				::rptMsg($key_path." found, has no values.");
			}
		}
		else {
# We're checking several keys in each hive, so if $key_path isn't found,
# don't generate a report 			
#			::rptMsg($key_path." not found.");
		}
	} 
	# Return # obligatory new-line #
	::rptMsg("");
	
# Get all programs for which PCA "came up", for a user, even if no compatibility modes were
# selected	
# Added 20130706 by H. Carvey
	@paths = ("Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Persisted",
	          "Wow6432Node\\Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Persisted",
	          "Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Persisted",
	          "Wow6432Node\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Persisted");
	   
	foreach my $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					::rptMsg("  ".$v->get_name());
				}
			}
			else {
				::rptMsg($key_path." found, has no values\.");
			}
		}
		else {
# As above, don't report on key paths not found			
#			::rptMsg($key_path." not found\.");
		}
	}
	
# Get Store key contents
# selected	
# Added 20130930 by H. Carvey
	@paths = ("Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Store",
	          "Wow6432Node\\Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Store",
	          "Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Store",
	          "Wow6432Node\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Store");
	   
	foreach my $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					
					my ($t0,$t1) = unpack("VV",substr($v->get_data(),0x2C,8));
					my $t = ::getTime($t0,$t1);
					
					::rptMsg("  ".gmtime($t)." - ".$v->get_name());
				}
			}
			else {
				::rptMsg($key_path." found, has no values\.");
			}
		}
		else {
# As above, don't report on key paths not found			
#			::rptMsg($key_path." not found\.");
		}
	}	
}

1;
