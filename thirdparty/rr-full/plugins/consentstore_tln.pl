#-----------------------------------------------------------
# consentstore_tln
#
# Change history:
#  20200904 - MITRE updates
#  20200608 - created
# 
# Ref:
#  https://medium.com/@7a616368/can-you-track-processes-accessing-the-camera-and-microphone-7e6885b37072
#  https://dfir.pubpub.org/pub/nm5b39ae/release/1
#
# copyright 2020 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package consentstore_tln;
use strict;

my %config = (hive          => "Software, NTUSER\.DAT",
			  category      => "collection",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "tln",
              MITRE         => "T1125 & T1123",
              version       => 20200904);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of ConsentStore subkeys";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
#	::rptMsg("Launching consentstore v.".$VERSION);
#	::rptMsg("consentstore v.".$VERSION); 
#	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	my @paths = ('Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore',
	             'Software\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			my @sk1 = $key->get_list_of_subkeys();
			if (scalar @sk1 > 0) {
				foreach my $s1 (@sk1) {
					my $top_name = $s1->get_name();
					
					my @sk2 = $s1->get_list_of_subkeys();
					if (scalar @sk2 > 0) {
						foreach my $s2 (@sk2) {
							my $name = $s2->get_name();
							
							if ($name eq "NonPackaged") {
								my @sk3 = $s2->get_list_of_subkeys();
								if (scalar @sk3 > 0) {
									foreach my $s3 (@sk3) {
										processKey($s3,$top_name);
									}
								}
							}
							else {
								processKey($s2,$top_name);
							}
						
						}
					}
				}
			}
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
}

sub processKey {
	my $key = shift;
	my $device = shift;
	my $name = $key->get_name();
	$name =~ s/#/\\/g;
	
	my $start = ();
	my $stop  = ();
						
	eval {
		my $s = $key->get_value("LastUsedTimeStart")->get_data();
		my ($t0,$t1) = unpack("VV",$s);
		$start = ::getTime($t0,$t1);
		::rptMsg($start."|REG|||ConsentStore ".$device." \"".$name."\" LastUsedTimeStart");
	};
							
	eval {
		my $s = $key->get_value("LastUsedTimeStop")->get_data();
		my ($t0,$t1) = unpack("VV",$s);
		$stop = ::getTime($t0,$t1);
		::rptMsg($stop."|REG|||ConsentStore ".$device." \"".$name."\" LastUsedTimeStop");
	};

}

1;