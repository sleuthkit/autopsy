#-----------------------------------------------------------
# shelloverlay
# Get contents of ShellIconOverlayIdentifiers subkeys; sorts data
# based on LastWrite times of subkeys
# 
# History
#   20201007 - MITRE update
#   20100308 - created
#
# References
#   http://msdn.microsoft.com/en-us/library/cc144123%28VS.85%29.aspx
#   https://attack.mitre.org/techniques/T1546/015/
#   https://www.welivesecurity.com/wp-content/uploads/2016/10/eset-sednit-part-2.pdf, pg 69
#
# Analysis Tip: Malware such as Coreflood uses a random subkey name and a
#               random CLSID GUID value
#
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package shelloverlay;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1546\.015",
              category      => "persistence",
			  output		=> "report",
              version       => 20201007);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets ShellIconOverlayIdentifiers values";	
}
sub getDescr{}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching shelloverlay v.".$VERSION);
	::rptMsg("shelloverlay v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my %id;
	
	my $key_path = 'Microsoft\\Windows\\CurrentVersion\\Explorer\\ShellIconOverlayIdentifiers';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("shelloverlay");
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				my $def;
				eval {
					$def = $s->get_value("")->get_data();
					$name .= "  ".$def;
				};
				push(@{$id{$s->get_timestamp()}},$name);
			}
			
			foreach my $t (reverse sort {$a <=> $b} keys %id) {
				::rptMsg(::format8601Date($t)."Z");
				foreach my $item (@{$id{$t}}) {
					::rptMsg("  ".$item);
				}
				::rptMsg("");
				::rptMsg("Analysis Tip: ShellIconOverlays can be used for persistence.");
				::rptMsg("See pg 69 of https://www.welivesecurity.com/wp-content/uploads/2016/10/eset-sednit-part-2.pdf");
#				::rptMsg("");
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