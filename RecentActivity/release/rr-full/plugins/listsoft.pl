#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# listsoft.pl
# Plugin for Registry Ripper; traverses thru the Software
# key of an NTUSER.DAT file, extracting all of the subkeys 
# and listing them in order by LastWrite time.
#
# Change history
# 
# 
# copyright 2008 H. Carvey
#-----------------------------------------------------------
package listsoft;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080324);

sub getConfig{return %config}
sub getShortDescr {
	return "Lists contents of user's Software key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $file = shift;
	my $reg = Parse::Win32Registry->new($file);
	my $root_key = $reg->get_root_key;
	::logMsg("Launching listsoft v.".$VERSION);
	::rptMsg("listsoft v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my %soft;
	my $key_path = 'Software';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("listsoft v.".$VERSION);
		::rptMsg("List the contents of the Software key in the NTUSER\.DAT hive");
		::rptMsg("file, in order by LastWrite time.");
		::rptMsg("");
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				push(@{$soft{$s->get_timestamp()}},$s->get_name());
			}
			
			foreach my $t (reverse sort {$a <=> $b} keys %soft) {
				foreach my $item (@{$soft{$t}}) {
					::rptMsg(gmtime($t)."Z \t".$item);
				}
			}	
		}
		else {
			::logMsg($key_path." has no subkeys.");
		}
	}
	else {
		::logMsg("Could not access ".$key_path);
	}
}

1;