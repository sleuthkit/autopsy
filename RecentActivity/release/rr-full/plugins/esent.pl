#-----------------------------------------------------------
# esent
# Get contents of Esent\Process key from Software hive
# 
# Note: Not sure why I wrote this one; just thought it might come
#       in handy as info about this key is developed.
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package esent;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              version       => 20101202);

sub getConfig{return %config}

sub getShortDescr {
	return "Get ESENT\\Process key contents";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching esent v.".$VERSION);
	::rptMsg("esent v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\ESENT\\Process";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my @sk = $key->get_list_of_subkeys();
		
		if (scalar(@sk) > 0) {
			my %esent;
			
			foreach my $s (@sk) {
				my $sk = $s->get_subkey("DEBUG");
#				my $lw = $s->get_timestamp();
				my $lw = $sk->get_timestamp();

				my $name = $s->get_name();
				
				push(@{$esent{$lw}},$name);
			}
			
			foreach my $t (reverse sort {$a <=> $b} keys %esent) {
				::rptMsg(gmtime($t)." (UTC)");
				foreach my $item (@{$esent{$t}}) {
					::rptMsg("  $item");
				}
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