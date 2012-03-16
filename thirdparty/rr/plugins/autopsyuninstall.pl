#-----------------------------------------------------------
# uninstall.pl
# Gets contents of Uninstall key from Software hive; sorts 
# display names based on key LastWrite time
# 
# References:
#    http://support.microsoft.com/kb/247501
#    http://support.microsoft.com/kb/314481
#    http://msdn.microsoft.com/en-us/library/ms954376.aspx
#
# Change History:
#    20100116 - Minor updates
#    20090413 - Extract DisplayVersion info
#    20090128 - Added references
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package autopsyuninstall;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100116);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets contents of Uninstall key from Software hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	#::logMsg("Launching uninstall v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Microsoft\\Windows\\CurrentVersion\\Uninstall';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		#::rptMsg("Uninstall");
		#::rptMsg($key_path);
		#::rptMsg("");
		
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
				::rptMsg(gmtime($t)." (UTC)");
				foreach my $item (@{$uninst{$t}}) {
					::rptMsg("\t$item");
				}
				::rptMsg("");
			}
	 	}
	 	else {
	 		#::rptMsg($key_path." has no subkeys.");
	 	}
	}
	else {
		#::rptMsg($key_path." not found.");
	}
}
1;