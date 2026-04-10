#-----------------------------------------------------------
# LogonStats
#  
# Change history
#  20200925 - MITRE update
#  20200517 - minor updates
#  20180128 - created
#
# References
#  https://twitter.com/jasonshale/status/623081308722475009
# 
# copyright 2020 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package logonstats;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
              category      => "user activity",
			  output		=> "report",
              version       => 20200925);

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
	::rptMsg("logonstats v.".$VERSION); 
  ::rptMsg(getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\LogonStats';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			my $flt = $key->get_value("FirstLogonTime")->get_data();
			my ($i,$g) = ::convertSystemTime($flt);
			::rptMsg("FirstLogonTime                     :  ".$i."Z");
		};
		::rptMsg("FirstLogonTime error: ".$@) if ($@);
		
		eval {
			my $oc = $key->get_value("FirstLogonTimeOnCurrentInstallation")->get_data();
			my ($i,$g) = ::convertSystemTime($oc);
			::rptMsg("FirstLogonTimeOnCurrentInstallation:  ".$i."Z");
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
}


1;