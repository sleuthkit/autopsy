#-----------------------------------------------------------
# updates.pl
# 
# 
# References:
#    https://stackoverflow.com/questions/5102900/registry-key-location-for-security-update-and-hotfixes
#	 https://www.iblue.team/windows-forensics/security-patch-kb-install-date
#
# Change History:
#    20220724 - updated with new content
#    20170715 - created
#
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package updates;
use strict;

my %config = (hive          => "Software",
              MITRE         => "",
			  category      => "",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
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
	::rptMsg("updates v.".$VERSION); 
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
		  my $name = $s->get_name();	
          my $lastwrite = $s->get_timestamp();
		  
		  ::rptMsg($name);
		  ::rptMsg("LastWrite time: ".::format8601Date($s->get_timestamp())."Z");
		  
		  my @values = ("InstallClient","InstallLocation","InstallUser","SelfUpdate");
		  foreach my $v (@values) {
			
			eval {
				my $t = $s->get_value($v)->get_data();
				::rptMsg(sprintf "  %-18s %-40s",$v,$t);
			};
		
		  }

          ::rptMsg(""); 
		}
      }
    
    }
    else {
      ::rptMsg($key_path." has no subkeys.");
    }
}
1;