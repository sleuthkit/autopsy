#-----------------------------------------------------------
# vawtrak.pl
# 
#
# Change history
#  20131010 - created
#
# References
#  http://www.microsoft.com/security/portal/threat/encyclopedia/entry.aspx?Name=Backdoor:Win32/Vawtrak.A#tab=2
# 
# copyright 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package vawtrak;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              category      => "malware",
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20131010);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks for possible VawTrak infection";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching vawtrak v.".$VERSION);
	::rptMsg("vawtrak v.".$VERSION); # banner
    ::rptMsg(getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	my $count = 0;
	my $key_path;
	
	my @paths = ('Software\\Microsoft\\Windows\\CurrentVersion\\Run',
	             'Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Run');
	my $key;
	
	foreach $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					my $name = $v->get_name();
					my $data = $v->get_data();
					my $lcdata = $data;
					$lcdata =~ tr/[A-Z]/[a-z]/;
					if ($lcdata =~ m/^regsvr32/ && $lcdata =~ m/\.dat$/) {
						::rptMsg("Possible Vawtrak infection: ".$name." - ".$data);
						$count++;
					}
				}
			}
			else {
				::rptMsg($key_path." has no values\.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
		::rptMsg("");
	}
	
	$key_path = 'Software\\Microsoft\\Internet Explorer\\Main';
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			my $banner = $key->get_value("NoProtectedModeBanner")->get_data();
			::rptMsg($key_path."\\NoProtectedModeBanner value = ".$banner);
			::rptMsg("");
			if ($banner == 1) {
				::rptMsg("Internet Explorer\\Main\\NoProtectedModeBanner set to 0x1: possible Vawtrak infection\.");
				$count++;
				::rptMsg("");
			}
		};
		
		eval {
			my $tab = $key->get_value("TabProcGrowth")->get_data();
			::rptMsg($key_path."\\TabProcGrowth value = ".$tab);
			::rptMsg("");
			if ($tab == 0) {
				::rptMsg("Internet Explorer\\Main\\TabProcGrowth value set to 0x0: possible VawTrak infection\.n");
				$count++;
				::rptMsg("");
			}
		};
		
	}
	else {
		::rptMsg($key_path." not found\.");
	}
	
	$key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\\Zones\\3';
	if ($key = $root_key->get_subkey($key_path)) {
		eval {
			my $val = $key->get_value("2500")->get_data();
			::rptMsg($key_path."\\2500 value = ".$val);
			::rptMsg("");
			if ($val == 0x3) {
				::rptMsg("Internet Settings\\Zones\\3\\2500 value is set to 0x3: possible Vawtrak infection\.");
				::rptMsg("");
				$count++;
			}
		};
	}
	else {
		::rptMsg($key_path." not found\.");
	}
	::rptMsg("Final Score: ".$count."/4 checks succeeded\.");
}

1;