#-----------------------------------------------------------
# tsclient_tln.pl
# Plugin for Registry Ripper
#
# Change history
#    20120827 - updated; added "Servers" key check, translated to TLN output
#    20080324 - created
#
# References
#   http://support.microsoft.com/kb/312169
# 
# copyright 2012 
# Author: H. Carvey
#-----------------------------------------------------------
package tsclient_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 0,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120827);

sub getConfig{return %config}
sub getShortDescr {
	return "Displays contents of user's Terminal Server Client key (TLN)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching tsclient v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Terminal Server Client\\Default';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("TSClient");
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my $lw = $key->get_timestamp();
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			my $mru0;
			eval {
				$mru0 = $key->get_value("MRU0")->get_data();
				::rptMsg($lw."|REG|||TSClient/Default - ".$mru0);
			};
		}
		else {
#			::rptMsg($key_path." has no values.");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	
	my $key_path = 'Software\\Microsoft\\Terminal Server Client\\Servers';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
#		::rptMsg("");
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				my $lw   = $s->get_timestamp();
				my $descr = "TSClient/Servers - ".$name;
				my $hint;
				eval {
					$hint = $s->get_value("UsernameHint")->get_data();
					$descr .= " (Hint: ".$hint.")";
#					::rptMsg("  UsernameHint: ".$hint);
				};
				::rptMsg($lw."|REG|||".$descr);
			}
		}
		else {
#			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}	
}

1;