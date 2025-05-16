#-----------------------------------------------------------
# sandbox
#
# Change history:
#  20221024 - created
# 
# Ref:
#  https://learn.microsoft.com/en-us/windows/security/threat-protection/windows-sandbox/windows-sandbox-overview
#  https://admx.help/?Category=Windows_11_2022&Policy=Microsoft.Policies.WindowsSandbox::AllowClipboardRedirection
#
# copyright 2022 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package sandbox;
use strict;

my %config = (hive          => "software",
			  category      => "",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "report",
              version       => 20221024);

sub getConfig{return %config}
sub getShortDescr {
	return "Check Sandbox settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching sandbox v.".$VERSION);
	::rptMsg("sandbox v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  

	my $key_path = ('Policies\\Microsoft\\Windows\\Sandbox');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			foreach my $v (@vals) {
				eval {
					my $x = $key->get_value($v)->get_data();
					::rptMsg(sprintf "%-30s %-4s",$v->get_name(),$x);
				};
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Windows Sandbox provides a lightweight desktop environment to safely run applications in isolation. ");
	::rptMsg("Software installed inside the Windows Sandbox environment remains \"sandboxed\" and runs separately from the host machine.");
	::rptMsg("This plugin retrieves Sandbox environment settings.");
	::rptMsg("");
	::rptMsg("Ref: https://learn.microsoft.com/en-us/windows/security/threat-protection/windows-sandbox/windows-sandbox-overview");
}
1;