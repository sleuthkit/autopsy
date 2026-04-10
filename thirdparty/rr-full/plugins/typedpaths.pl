#-----------------------------------------------------------
# typedpaths.pl
# For Windows 7, Desktop Address Bar History
#
# Change history
#   20201005 - MITRE update
#   20200526 - updated date output format
#	  20100330 - created
#
# References
#   
# copyright 2020 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package typedpaths;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
              category      => "user activity",
			  output		=> "report",
              version       => 20201005);

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
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
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