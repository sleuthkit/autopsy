#-----------------------------------------------------------
# appkeys_tln.pl
#
# Change history
#   20180920 - created
#
# References
#   http://www.hexacorn.com/blog/2018/07/06/beyond-good-ol-run-key-part-80/
#   http://blog.airbuscybersecurity.com/post/2015/06/Latest-improvements-in-PlugX
#   https://docs.microsoft.com/en-us/windows/desktop/inputdev/wm-appcommand
#
# Copyright (c) 2018 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package appkeys_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT, Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              category      => "persistence",
              version       => 20180920);
my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Extracts AppKeys entries.";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

#	::logMsg("Launching appkeys v.".$VERSION);
#  ::rptMsg("appkeys v.".$VERSION); 
# ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");     
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	
	my @paths = ("Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\AppKey",
	             "Microsoft\\Windows\\CurrentVersion\\Explorer\\AppKey");
	
	foreach my $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
#			::rptMsg($key_path);
			my $lw = $key->get_timestamp();
			
			my @sk = $key->get_list_of_subkeys();
			if (scalar @sk > 0) {
				foreach my $s (@sk) {
					my $name = $s->get_name();
					my $sk_lw = $s->get_timestamp();
					eval {
						my $shell = $s->get_value("ShellExecute")->get_data();
						::rptMsg($sk_lw."|AppKeys|||AppKey\\".$name." LastWrite - ShellExecute Value: ".$shell);
					};
					
					eval {
						my $assoc = $s->get_value("Association")->get_data();
						::rptMsg($sk_lw."|AppKeys|||AppKey\\".$name." LastWrite - Association Value: ".$assoc);
					};
				}
			}
			else {
#				::rptMsg($key_path." has no subkeys.");
			}	
		}
	}
}

1;
