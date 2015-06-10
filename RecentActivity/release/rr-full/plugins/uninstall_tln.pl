#-----------------------------------------------------------
# uninstall_tln.pl
# Gets contents of Uninstall key from Software hive; sorts 
# display names based on key LastWrite time; displays in TLN
# format
# 
# References:
#    http://support.microsoft.com/kb/247501
#    http://support.microsoft.com/kb/314481
#    http://msdn.microsoft.com/en-us/library/ms954376.aspx
#
# Change History:
#    20120523 - updated to include 64-bit systems
#    20100116 - Minor updates
#    20090413 - Extract DisplayVersion info
#    20090128 - Added references
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package uninstall_tln;
use strict;

my %config = (hive          => "Software, NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20120523);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets contents of Uninstall keys from Software, NTUSER\.DAT hives(TLN format)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching uninstall v.".$VERSION);
	
	my @keys = ('Microsoft\\Windows\\CurrentVersion\\Uninstall',
	            'Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall',
	            'Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall',                  # NTUSER.DAT
	            'Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall');    # NTUSER.DAT
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	::rptMsg("Uninstall");
	foreach my $key_path (@keys) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			
			::rptMsg($key_path);
			::rptMsg("");
		
			my %uninst;
			my @subkeys = $key->get_list_of_subkeys();
	 		if (scalar(@subkeys) > 0) {
	 			foreach my $s (@subkeys) {
	 				my $lastwrite = $s->get_timestamp();
	 				my $display;
	 				eval {
	 					$display = $s->get_value("DisplayName")->get_data();
	 				};
	 				$display = $s->get_name() if ($display eq "");
	 			
	 				my $ver;
	 				eval {
	 					$ver = $s->get_value("DisplayVersion")->get_data();
	 				};
	 				$display .= " v\.".$ver unless ($@);
	 			
	 				push(@{$uninst{$lastwrite}},$display);
	 			}
	 			foreach my $t (reverse sort {$a <=> $b} keys %uninst) {
	 				foreach my $item (@{$uninst{$t}}) {
	 					::rptMsg($t."|REG|||[Uninstall] - $item");
	 				}
				}
	 		}
	 		else {
	 			::rptMsg($key_path." has no subkeys.");
	 		}
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
}
1;