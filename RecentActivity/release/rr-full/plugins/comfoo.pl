#-----------------------------------------------------------
#comfoo
#   
#
# Change history
#   20131007 - created
#
#
# References
#   http://www.secureworks.com/cyber-threat-intelligence/threats/secrets-of-the-comfoo-masters/
#
# copyright 2013 QAR, LLC 
#-----------------------------------------------------------
package comfoo;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              category      => "malware",
              version       => 20131007);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks known Comfoo values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching comfoo v.".$VERSION);
  ::rptMsg("comfoo v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	
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
		
# Check the services listed in the SecureWorks post/reference
		my @svcs = ("NetMan", "SENS", "RasAuto");		
		my ($cf_path,$cf);
		
		foreach my $s (@svcs) {
			$cf_path = $ccs."\\Services\\".$s;
			$cf;
			if ($cf = $root_key->get_subkey($cf_path)) {
				::rptMsg($cf_path);
				::rptMsg("LastWrite Time ".gmtime($cf->get_subkey("Parameters")->get_timestamp())." (UTC)");
#				::rptMsg("");
			
				eval {
					my $start = $cf->get_value("Start")->get_data();
					if ($start != 0x03 && $s ne "SENS") {
						::rptMsg("Start value = ".$start);
						::rptMsg("Comfoo malware is known to change the Start value from 3 to 2");
						::rptMsg("");
					}
				};
			
				eval {
					my $dllname = $s."\.dll";
					$dllname =~ tr/[A-Z]/[a-z]/;
					my $dll = $cf->get_subkey("Parameters")->get_value("ServiceDll")->get_data();
					::rptMsg("ServiceDll value : ".$dll);
					::rptMsg("Should be/include: ".$dllname);
				};
			}
			::rptMsg("");
		}
		::rptMsg("Analysis Tip: Comfoo malware is known to change the ServiceDll value to point");
		::rptMsg("to something other than the normal value");
	}
}

1;