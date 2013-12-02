#-----------------------------------------------------------
# ctrlpnl.pl
# Get Control Panel info from the Software hive
#
# Change history:
#   20100116 - created
#
# References:
#   http://support.microsoft.com/kb/292463
#   http://learning.infocollections.com/ebook%202/Computer/
#          Operating%20Systems/Windows/Windows.XP.Hacks/
#          0596005113_winxphks-chp-2-sect-3.html
#   http://msdn.microsoft.com/en-us/library/cc144195%28VS.85%29.aspx
#
# Notes: 
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package ctrlpnl;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100116);

sub getConfig{return %config}

sub getShortDescr {
	return "Get Control Panel info from Software hive";	
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
	::logMsg("Launching ctrlpnl v.".$VERSION);
	::rptMsg("ctrlpnl v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows\\CurrentVersion\\Control Panel";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("");

# Cpls section
		if (my $cpl = $key->get_subkey("Cpls")) {
			my @vals = $cpl->get_list_of_values();
			if (scalar @vals > 0) {
				::rptMsg("Cpls key");
				foreach my $v (@vals) {
					my $str = sprintf "%-10s %-50s",$v->get_name(),$v->get_data();
					::rptMsg($str);
				}
				::rptMsg("");
			}
			else {
				::rptMsg("Cpls key has no values.");
			}
		}
		else {
			::rptMsg("Cpls key not found.");
		}
	
# don't load section
# The 'don't load' key prevents applets from being loaded
# Be sure to check the user's don't load key, as well
	if (my $cpl = $key->get_subkey("don't load")) {
			my @vals = $cpl->get_list_of_values();
			if (scalar @vals > 0) {
				::rptMsg("don't load key");
				foreach my $v (@vals) {
					::rptMsg($v->get_name());
				}
				::rptMsg("");
			}
			else {
				::rptMsg("don't load key has no values.");
			}
		}
		else {
			::rptMsg("don't load key not found.");
		}

# Extended Properties section		
		if (my $ext = $key->get_subkey("Extended Properties")) {
			my @sk = $ext->get_list_of_subkeys();
			if (scalar @sk > 0) {
				foreach my $s (@sk) {
					my @vals = $s->get_list_of_values();
					if (scalar @vals > 0) {
						::rptMsg($s->get_name()." [".gmtime($s->get_timestamp)." UTC]");

# Ref: http://support.microsoft.com/kb/292463						
						my %cat = (0x00000000 => "Other Control Panel Options",
                       0x00000001 => "Appearance and Themes",
                       0x00000002 => "Printers and Other Hardware",
                       0x00000003 => "Network and Internet Connections",
                       0x00000004 => "Sounds, Speech, and Audio Devices",
                       0x00000005 => "Performance and Maintenance",
                       0x00000006 => "Date, Time, Language, and Regional Options",
                       0x00000007 => "Accessibility Options",
                       0xFFFFFFFF => "No Category");
            my %prop;    
						foreach my $v (@vals) {
							push(@{$prop{$v->get_data()}},$v->get_name());
						}
						
						foreach my $t (sort {$a <=> $b} keys %prop) {
							(exists $cat{$t}) ?	(::rptMsg($cat{$t})) : (::rptMsg("Category ".$t));
							foreach my $i (@{$prop{$t}}) {
								::rptMsg("  ".$i);
							}
							::rptMsg(""); 
						}
					}
				}
				::rptMsg("");
			}
			else {
				::rptMsg("Extended Properties key has no subkeys.");
			}
		}
		else {
			::rptMsg("Extended Properties key not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;