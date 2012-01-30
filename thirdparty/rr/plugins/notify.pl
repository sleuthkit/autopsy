#-----------------------------------------------------------
# notify.pl
# 
#
# Change History:
#   20110309 - updated output format to sort entries based on
#              LastWrite time
#   20110308 - created
#
# References
#   http://blogs.technet.com/b/markrussinovich/archive/2011/03/08/3392087.aspx
#
# copyright 2011 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package notify;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20110309);

sub getConfig{return %config}

sub getShortDescr {
	return "Get Notify subkey entries";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %notify;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching notify v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Winlogon\\Notify";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("notify");
		::rptMsg($key_path);
		::rptMsg("");
		my @sk = $key->get_list_of_subkeys();
		if (scalar(@sk) > 0) {
			foreach my $s (@sk) {
				my $name = $s->get_name();
				my $lw = $s->get_timestamp();
				my $dll;
				eval {
					$dll = $s->get_value("DLLName")->get_data();
					push(@{$notify{$lw}},sprintf "%-15s %-25s",$name,$dll);
				};
			}
			
			foreach my $t (reverse sort {$a <=> $b} keys %notify) {
				::rptMsg(gmtime($t)." UTC");
				foreach my $i (@{$notify{$t}}) {
					::rptMsg("  ".$i);
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