#-----------------------------------------------------------
# updates.pl
# 
# 
# References:
#    https://stackoverflow.com/questions/5102900/registry-key-location-for-security-update-and-hotfixes
#
# Change History:
#    20170715 - created
#
# copyright 2017 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package updates;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20170715);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets updates/hotfixes from Software hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	
	my %uninst;
	::logMsg("Launching updates v.".$VERSION);
	::rptMsg("updates v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n");
	
	my $key_path = 'Microsoft\\Windows\\CurrentVersion\\Component Based Servicing\\Packages';
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	::rptMsg("Updates");
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {

      ::rptMsg($key_path);
      ::rptMsg("");
		
     
      my @subkeys = $key->get_list_of_subkeys();
      if (scalar(@subkeys) > 0) {
        foreach my $s (@subkeys) {
          my $lastwrite = $s->get_timestamp();
          my $install;
          eval {
            $install = $s->get_value("InstallName")->get_data();
          };
          $install = $s->get_name() if ($install eq "");
	 			
          my $client;
          eval {
            $client = $s->get_value("InstallClient")->get_data();
          };
          $install .= "   InstallClient: ".$client unless ($@);
	 			
          push(@{$uninst{$lastwrite}},$install);
		}
      }
    
	  foreach my $t (reverse sort {$a <=> $b} keys %uninst) {
        ::rptMsg(gmtime($t)." (UTC)");
        foreach my $item (@{$uninst{$t}}) {
          ::rptMsg("  ".$item);
        }
        ::rptMsg("");
      }
    }
    else {
      ::rptMsg($key_path." has no subkeys.");
    }
}
1;