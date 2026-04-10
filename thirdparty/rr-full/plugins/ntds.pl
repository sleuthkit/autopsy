#-----------------------------------------------------------
# ntds.pl
#
# History:
#  20200921 - MITRE update
#  20200427 - updated output date format
#  20191016 - created
#
# References:
#  https://blog.xpnsec.com/exploring-mimikatz-part-1/
#  http://redplait.blogspot.com/2015/02/lsasrvdlllsaploadlsadbextensiondll.html
#  https://attack.mitre.org/techniques/T1547/008/
# 
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package ntds;
use strict;

my %config = (hive          => "System",
			  hivemask      => 4,
			  output        => "report",
			  category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1547\.008",  
              version       => 20200921);

sub getConfig{return %config}
sub getShortDescr {
	return "Parse Services NTDS key for specific persistence values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my $str = "";

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching ntds v.".$VERSION);
	::rptMsg("ntds v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my ($current,$ccs);
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		
		my $ntds_path = $ccs."\\Services\\NTDS";
		
		if (my $ntds = $key->get_subkey($ntds_path)) {
			::rptMsg("LastWrite Time: ".::format8601Date($ntds->get_timestamp())."Z");
			eval {
				my $lsa = $ntds->get_value("LsaDbExtPt")->get_data();
				::rptMsg("LsaDbExtPt value: ".$lsa);
			};
			
			eval {
				my $dir = $ntds->get_value("DirectoryServiceExtPt")->get_data();
				::rptMsg("DirectoryServiceExtPt value: ".$dir);
			};
		}
		else {
			::rptMsg($ntds_path." not found.");
		}
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;