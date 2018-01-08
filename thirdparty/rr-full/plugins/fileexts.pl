#-----------------------------------------------------------
# fileexts.pl
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package fileexts;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20080818);

sub getConfig{return %config}

sub getShortDescr {
	return "Get user FileExts values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching fileexts v.".$VERSION);
	::rptMsg("fileexts v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("fileexts");
		::rptMsg($key_path);
		::rptMsg("");
		
		my @sk = $key->get_list_of_subkeys();
		if (scalar(@sk) > 0) {
			foreach my $s (@sk) {
				my $name = $s->get_name();
				next unless ($name =~ m/^\.\w+/);
				
				eval {
					my $data = $s->get_subkey("OpenWithList")->get_value("MRUList")->get_data();
					if ($data =~ m/^\w/) {
						::rptMsg("File Extension: ".$name);
						::rptMsg("LastWrite: ".gmtime($s->get_subkey("OpenWithList")->get_timestamp()));
						::rptMsg("MRUList: ".$data);
						my @list = split(//,$data);
						foreach my $l (@list) {
							my $valdata = $s->get_subkey("OpenWithList")->get_value($l)->get_data();
							::rptMsg("  ".$l." => ".$valdata);
						}
						::rptMsg("");
					}
				};
			}
		}
		else {
			::rptMsg($key_path." does not have subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;