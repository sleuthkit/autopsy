#-----------------------------------------------------------
# duo
# 
#
#
# Change history:
#  20220927 - created
# 
# Ref:
#  https://www.mandiant.com/resources/blog/abusing-duo-authentication-misconfigurations
#
# copyright 2022 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package duo;
use strict;

my %config = (hive          => "software",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1562\.001",
              version       => 20220927);

sub getConfig{return %config}
sub getShortDescr {
	return "Get DUO config";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching duo v.".$VERSION);
	::rptMsg("duo v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my @paths = ('Duo Security\\DuoCredProv',
	             'Policies\\Duo Security\\DuoCredProv');

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");
			
			my @vals = $key->get_list_of_values();
			if (scalar @vals > 0) {
				foreach my $v (@vals) {
					::rptMsg(sprintf "%-20s %-2s",$v->get_name(),$v->get_data());
				}
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Users with admin privileges can modify the DUO config settings.");
#	::rptMsg("");
	::rptMsg("Ex: FailOpen = 1 tells the system to fail open if DUO is offline");
	::rptMsg("");
	::rptMsg("Ref: https://www.mandiant.com/resources/blog/abusing-duo-authentication-misconfigurations");
}
1;