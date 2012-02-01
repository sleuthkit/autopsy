#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# muicache.pl
# Plugin for Registry Ripper, NTUSER.DAT edition - gets the 
# MUICache values 
#
# Change history
#
#
# 
# copyright 2008 H. Carvey
#-----------------------------------------------------------
package muicache;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080324);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets EXEs from user's MUICache key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching muicache v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	my $key_path = 'Software\\Microsoft\\Windows\\ShellNoRoam\\MUICache';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("MUICache");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $name = $v->get_name();
				next if ($name =~ m/^@/ || $name eq "LangID");
				my $data = $v->get_data();
				::rptMsg("\t".$name." (".$data.")");
			}
		}
		else {
			::rptMsg($key_path." has no values.");
			::logMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}

1;