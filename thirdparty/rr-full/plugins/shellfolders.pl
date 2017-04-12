#-----------------------------------------------------------
# shellfolders.pl
#
# Retrieve the Shell Folders values from user's hive; while 
# this may not be important in every instance, it may give the
# examiner indications as to where to look for certain items;
# for example, if the user's "My Documents" folder has been redirected
# as part of configuration changes (corporate policies, etc.).  Also,
# this may be important as part of data leakage exams, as XP and Vista
# allow users to drop and drag files to the CD Burner.
#
# References:
#   http://support.microsoft.com/kb/279157
#   http://support.microsoft.com/kb/326982
#
# copyright 2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package shellfolders;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20090115);

sub getConfig{return %config}

sub getShortDescr {
	return "Retrieve user Shell Folders values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching shellfolders v.".$VERSION);
	::rptMsg("shellfolders v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		my @vals = $key->get_list_of_values();
		
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $str = sprintf "%-20s %-40s",$v->get_name(),$v->get_data();
				::rptMsg($str);
			}
			::rptMsg("");
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;