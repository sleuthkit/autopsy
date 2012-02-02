#-----------------------------------------------------------
# mp2.pl
# Plugin for Registry Ripper,
# MountPoints2 key parser
#
# Change history
#   20091116 - updated output/sorting; added getting 
#              _LabelFromReg value
#   20090115 - Removed printing of "volumes"
#
# References
#   http://support.microsoft.com/kb/932463
# 
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package mp2;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20090115);

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
	::logMsg("Launching mp2 v.".$VERSION);
	
	my %drives;
	my %volumes;
	my %remote;
	
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\MountPoints2';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("MountPoints2");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				if ($name =~ m/^{/) {
					my $label;
					eval {
						$label = $s->get_value("_LabelFromReg")->get_data();
					};
					$name = $name." (".$label.")" unless ($@);
					push(@{$volumes{$s->get_timestamp()}},$name);
				}
				elsif ($name =~ m/^[A-Z]/) {
					push(@{$drives{$s->get_timestamp()}},$name);
				}
				elsif ($name =~ m/^#/) {
					push(@{$remote{$s->get_timestamp()}},$name);
				}
				else {
					::rptMsg("  Key name = ".$name);
				}
			}
			::rptMsg("");
			::rptMsg("Remote Drives:");
			foreach my $t (reverse sort {$a <=> $b} keys %remote) {
				::rptMsg(gmtime($t)." (UTC)");
				foreach my $item (@{$remote{$t}}) {
					::rptMsg("  $item");
				}
			}
			
			::rptMsg("");
			::rptMsg("Volumes:");
			foreach my $t (reverse sort {$a <=> $b} keys %volumes) {
				::rptMsg(gmtime($t)." (UTC)");
				foreach my $item (@{$volumes{$t}}) {
					::rptMsg("  $item");
				}
			}
			::rptMsg("");
			::rptMsg("Drives:");
			foreach my $t (reverse sort {$a <=> $b} keys %drives) {
				my $d = join(',',(@{$drives{$t}}));
				::rptMsg(gmtime($t)." (UTC) - ".$d);
			}
		
			::rptMsg("");
			::rptMsg("Analysis Tip: Correlate the Volume entries to those found in the MountedDevices");
			::rptMsg("entries that begin with \"\\??\\Volume\"\.");
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;