#-----------------------------------------------------------
# clsid.pl
# Plugin to extract file association data from the Software hive file
# Can take considerable time to run; recommend running it via rip.exe
#
# History
#   20130603 - added alert functionality
#   20100227 - created
#
# References
#   http://msdn.microsoft.com/en-us/library/ms724475%28VS.85%29.aspx
#
# copyright 2010, Quantum Analytics Research, LLC
#-----------------------------------------------------------
package clsid;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130603);

sub getConfig{return %config}

sub getShortDescr {
	return "Get list of CLSID/registered classes";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my %clsid;
	::logMsg("Launching clsid v.".$VERSION);
	::rptMsg("clsid v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Classes\\CLSID";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
# First step will be to get a list of all of the file extensions
		my %ext;
		my @sk = $key->get_list_of_subkeys();
		if (scalar(@sk) > 0) {
			foreach my $s (@sk) {
				
				my $name = $s->get_name();
				eval {
					my $n = $s->get_value("")->get_data();
					$name .= "  ".$n unless ($n eq "");
				};
				
			  eval {
			  	my $path = $s->get_subkey("InprocServer32")->get_value("")->get_data();
			  	alertCheckPath($path);
			  	alertCheckADS($path);
			  	
			  };
				
				push(@{$clsid{$s->get_timestamp()}},$name);
			}
			
			foreach my $t (reverse sort {$a <=> $b} keys %clsid) {
				::rptMsg(gmtime($t)." Z");
				foreach my $item (@{$clsid{$t}}) {
					::rptMsg("  ".$item);
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

#-----------------------------------------------------------
# alertCheckPath()
#-----------------------------------------------------------
sub alertCheckPath {
	my $path = shift;
	$path = lc($path);
	my @alerts = ("recycle","globalroot","temp","system volume information","appdata",
	              "application data");
	
	foreach my $a (@alerts) {
		if (grep(/$a/,$path)) {
			::alertMsg("ALERT: clsid: ".$a." found in path: ".$path);              
		}
	}
}

#-----------------------------------------------------------
# alertCheckADS()
#-----------------------------------------------------------
sub alertCheckADS {
	my $path = shift;
	my @list = split(/\\/,$path);
	my $last = $list[scalar(@list) - 1];
	::alertMsg("ALERT: clsid: Poss. ADS found in path: ".$path) if grep(/:/,$last);
}
1;