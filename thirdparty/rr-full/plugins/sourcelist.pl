#-----------------------------------------------------------
# sourcelist
#
# Change history:
#  20221031 - created
# 
# Ref:
#  https://twitter.com/SBousseaden/status/1586862562624299010
#
# copyright 2022 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package sourcelist;
use strict;

my %config = (hive          => "ntuser\.dat",
			  category      => "execution",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1204\.002",
			  output		=> "report",
              version       => 20221031);

sub getConfig{return %config}
sub getShortDescr {
	return "Get media source for product installs";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching sourcelist v.".$config{version});
	::rptMsg("sourcelist v.".$config{version}); 
	::rptMsg("(".$config{hive}.") ".getShortDescr())
#	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
#	::rptMsg("");
	
	my $key_path = ('Software\\Microsoft\\Installer\\Products');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
#		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
#		::rptMsg("");	
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $sk (@subkeys) {
				
				eval {
					my $p = $sk->get_value("ProductName")->get_data();
					::rptMsg("ProductName: ".$p);
				};
				
				eval {
					my $p = $sk->get_subkey("SourceList")->get_value("PackageName")->get_data();
					::rptMsg("  PackageName       : ".$p);
				};
				
				eval {
					my $m = $sk->get_subkey("SourceList\\Media")->get_value("1")->get_data();
					::rptMsg("  SourceList\\Media\\1: ".$m);
				
				};
				
				eval {
					my $m = $sk->get_subkey("SourceList\\Net")->get_value("1")->get_data();
					::rptMsg("  SourceList\\Net\\1  : ".$m);
				
				};
				
				::rptMsg("");
			}
		}	
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;