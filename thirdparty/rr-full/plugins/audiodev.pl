#-----------------------------------------------------------
# audiodev.pl
#  Get audio input/output device information from the Software hive,
#  for use with mixer.pl/mixer_tln.pl plugins
#
# Change history:
#  20141112 - created
# 
# Ref:
#  http://www.ghettoforensics.com/2014/11/dj-forensics-analysis-of-sound-mixer.html
#
# copyright 2014 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package audiodev;
use strict;

my %config = (hive          => "Software",
							category      => "devices",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20141112);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets audio capture/render devices";	
}
sub getDescr{}
sub getRefs {
	my %refs = ();
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching audiodev v.".$VERSION);
	::rptMsg("audiodev v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	my $key_path = 'Microsoft\\Windows\\CurrentVersion\\MMDevices\\Audio';
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Get capture/input devices
	my $key;
	if ($key = $root_key->get_subkey($key_path.'\\Capture')) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			::rptMsg("Capture/Input Devices: GUID, Device");
			foreach my $sk (@subkeys) {
				my $name = $sk->get_name();
				eval {
					my $dev = $sk->get_subkey("Properties")->get_value("{a45c254e-df1c-4efd-8020-67d146a850e0},2")->get_data();
					::rptMsg($name.", Device: ".$dev);
				};
			}
		}
	}
	else {
		::rptMsg("Could not get root key\.");
	}
	::rptMsg("");
# Get render/output devices	
	if ($key = $root_key->get_subkey($key_path.'\\Render')) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			::rptMsg("Render/Output Devices: GUID, Device");
			foreach my $sk (@subkeys) {
				my $name = $sk->get_name();
				eval {
					my $dev = $sk->get_subkey("Properties")->get_value("{a45c254e-df1c-4efd-8020-67d146a850e0},2")->get_data();
					::rptMsg($name.", Device: ".$dev);
				};
			}
		}
	}
	else {
		::rptMsg("Could not get root key\.");
	}
	
	
	
	
	
	
}
1;