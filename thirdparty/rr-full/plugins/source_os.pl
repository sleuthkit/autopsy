#-----------------------------------------------------------
# source_os.pl
#
# History:
#  20220111 - updated with additional keys, etc.
#  20201005 - MITRE update
#  20200511 - update date output format
#  20190829 - added check for CmdLine value
#  20180629 - created
#
# References:
#  http://az4n6.blogspot.com/2017/02/when-windows-lies.html
# 
# 
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package source_os;
use strict;

my %config = (hive          => "System",
			  category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",  
			  output		=> "report",
              version       => 20220111);

sub getConfig{return %config}
sub getShortDescr {
	return "Parse Source OS subkey values";	
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
	::logMsg("Launching source_os v.".$VERSION);
	::rptMsg("source_os v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Setup';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
# https://eddiejackson.net/wp/?p=15847		
		eval {
			my $cmd = $key->get_value("CmdLine")->get_data();	
			if ($cmd ne "") {
				::rptMsg("SetupType: ".$key->get_value("SetupType")->get_data());
				::rptMsg($key_path."\\CmdLine value = ".$cmd);
			}
		};
		
		my @sk = $key->get_list_of_subkeys();
		foreach my $s (@sk) {
			my $name = $s->get_name();
			if (substr($name,0,6) eq "Source") {
				
				my $id = $s->get_value("InstallDate")->get_data();
				
				::rptMsg($name);
				::rptMsg("Last Write time: ".::format8601Date($s->get_timestamp())."Z");
				::rptMsg("  InstallDate: ".::format8601Date($id)."Z");
				
				eval {
					my ($t0,$t1) = unpack("VV",$s->get_value("InstallTime")->get_data());
					my $t = ::getTime($t0,$t1);
					::rptMsg("  InstallTime: ".::format8601Date($t)." Z");
				};
				
				eval {
					::rptMsg("  BuildLab: ".$s->get_value("BuildLab")->get_data());
				};
				
				eval {
					::rptMsg("  CurrentBuild: ".$s->get_value("CurrentBuild")->get_data());
				};
				
				eval {
					::rptMsg("  ProductName: ".$s->get_value("ProductName")->get_data());
				};
				
				eval {
					::rptMsg("  RegisteredOwner: ".$s->get_value("RegisteredOwner")->get_data());
				};
				
				eval {
					::rptMsg("  ReleaseID: ".$s->get_value("ReleaseID")->get_data());
				};
				
				::rptMsg("");
			}
		}
# BuildUpdate subkey (added 20220111)
		if (my $s = $key->get_subkey("BuildUpdate")) {
			::rptMsg("BuildUpdate key");
			::rptMsg("LastWrite time: ".::format8601Date($s->get_timestamp())."Z");
			::rptMsg("");
		}
# Upgrade subkey (added 20220111)
# There may be devices of interest listed beneath 
# Upgrade\PnP\CurrentControlSet\Control\DeviceMigration\Devices\USBStor, SWD\WPDBUSENUM, etc.
# Key LastWrite times may correspond to the Upgrade, but the devices will be listed
		if (my $s = $key->get_subkey("Upgrade")) {
			::rptMsg("Upgrade key");
			::rptMsg("LastWrite time: ".::format8601Date($s->get_timestamp())."Z");
			::rptMsg("");
		}

	}
	else {
		::rptMsg($key_path." not found.");
	}
}


1;