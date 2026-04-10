#-----------------------------------------------------------
# networkcards
#
# History
#  20200921 - MITRE update
#	 20200518 - update date output format
#  20080325 - created 
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package networkcards;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
              category      => "config",
			  output		=> "report",
              version       => 20200921);

sub getConfig{return %config}
sub getShortDescr {
	return "Get NetworkCards Info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching networkcards v.".$VERSION);
	::rptMsg("networkcards v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\NetworkCards";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("NetworkCards");
		::rptMsg($key_path);
		::rptMsg("");
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			::rptMsg(sprintf "%-50s %-50s","Description","Key LastWrite time");
			foreach my $s (@subkeys) {
				eval {
					my $desc = $s->get_value("Description")->get_data();
					::rptMsg(sprintf "%-50s %-50s",$desc,::format8601Date($s->get_timestamp())."Z");
				};

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
1;