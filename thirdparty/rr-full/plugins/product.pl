#-----------------------------------------------------------
# product.pl
# Plugin to determine the MSI packages installed on the system
#
# Change history:
#   20100325 - created
#
# References:
#   http://support.microsoft.com/kb/236590
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package product;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100325);

sub getConfig{return %config}

sub getShortDescr {
	return "Get installed product info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %msi;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching product v.".$VERSION);
	::rptMsg("product v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows\\CurrentVersion\\Installer\\UserData";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
# Each of these subkeys should be SIDs
			foreach my $s (@subkeys) {
				next unless ($s->get_name() =~ m/^S/);
				::rptMsg($s->get_name());
				if ($s->get_subkey("Products")) {
					processSIDKey($s->get_subkey("Products"));
					::rptMsg("");
				}
				else {
					::rptMsg($s->get_name()."\\Products subkey not found.");
				}
			}			
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub processSIDKey {
	my $key = shift;
	my %prod;
	my @subkeys = $key->get_list_of_subkeys();
	if (scalar(@subkeys) > 0) {
#		::rptMsg($key->get_name());
		foreach my $s (@subkeys) {
			my ($displayname,$lastwrite);
			eval {
				$displayname = $s->get_subkey("InstallProperties")->get_value("DisplayName")->get_data();
				$lastwrite   = $s->get_subkey("InstallProperties")->get_timestamp();
			};
			
			my $displayversion;
			eval {
				$displayversion = $s->get_subkey("InstallProperties")->get_value("DisplayVersion")->get_data();
			};
			
			my $installdate;
			eval {
				$installdate = $s->get_subkey("InstallProperties")->get_value("InstallDate")->get_data();
			};
			
			my $str = $displayname." v.".$displayversion.", ".$installdate;
			push(@{$prod{$lastwrite}},$str);
		}
		
		foreach my $t (reverse sort {$a <=> $b} keys %prod) {
			::rptMsg(gmtime($t)." Z");
			foreach my $i (@{$prod{$t}}) {
				::rptMsg("  ".$i);
			}
		}
		
		
	}
	else {
		::rptMsg($key->get_name()." has no subkeys.");
		return;
	}
}

1;