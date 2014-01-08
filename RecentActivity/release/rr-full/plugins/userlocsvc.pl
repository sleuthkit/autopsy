#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# userlocsvc.pl
# Get the contents of the Microsoft\User Location Service\Clients key
# from the user's hive 
#
# Ref:
#  http://support.microsoft.com/kb/196301
#  
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package userlocsvc;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20090411);

sub getConfig{return %config}
sub getShortDescr {
	return "Displays contents of User Location Service\\Client key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching userlocsvc v.".$VERSION);
	::rptMsg("userlocsvc v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	my $key_path = 'Software\\Microsoft\\User Location Service\\Client';
	my $key;
	my %ua;
	my $hrzr = "HRZR";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $str = sprintf "%-15s %-30s",$v->get_name(),$v->get_data();
				::rptMsg($str) if ($v->get_type() == 1);
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