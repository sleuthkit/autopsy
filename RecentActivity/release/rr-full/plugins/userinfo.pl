#-----------------------------------------------------------
# userinfo.pl
# Plugin for Registry Ripper, NTUSER.DAT edition - gets the 
# MS Office UserInfo values 
#
# Change history
#   20130513 - added check for UserName in Common key
#   20110609 - created
#
# References
#   Based on Joe G.'s post to ForensicArtifacts.com
#   
# 
# copyright 2011 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package userinfo;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20130513);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of MS Office UserInfo values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching userinfo v.".$VERSION);
	::rptMsg("userinfo v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $key_path = 'Software\\Microsoft\\Office\\Common';
	if (my $key = $root_key->get_subkey($key_path)) {
		my $username;
		eval {
			$username = $key->get_value("UserName")->get_data();
			::rptMsg($key_path."\\UserName = ".$username);
		};
		
	}
	else {
	  ::rptMsg($key_path." not found\.");	
	}
	
	::rptMsg("");
	my %keys = (2003 => 'Software\\Microsoft\\Office\\11\.0\\Common\\UserInfo',
	            2007 => 'Software\\Microsoft\\Office\\Common\\UserInfo');
	
	foreach my $k (keys %keys) {
		my $key_path = $keys{$k};
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
			my @vals = $key->get_list_of_values();
			if (scalar (@vals) > 0) {
				foreach my $v (@vals) {
					::rptMsg(sprintf "  %-15s %-20s",$v->get_name(),$v->get_data());
				}
			}
			else {
				::rptMsg($key_path." has no values.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
		::rptMsg("");
	}
}

1;