#-----------------------------------------------------------
# javafx.pl
# Plugin written based on Cory Harrell's Exploit Artifacts posts at
# http://journeyintoir.blogspot.com/
#
# Change history
#   20110322 - created
#
# References
#   http://java.sun.com/j2se/1.4.2/runtime_win32.html
#		
# copyright 2011 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package javafx;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20110322);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's JavaFX key";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching javafx v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $key_path = "Software\\JavaSoft\\Java Update\\Policy\\JavaFX";
	my $key;
	my @vals;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("javafx v.".$VERSION);
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".gmtime($key->get_timestamp()));
		::rptMsg("");
		@vals = $key->get_list_of_values();
		
		if (scalar(@vals) > 0) {
# First, read in all of the values and the data
			foreach my $v (@vals) {
				::rptMsg(sprintf "%-25s %-20s",$v->get_name(), $v->get_data());
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}	

1;		