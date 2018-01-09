#-----------------------------------------------------------
# rdpnla.pl
#
# 20151203 - created
#
# Author: Chakib Gzenayi, chakib.gzenayi@gmail.com
#-----------------------------------------------------------
package rdpnla;
use strict;
my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20151203);

sub getConfig{return %config}
sub getShortDescr {
 	return "Queries System hive for RDP NLA Checking";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $key;

	::logMsg("Launching rdpnla v.".$VERSION);
	::rptMsg("rdpnla v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
        
 	my $chak = $root_key->get_subkey("Select")->get_value("Current")->get_data(); 
  my $key_path = "ControlSet00".$chak."\\Control\\Terminal Server\\WinStations\\RDP-Tcp";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
 		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
    my $sec;
    eval {
    	$sec = $key->get_value("SecurityLayer")->get_data();
       ::rptMsg("SecurityLayer = ".$sec );
		};
		::rptMsg("Error getting Value: ".$@) if ($@);

	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;
