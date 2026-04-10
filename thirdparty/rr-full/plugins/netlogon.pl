#-----------------------------------------------------------
# netlogon.pl
# 
#
# History:
#  20200921 - MITRE update
#  20200724 - minor updates
#  20200515 - minor updates
#  20190223 - created
#
# References:
#  https://support.microsoft.com/en-us/help/154501/how-to-disable-automatic-machine-account-password-changes
#  http://malwarejake.blogspot.com/2015/11/kerberos-silver-tickets-unique-attacker.html
#  https://attack.mitre.org/techniques/T1558/002/
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package netlogon;
use strict;

my %config = (hive          => "system",
			  hivemask      => 4,
			  output        => "report",
			  category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1558\.002",  
              version       => 20200921);

sub getConfig{return %config}
sub getShortDescr {
	return "Parse values for machine account password changes";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my @vals;
my $name;
my $data;
my $type;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching netlogon v.".$VERSION);
	::rptMsg("netlogon v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my @sets = ();
	
	my @subkeys = ();
	if (@subkeys = $root_key->get_list_of_subkeys()) {
		foreach my $s (@subkeys) {
			my $name = $s->get_name();
			push(@sets,$name) if ($name =~ m/^ControlSet/);
		}
	}
	
	my $set;
	foreach $set (@sets) {
		::rptMsg("*** ".$set." ***");
		my $key_path = $set."\\services\\NetLogon\\Parameters";
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("LastWrite Time: ".::format8601Date($key->get_timestamp())."Z");
			@vals = $key->get_list_of_values();
			if (scalar @vals > 0) {
				foreach my $v (@vals) {
					$name = $v->get_name();
					$data = $v->get_data();
					$type = $v->get_type();
					if ($type == 4) {
						::rptMsg(sprintf "%-35s  0x%04x",$name,$data);
					}
					else {
						::rptMsg(sprintf "%-35s  $data",$name);
					}
				}
			}
			else {
# no values				
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
		::rptMsg("");
	}
	::rptMsg("Analysis Note: If \"DisablePasswordChange\" is set to 0x1, this may indicate a silver ticket attack\.");
	::rptMsg("Also, searching for this value across the enterprise can be useful in threat hunting.");
}

1;
