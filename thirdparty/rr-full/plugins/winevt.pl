#-----------------------------------------------------------
# winevt
#
# Change history:
#  20201012 - created
# 
# Ref:
#  
#
# copyright 2020 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package winevt;
use strict;

my %config = (hive          => "Software",
			  category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "report",
              version       => 20201012);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets Enabled values for WINEVT Channels";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching winevt v.".$VERSION);
	::rptMsg("winevt v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  
	my @paths = ('Microsoft\\Windows\\CurrentVersion\\WINEVT\\Channels');
	
	::rptMsg("WINEVT");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar @subkeys > 0) {
				::rptMsg(sprintf "%-22s %-87s %-2s","LastWrite","Channel","Enabled");
				foreach my $s (@subkeys) {
					my $enabled = ();
					eval {
						$enabled = $s->get_value("Enabled")->get_data();
					};
					$enabled = $@ if ($@);
					my $lw = ::format8601Date($key->get_timestamp())."Z";
					::rptMsg(sprintf "%-22s %-87s %-2s",$lw,$s->get_name(),$enabled);
				}
			}
			
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: This plugin retrieves the \"Enabled\" value from each available WINEVT Channel, indicating");
	::rptMsg("if it's enabled.  This can help obviate attempts at anti- or counter-forensics, by identifying when the");
	::rptMsg("setting may have been changed.");
}
1;