#-----------------------------------------------------------
# mp3.pl
# Plugin for Registry Ripper,
# MountPoints2 key parser
#
# Change history
#   20120330 - updated to include parsing of UUID v1 GUIDs to get unique
#              MAC addresses
#   20091116 - updated output/sorting; added getting 
#              _LabelFromReg value
#   20090115 - Removed printing of "volumes"
#
# References
#   http://support.microsoft.com/kb/932463
# 
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package mp3;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120330);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's MountPoints2 key contents";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching mp3 v.".$VERSION);
	::rptMsg("mp3 v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my %drives;
	my %volumes;
	my %remote;
	my %macs;
	
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\MountPoints2';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("MountPoints2");
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				if ($name =~ m/^{/) {
					my $label;
					eval {
						$label = $s->get_value("_LabelFromReg")->get_data();
					};
					
					my $m = (split(/-/,$name,5))[4];
					$m =~ s/}$//;
					$m = uc($m);
					$m = join(':',unpack("(A2)*",$m));
					$macs{$m} = 1;

					$name = $name." (".$label.")" unless ($@);
					
					push(@{$volumes{$s->get_timestamp()}},$name);
				}
				else {
#					::rptMsg("  Key name = ".$name);
				}
			}
			foreach my $t (reverse sort {$a <=> $b} keys %volumes) {
				foreach my $id (@{$volumes{$t}}) {
					::rptMsg($t."|REG|Server|User|".$id." Volume MP2 key LastWrite");
					my $id2 = $id;
					$id =~ s/^{//;
					$id =~ s/}$//;
					$id =~ s/-//g;
					
					my $l = hex(substr($id,0,8));
					my $m = hex(substr($id,8,4));
					my $h = hex(substr($id,12,4)) & 0x0fff;
					my $h = $m | $h << 16;
					my $t2 = (::getTime($l,$h) - 574819200);
					
					::rptMsg($t2."|REG|Server|User|".$id2." Vol GUID date");
					
				}
			}

		}
		else {
#			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}
}

1;