#-----------------------------------------------------------
# winevt.pl
#   Extracts the event log settings stored in the software hive
#   to show what logging is enabled and disabled
#
#
# Change History:
#   20140402 % created
#
# References
#   http://publib.boulder.ibm.com/infocenter/tivihelp/v61r1/index.jsp?topic=%2Fcom.ibm.itm.doc_6.3%2Ftrouble%2Ftema_oswinevents_trouble.htm
#
# Script written by Corey Harrell (Journey Into IR)
#-----------------------------------------------------------
package winevt;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20140402);

sub getConfig{return %config}

sub getShortDescr {
	return "Get the Windows event log policy from the Winevt\\Channels key";	
}

sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {

  ::logMsg("Launching winevt v.".$VERSION);
  ::rptMsg("winevt v.".$VERSION); # 20110830 [fpi] + banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $class = shift;
	my $hive = shift;
	my $reg = Parse::Win32Registry->new($hive);
	
	my $root_key = $reg->get_root_key;
	my $key_path = "Microsoft\\Windows\\CurrentVersion\\WINEVT\\Channels";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
	::rptMsg("");
	::rptMsg($key_path);
	::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
	::rptMsg("");
		
	my @subkeys = $key->get_list_of_subkeys();
	if (scalar(@subkeys) > 0) {
		foreach my $s (@subkeys) {
			my $enabled;
			eval {
				$enabled = $s->get_value("Enabled")->get_data();
			};
				
			::rptMsg("Event Log Registry Key   : ".$s->get_name());
			::rptMsg("LastWrite                : ".gmtime($s->get_timestamp())." (UTC)");
			::rptMsg("Enabled Value            : ".$enabled);
			::rptMsg("");
				
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
