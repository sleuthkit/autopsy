#-----------------------------------------------------------
# shelloverlay
# Get contents of ShellIconOverlayIdentifiers subkeys; sorts data
# based on LastWrite times of subkeys
# 
# History
#   20100308 - created
#
# References
#   http://msdn.microsoft.com/en-us/library/cc144123%28VS.85%29.aspx
#   Coreflood - http://vil.nai.com/vil/content/v_102053.htm
#   http://www.secureworks.com/research/threats/coreflood/?threat=coreflood
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
              osmask        => 22,
              version       => 20100308);

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
	::rptMsg("shelloverlay v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my %id;
	
	my $key_path = 'Microsoft\\Windows\\CurrentVersion\\Explorer\\ShellIconOverlayIdentifiers';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("shelloverlay");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
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
				::rptMsg(gmtime($t)." Z");
				foreach my $item (@{$id{$t}}) {
					::rptMsg("  ".$item);
				}
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