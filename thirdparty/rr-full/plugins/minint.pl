#-----------------------------------------------------------
# minint.pl
# Detects if OS was told it is WinPE
# 
# Change history
#   20200831 - created
#
# References
#  https://twitter.com/0gtweet/status/1182516740955226112
#  https://blog.sec-labs.com/2019/10/hunting-for-minint-security-audit-block-in-registry/
#  https://www.quppa.net/blog/2016/04/14/beware-of-the-minint-registry-key/
# 
#  MITRE: https://attack.mitre.org/techniques/T1562/002/
#
# copyright 2020 QAR, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package minint;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              category      => "defense evasion",
              MITRE         => "T1562\.002",
              version       => 20200831);

sub getConfig{return %config}
sub getShortDescr {
	return "MiniNT key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching minint v.".$VERSION);
	::rptMsg("minint v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Control\\MiniNT";
	if ($key = $root_key->get_subkey($key_path)) {
		my $lw = ::format8601Date($key->get_timestamp())."Z";
		::rptMsg($key_path." key found, LastWrite: ".$lw);
		::rptMsg("");
		::rptMsg("Analysis Tip: If the MiniNt key is found, then it may have been added to make Windows think it is");
		::rptMsg("WinPE; this can inhibit logging.");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;