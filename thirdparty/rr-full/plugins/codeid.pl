#-----------------------------------------------------------
# codeid
# Get DefaultLevel value from CodeIdentifiers key
# 
#
# Change History
#   20100608 - created
#
# References
#   SANS ISC blog - http://isc.sans.edu/diary.html?storyid=8917
#   CodeIdentifiers key
#       - http://technet.microsoft.com/en-us/library/bb457006.aspx
#   SAFER_LEVELID_FULLYTRUSTED value 
#       - http://msdn.microsoft.com/en-us/library/ms722424%28VS.85%29.aspx
#         (262144 == Unrestricted)
# 
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package codeid;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100608);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets CodeIdentifier DefaultLevel value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching codeid v.".$VERSION);
	::rptMsg("codeid v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Policies\\Microsoft\\Windows\\Safer\\CodeIdentifiers";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("CodeID");
		::rptMsg($key_path);
		my $lastwrite = $key->get_timestamp();
		::rptMsg("  LastWrite time: ".gmtime($lastwrite)." Z");
		::rptMsg("");
		
		my $level;
		eval {
			$level = $key->get_value("DefaultLevel")->get_data();
			::rptMsg(sprintf "DefaultLevel = 0x%08x",$level);
		};
		
		my $exe;
		eval {
			$exe = $key->get_value("ExecutableTypes")->get_data();
			$exe =~ s/\s/,/g;
			::rptMsg("ExecutableTypes = ".$exe);
			
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;