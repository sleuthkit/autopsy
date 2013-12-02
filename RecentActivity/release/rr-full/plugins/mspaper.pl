#-----------------------------------------------------------
# mspaper.pl
# Plugin for Registry Ripper, NTUSER.DAT edition - gets the 
# MSPaper Recent File List values 
#
# Change history
#
#
# References
#
# 
# copyright 2008 H. Carvey
#-----------------------------------------------------------
package mspaper;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080324);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets images listed in user's MSPaper key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching mspaper v.".$VERSION);
	::rptMsg("mspaper v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $tick = 0;
	my $key_path = 'Software\\Microsoft';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		
		if (scalar @subkeys > 0) {
			foreach my $sk (@subkeys) {
				if ($sk->get_name() =~ m/^mspaper/i) {
					$tick = 1;
					my $nkey = $sk->get_name()."\\Recent File List";
					my $msp;
					if ($msp = $key->get_subkey($nkey)) {
						::rptMsg("MSPaper - Recent File List");
						::rptMsg($key_path."\\".$nkey);
						::rptMsg("LastWrite Time ".gmtime($msp->get_timestamp())." (UTC)");
						my @vals = $msp->get_list_of_values();
						if (scalar(@vals) > 0) {
							my %files;
# Retrieve values and load into a hash for sorting			
							foreach my $v (@vals) {
								my $val = $v->get_name();
								my $data = $v->get_data();
								my $tag = (split(/File/,$val))[1];
								$files{$tag} = $val.":".$data;
							}
# Print sorted content to report file			
							foreach my $u (sort {$a <=> $b} keys %files) {
								my ($val,$data) = split(/:/,$files{$u},2);
								::rptMsg("  ".$val." -> ".$data);
							}
						}
						else {
							::rptMsg($key_path."\\".$nkey." has no values.");
						}
					}
					else {
						::rptMsg($key_path."\\".$nkey." not found.");
						::logMsg("Error: ".$key_path."\\".$nkey." not found.");
					}
				}
			}
			if ($tick == 0) {
				::rptMsg("SOFTWARE\\Microsoft\\MSPaper* not found.");
				::logMsg("SOFTWARE\\Microsoft\\MSPaper* not found.");
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
			::logMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}

1;