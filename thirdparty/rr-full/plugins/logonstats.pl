#-----------------------------------------------------------
# LogonStats
#  
# Change history
#  20180128 - created
#
# References
#  https://twitter.com/jasonshale/status/623081308722475009
# 
# copyright 2018 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package logonstats;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20180128);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's LogonStats key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching logonstats v.".$VERSION);
	::rptMsg("logonstats v.".$VERSION); # banner
  ::rptMsg("- ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\LogonStats';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			my $flt = $key->get_value("FirstLogonTime")->get_data();
			my $str = convertSystemTime($flt);
			::rptMsg("FirstLogonTime:                       ".$str);
		};
		
		eval {
			my $oc = $key->get_value("FirstLogonTimeOnCurrentInstallation")->get_data();
			my $i = convertSystemTime($oc);
			::rptMsg("FirstLogonTimeOnCurrentInstallation:  ".$i);
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
}



#-----------------------------------------------------------
# convertSystemTime()
# Converts 128-bit SYSTEMTIME object to readable format
#-----------------------------------------------------------
sub convertSystemTime {
	my $date = $_[0];
	my @months = ("Jan","Feb","Mar","Apr","May","Jun","Jul",
	              "Aug","Sep","Oct","Nov","Dec");
	my @days = ("Sun","Mon","Tue","Wed","Thu","Fri","Sat");
	my ($yr,$mon,$dow,$dom,$hr,$min,$sec,$ms) = unpack("v*",$date);
	$hr = "0".$hr if ($hr < 10);
	$min = "0".$min if ($min < 10);
	$sec = "0".$sec if ($sec < 10);
	my $str = $days[$dow]." ".$months[$mon - 1]." ".$dom." ".$hr.":".$min.":".$sec." ".$yr;
	return $str;
}

1;