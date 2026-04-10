#-----------------------------------------------------------
# activesetup.pl
# Get Active Setup StubPath values
#
# Change history:
#   20201230 - Near-complete overhaul of installedcomp.pl plugin
#
# References:
#   https://twitter.com/pabraeken/status/990717080805789697
#   https://helgeklein.com/blog/2010/04/active-setup-explained/
#   http://www.microsoft.com/security/portal/threat/encyclopedia/entry.aspx?Name=Backdoor%3AWin32%2FBifrose.ACI#tab=2
#   
#        
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, 2013
#-----------------------------------------------------------
package activesetup;
use strict;

my %config = (hive          => "software, ntuser\.dat",
			  category      => "persistence",
			  MITRE         => "T1547",
              osmask        => 22,
			  output        => "report",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20201230);

sub getConfig{return %config}

sub getShortDescr {
	return "Get Active Setup StubPath values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %comp;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching activesetup v.".$VERSION);
	::rptMsg("activesetup v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my @paths = ("Microsoft\\Active Setup\\Installed Components",
	             "Wow6432Node\\Microsoft\\Active Setup\\Installed Components",
	             "Software\\Microsoft\\Active Setup\\Installed Components",
	             "Software\\Wow6432Node\\Microsoft\\Active Setup\\Installed Components",);
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");
			::rptMsg("Key path: ".$key_path);
			::rptMsg("");
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar(@subkeys) > 0) {
				foreach my $s (@subkeys) {
					my $name = $s->get_name();
# If a Default value exists, use it as the name; otherwise, use the key name/GUID
					eval {
						my $id = $s->get_value("")->get_data();
						$name = $id;
					};
					
					my $stub = ();
					eval {
						$stub = $s->get_value("StubPath")->get_data();
					};
					
					my $is = ();
					eval {
						$is = $s->get_value("IsInstalled")->get_data();
# No IsInstalled value is the same as IsInstalled = 1; what we're interested in here
# is IsInstalled = 0						
					};
				
					if ($stub) {
						::rptMsg("Name          : ".$name);
						::rptMsg("LastWrite time: ".::format8601Date($s->get_timestamp())."Z");
						::rptMsg("StubPath      : ".$stub);
						::rptMsg("IsInstalled   : ".$is);
						::rptMsg("");
					}
				}
			}
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("Analysis Tip: The Active Setup key defines processes that are run synchronously prior to the Run & RunOnce keys, and");
	::rptMsg("prior to the Desktop appearing\. For users, logon in blocked while commands are executing.");
	::rptMsg("Ref: https://helgeklein.com/blog/2010/04/active-setup-explained/");
}
1;