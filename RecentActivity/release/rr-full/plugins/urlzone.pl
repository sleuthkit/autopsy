#-----------------------------------------------------------
# /root/bin/plugins/urlzone.pl
# Plugin to detect URLZONE infection
#
# copyright 2009 Stefan Kelm (skelm@bfk.de)
#-----------------------------------------------------------
package urlzone;
use strict;

my %config = (hive          => "Software",
             osmask        => 22,
             hasShortDescr => 1,
             hasDescr      => 0,
             hasRefs       => 0,
             version       => 20090526);

sub getConfig{return %config}

sub getShortDescr {return "URLZONE detection";}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
my $class = shift;
my $hive = shift;
::logMsg("Launching urlzone v.".$VERSION);
::rptMsg("urlzone v.".$VERSION); # banner
::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
my $reg = Parse::Win32Registry->new($hive);
my $root_key = $reg->get_root_key;

my $key_path = "Microsoft\\Windows\\CurrentVersion\\Internet Settings\\urlzone";
my $key;
if ($key = $root_key->get_subkey($key_path)) {
	::rptMsg($key_path);
	::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
	::rptMsg("");
 
	my @subkeys = $key->get_list_of_subkeys();
	if (scalar(@subkeys) > 0) {
		foreach my $s (@subkeys) {
			::rptMsg($key_path."\\".$s->get_name());
			::rptMsg("LastWrite Time = ".gmtime($s->get_timestamp())." (UTC)");
			eval {
				my @vals = $s->get_list_of_values();
				if (scalar(@vals) > 0) {
					my %sns;
					foreach my $v (@vals) {
						$sns{$v->get_name()} = $v->get_data();
					}
					foreach my $i (keys %sns) {
						::rptMsg("\t\t".$i." = ".$sns{$i});
					}
				}
				else {
# No values                                                    
				}
			};
			::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
#		::logMsg($key_path." not found.");
	}

	my $key_path2 = "Microsoft\\Windows NT\\CurrentVersion\\Image File Execution Options\\userinit.exe";
	my $key2;
	if ($key2 = $root_key->get_subkey($key_path2)) {
		::rptMsg($key_path2);
		::rptMsg("LastWrite Time ".gmtime($key2->get_timestamp())." (UTC)");
 		::rptMsg("");
		my $dbg;
		eval {
			$dbg = $key2->get_value("Debugger")->get_data();
		};
		if ($@) {
			::rptMsg("Debugger value not found.");
		}
		else {
			::rptMsg("Debugger = ".$dbg);
		}
		::rptMsg("");
	}
	else {
		::rptMsg($key_path2." not found.");
#		::logMsg($key_path2." not found.");
	}
}
1;