#-----------------------------------------------------------
# appx_tln.pl
# Checks for persistence via Universal Windows Platform Apps (see ref)
#
# Change history
#   20200904 - MITRE updates
#	  20191014 - created
#
# References
#   https://oddvar.moe/2018/09/06/persistence-using-universal-windows-platform-apps-appx/
#   
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package appx_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT, USRCLASS\.DAT",
              category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output 		=> "tln",
              version       => 20200904);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks for persistence via Universal Windows Platform Apps";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
#	::logMsg("Launching appx_tln v.".$VERSION);
#	::rptMsg("appx_tln v.".$VERSION); 
#  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

# NTUSER.DAT Checks	
	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\PackagedAppXDebug";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $sk (@subkeys) {			
				eval {
					my $def = $sk->get_value("")->get_data();
					my $name = $sk->get_name();
					my $lw   = $sk->get_timestamp();
					::rptMsg($lw."|REG|||NTUSER ".$key_path."\\".$name."  Default value: ".$def);
				};
			}
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}

# USRCLASS.DAT Checks
	my $key_path = "ActivatableClasses\\Package";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @sk1 = $key->get_list_of_subkeys();
		if (scalar @sk1 > 0) {
			foreach my $s1 (@sk1) {
				my $s1_name = $s1->get_name();
				my $key_path2 = $s1_name."\\DebugInformation";
				if (my $key2 = $key->get_subkey($key_path2)) {
					my @sk2 = $key2->get_list_of_subkeys();
					if (scalar @sk2 > 0) {
						foreach my $s2 (@sk2) {
							eval {
								my $debug = $s2->get_value("DebugPath")->get_data();
								my $name  = $s2->get_name();
								my $lw    = $s2->get_timestamp();
								::rptMsg($lw."|REG|||USRCLASS ".$key_path."\\".$key_path2."\\".$name."  DebugPath value: ".$debug);
							};
						}
					}
				}
				else {
#					::rptMsg($key_path."\\".$key_path2." not found.");	
				}
			}
		}
	}
}

1;