#-----------------------------------------------------------
# typedpaths.pl
# For Windows 7, Desktop Address Bar History
#
# Change history
#	  20100330 - created
#
# References
#   
# 
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package typedpaths;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100330);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's typedpaths key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching typedpaths v.".$VERSION);
	::rptMsg("typedpaths v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\TypedPaths";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			my %paths;
			foreach my $v (@vals) { 
				my $name = $v->get_name();
				$name =~ s/^url//;
				my $data = $v->get_data();
				$paths{$name} = $data;
			}
			foreach my $p (sort {$a <=> $b} keys %paths) {
				::rptMsg(sprintf "%-8s %-30s","url".$p,$paths{$p});
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;